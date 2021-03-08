package com.wwstation.messagecenter.components.master;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.thread.RejectPolicy;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wwstation.messagecenter.model.bo.HttpBean;
import com.wwstation.messagecenter.model.po.ConsumerConfig;
import com.wwstation.messagecenter.model.po.DeadMessage;
import com.wwstation.messagecenter.model.po.FailedMessage;
import com.wwstation.messagecenter.service.MPConsumerConfigService;
import com.wwstation.messagecenter.service.MPDeadMessageService;
import com.wwstation.messagecenter.service.MPFailedMessageService;

import com.wwstation.messagecenter.utils.HttpUtils4LoadBalancer;
import com.zaxxer.hikari.util.UtilityElf;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.map.HashedMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;


/**
 * 消费重试环节
 *
 * @author william
 * @description
 * @Date: 2021-03-08 10:00
 */
@Component
@Slf4j
public class RetryerMaster {
    @Autowired
    MPFailedMessageService failedMessageService;
    @Autowired
    MPDeadMessageService deadMessageService;
    @Autowired
    MPConsumerConfigService consumerConfigService;
    @Autowired
    HttpUtils4LoadBalancer httpUtil;
    @Autowired
    @Qualifier(value = "BalancedRestTemplate")
    private RestTemplate balancedRestTemplate;
    @Autowired
    @Qualifier(value = "RestTemplate")
    private RestTemplate restTemplate;
    @Autowired
    DiscoveryClient discoveryClient;

    private volatile AtomicBoolean inProcessing;
    private ThreadPoolExecutor retryerPool;

    private CountDownLatch countDownLatch;
    private LocalDateTime currentTime;
    private List<Thread> aliveWorkers;

    @PostConstruct
    public void initialize() {
        inProcessing = new AtomicBoolean(false);
        retryerPool = new ThreadPoolExecutor(3,
            10,
            15,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue(100),
            new UtilityElf.DefaultThreadFactory("retryer", false),
            RejectPolicy.CALLER_RUNS.getValue());
        new Thread(() -> {
            try {
                run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "retryerMaster").start();
    }

    public void run() throws InterruptedException {
        //定时轮询DB查询failed的数据
        while (true) {
            try {
                masterProcess();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                TimeUnit.SECONDS.sleep(5);
                //正常情况下处理不会出问题，如果抛出异常，那么
                inProcessing.set(false);
            }

        }
    }

    /**
     * 同一时刻只允许运行一个masterProcess
     * 只有写入sql出错的时候才可以回滚
     *
     * @throws Exception
     */
    @Transactional(rollbackFor = SQLException.class)
    public void masterProcess() throws Exception {
        //查询可以被执行的失败消息
        //k=失败消息id v=失败消息明细
        Map<Long, FailedMessage> failedMessageMap = failedMessageService.list(new QueryWrapper<FailedMessage>()
            .le("next_retry_time", LocalDateTime.now()))
            .stream()
            .collect(Collectors.toMap(
                k -> k.getId(),
                v -> v));
        if (CollectionUtil.isEmpty(failedMessageMap)) {
            TimeUnit.SECONDS.sleep(5);
            return;
        }
        //k=消费者配置ID v=消费者实例明细
        Map<Long, ConsumerConfig> consumerConfigMap = consumerConfigService.listByIds(failedMessageMap.values().stream().map(e -> e.getConsumerConfigId()).collect(Collectors.toList()))
            .stream()
            .collect(Collectors.toMap(
                k -> k.getId(),
                v -> v));
        //本次轮询涉及的需要用到的实例及其对应存活实例列表
        Map<String, Integer> inNeedServiceInstanceCount = consumerConfigMap.values().stream().map(e -> e.getModuleName()).distinct().collect(Collectors.toMap(
            k -> k,
            v -> {
                return discoveryClient.getInstances(v).size();
            }));

        //理论上需要遍历所有的message
        countDownLatch = new CountDownLatch(failedMessageMap.size());
        currentTime = LocalDateTime.now();
        Map<Long, FutureTask<Integer>> workerMap = new HashedMap();

        for (FailedMessage failedMessage : failedMessageMap.values()) {
            Long consumerConfigId = failedMessage.getConsumerConfigId();
            String mqId = failedMessage.getMqId();
            ConsumerConfig consumerConfig = consumerConfigMap.get(consumerConfigId);
            if (consumerConfig == null) {
                log.error("失败重试线程在尝试获取messageID={}的消费者配置时未找到相关的参数，消费者ID={}", mqId, consumerConfigId);
                countDownLatch.countDown();
                continue;
            }
            String moduleName = consumerConfig.getModuleName();
            if (!inNeedServiceInstanceCount.containsKey(moduleName) || inNeedServiceInstanceCount.get(moduleName) == 0) {
                log.error("尝试处理messageID={}，此次轮询注册中心发现存活的实例列表中没有相应的消费处理服务，跳过。");
                countDownLatch.countDown();
                continue;
            }

            //注册中心有实例且配置等信息无问题的消息，才可以尝试创建线程进行消费重试
            RetryerWorker retryerWorker = new RetryerWorker(
                failedMessage.getCreateTime(),
                failedMessage,
                consumerConfig);
            FutureTask<Integer> futureTask = new FutureTask(retryerWorker);
            //本次循环的消息内容
            workerMap.put(failedMessage.getId(), futureTask);
            retryerPool.submit(futureTask);
        }
        countDownLatch.await();

        //等待处理全部结束，更新数据
        List<Long> messages2Dead = new ArrayList<>();
        List<Long> messages2Remove = new ArrayList<>();
        List<Long> messages2resume = new ArrayList<>();
        workerMap.entrySet().stream()
            .forEach(e -> {
                Long messageId = e.getKey();
                Integer status = null;
                try {
                    status = e.getValue().get();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                switch (status) {
                    case 0:
                        break;
                    case 1:
                        messages2Remove.add(messageId);
                        break;
                    case 2:
                        if (failedMessageMap.get(messageId).getRetryTimes() >= 14) {
                            messages2Dead.add(messageId);
                        }
                        messages2resume.add(messageId);
                        break;
                    default:
                        break;
                }
            });

        //更新数据
        if (CollectionUtil.isNotEmpty(messages2Dead)) {
            List<DeadMessage> deadMessages2Save = failedMessageMap.entrySet().stream().map(e -> {
                if (messages2Dead.contains(e.getKey())) {
                    FailedMessage value = e.getValue();
                    DeadMessage deadMessage = new DeadMessage();
                    deadMessage.setMqId(value.getMqId());
                    deadMessage.setConsumerConfigId(value.getConsumerConfigId());
                    deadMessage.setDeadTime(LocalDateTime.now());
                    deadMessage.setMessage(value.getMessage());
                    return deadMessage;
                }
                return null;
            }).filter(Objects::nonNull).collect(Collectors.toList());
            failedMessageService.removeByIds(messages2Dead);
            for (DeadMessage deadMessage : deadMessages2Save) {

                System.out.println(JSONObject.toJSONString(deadMessage));
            }
            deadMessageService.saveBatch(deadMessages2Save);
        }
        if (CollectionUtil.isNotEmpty(messages2Remove)) {
            failedMessageService.removeByIds(messages2Remove);
        }
        if (CollectionUtil.isNotEmpty(messages2resume)) {
            List<FailedMessage> failedMessages2Update = messages2resume.stream().map(e -> {
                if (failedMessageMap.containsKey(e)) {
                    FailedMessage failedMessage = failedMessageMap.get(e);
                    failedMessage.setRetryTimes(failedMessage.getRetryTimes() + 1);
                    long addtion = (long) (Math.pow(2, failedMessage.getRetryTimes().doubleValue()) + 10L);
                    failedMessage.setNextRetryTime(LocalDateTime.now().plusSeconds(addtion));
                    return failedMessage;
                }
                return null;
            }).filter(Objects::nonNull).collect(Collectors.toList());
            failedMessageService.updateBatchById(failedMessages2Update);
        }


    }


    /**
     * 执行实际重试动作的内部类（方便共享主线程变量）
     */
    public class RetryerWorker implements Callable<Integer> {
        //响应 0-现在不能重试
        //响应 1-重试成功
        //响应 2-重试失败

        private LocalDateTime createTime;
        private FailedMessage failedMessage;
        private ConsumerConfig consumerConfig;
        private Boolean isInnerProcessor;
        private String moduleName;
        private String url;
        private String messageId;
        private String body;

        public RetryerWorker(LocalDateTime createTime,
                             FailedMessage failedMessage,
                             ConsumerConfig consumerConfig) {
            this.createTime = createTime;
            this.failedMessage = failedMessage;
            this.consumerConfig = consumerConfig;
            this.isInnerProcessor = consumerConfig.getIsInnerProcessor();
            this.moduleName = consumerConfig.getModuleName();
            this.url = consumerConfig.getProcessUrl();
            this.messageId = failedMessage.getMqId();
            this.body = failedMessage.getMessage();
        }

        @Override
        public Integer call() throws Exception {
            try {
                HttpBean httpBean = new HttpBean();
                httpBean.setMethod(HttpMethod.POST);
                httpBean.setUrl(isInnerProcessor ? moduleName + url : url);
                httpBean.setBody(JSONObject.parseObject(body));

                JSONObject execute = null;
                execute = httpUtil.execute(
                    isInnerProcessor ? balancedRestTemplate : restTemplate,
                    httpBean);
                if (execute.getString("status").equals("200")) {
                    log.info("消息 id={} 重新消费成功", messageId);
                    return 1;
                }
                return 2;
            } catch (Exception e) {
                e.printStackTrace();
                log.error("messageID={} 再次消费失败，期待重试", messageId);
                return 2;
            } finally {
                countDownLatch.countDown();
            }
        }
    }
}
