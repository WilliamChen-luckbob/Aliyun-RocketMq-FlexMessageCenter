package com.wwstation.messagecenter.components.master;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.thread.RejectPolicy;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wwstation.messagecenter.components.config.ConsumeTypeEnum;
import com.wwstation.messagecenter.components.config.MessageConfig;
import com.wwstation.messagecenter.model.po.ConsumerConfig;
import com.wwstation.messagecenter.model.po.DeadMessage;
import com.wwstation.messagecenter.model.po.FailedMessage;
import com.wwstation.messagecenter.service.MPConsumerConfigService;
import com.wwstation.messagecenter.service.MPDeadMessageService;
import com.wwstation.messagecenter.service.MPFailedMessageService;

import com.wwstation.messagecenter.utils.Http.HttpBean;
import com.wwstation.messagecenter.utils.Http.HttpUtils4LoadBalancer;
import com.zaxxer.hikari.util.UtilityElf;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.map.HashedMap;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
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
@AutoConfigureAfter(value = MessageConfig.class)
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

    private static final Long interval = 10L;

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
                if (
                    (SQLException.class.isAssignableFrom(e.getClass()) ||
                        DataAccessException.class.isAssignableFrom(e.getClass())
                    ) &&
                        (e.getMessage().contains("Unknown database") ||
                            e.getMessage().contains("doesn't exist") ||
                            e.getMessage().contains("Exception during pool initialization")
                        )
                ) {
                    log.error("未初始化表结构，等待程序自动创建表结构...");
                } else {
                    log.error(e.getMessage());
                    e.printStackTrace();
                }
            } finally {
                TimeUnit.SECONDS.sleep(interval);
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
            log.debug("没有需要重试的数据");
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
            FutureTask<Integer> futureTask = null;
            if (ConsumeTypeEnum.typeMatch(consumerConfig.getConsumeType(), ConsumeTypeEnum.BROADCAST)) {
                BroadCastRetryerWorker broadCastRetryerWorker = new BroadCastRetryerWorker(failedMessage.getCreateTime(),
                    failedMessage,
                    consumerConfig,
                    restTemplate,
                    discoveryClient,
                    retryerPool);
                futureTask = new FutureTask<>(broadCastRetryerWorker);
            } else if (ConsumeTypeEnum.typeMatch(consumerConfig.getConsumeType(), ConsumeTypeEnum.PULL)) {
                RetryerWorker retryerWorker = new RetryerWorker(
                    failedMessage.getCreateTime(),
                    failedMessage,
                    consumerConfig);
                futureTask = new FutureTask(retryerWorker);
            } else if (ConsumeTypeEnum.typeMatch(consumerConfig.getConsumeType(), ConsumeTypeEnum.DEFAULT)) {
                RetryerWorker retryerWorker = new RetryerWorker(
                    failedMessage.getCreateTime(),
                    failedMessage,
                    consumerConfig);
                futureTask = new FutureTask(retryerWorker);
            } else {
                log.error("不支持的消费类型{}！消费失败！", consumerConfig.getConsumeType());
            }


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
                JSONObject body2Send = JSONObject.parseObject(body);
                if (StringUtils.isNotEmpty(consumerConfig.getAsyncCallbackHandlerOnSucceed())){
                    body2Send.put("messageId",messageId);
                }
                httpBean.setBody(body2Send);
                JSONObject execute = null;
                execute = httpUtil.execute(
                    isInnerProcessor ? balancedRestTemplate : restTemplate,
                    httpBean);
                if (execute.getString("status").equals("200")) {
                    log.info("消息 id={} 重新消费成功", messageId);
                    return 1;
                }
                log.error("messageID={} 再次消费失败"+ execute.toJSONString() +"，期待重试", messageId);
                return 2;
            } catch (Exception e) {
                e.printStackTrace();
                log.error("messageID=" + messageId + " 再次消费失败，期待重试", e);
                return 2;
            } finally {
                countDownLatch.countDown();
            }
        }
    }

    /**
     * 广播模式执行实际重试动作的内部类（方便共享主线程变量）
     */
    public class BroadCastRetryerWorker implements Callable<Integer> {
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
        private RestTemplate restTemplate;
        private DiscoveryClient discoveryClient;
        private Executor executor;

        public BroadCastRetryerWorker(LocalDateTime createTime,
                                      FailedMessage failedMessage,
                                      ConsumerConfig consumerConfig,
                                      RestTemplate restTemplate,
                                      DiscoveryClient discoveryClient,
                                      Executor executor) {
            this.createTime = createTime;
            this.failedMessage = failedMessage;
            this.consumerConfig = consumerConfig;
            this.isInnerProcessor = consumerConfig.getIsInnerProcessor();
            this.moduleName = consumerConfig.getModuleName();
            this.url = consumerConfig.getProcessUrl();
            this.messageId = failedMessage.getMqId();
            this.body = failedMessage.getMessage();
            this.restTemplate = restTemplate;
            this.discoveryClient = discoveryClient;
            this.executor = executor;
        }

        @Override
        public Integer call() throws Exception {
            try {
                //发送请求,需要先从注册中心获取目标服务的所有实例，并尝试异步发送并获取结果
                //至少有一个响应成功，视为成功
                List<ServiceInstance> availableInstances = discoveryClient.getInstances(moduleName);
                Integer count = availableInstances.size();
                CountDownLatch countDownLatch = new CountDownLatch(count);
                log.info("正在向存活的服务实例进行消息广播...");

//                    redisUtils.set(String.format(Constants.BROADCAST_ACK_NUM,message.getMessageId()),count);
                Map<String, FutureTask<JSONObject>> futureTasks = new HashMap<>();
                for (ServiceInstance instance : availableInstances) {
                    //这里注意httpBean在异步时可能会导致两个线程引用时使用了相同的url的并发问题，此处最好进行新实例化对象
                    HttpBean httpBean = new HttpBean();
                    httpBean.setMethod(HttpMethod.POST);
                    httpBean.setBody(JSONObject.parseObject(body));
                    httpBean.setUrl(instance.getUri().toString() + url);
                    HttpBean clone = SerializationUtils.clone(httpBean);
                    FutureTask<JSONObject> futureTask = new FutureTask<>(new Callable<JSONObject>() {
                        @Override
                        public JSONObject call() throws Exception {
                            try {
                                log.info("正在向{}发送消费数据...", clone.getUrl());
                                return httpUtil.execute(restTemplate, clone);
                            } catch (Exception exception) {
                                throw exception;
                            } finally {
                                countDownLatch.countDown();
                            }
                        }
                    });
                    futureTasks.put(instance.getUri().toString(), futureTask);
                    executor.execute(futureTask);
                }
                countDownLatch.await();


                //等待所有异步线程执行完毕，判定有多少成功的数据
                Long succeedCount = futureTasks.entrySet().stream().filter(e -> {
                    JSONObject result = null;
                    String moduleInstance = e.getKey();
                    try {
                        //尝试获取result
                        result = e.getValue().get();
                        String status = result.getString("status");
                        if (status == null) {
                            throw new NullPointerException("status 为null！");
                        } else {
                            if (!status.equals("200")) {
                                log.error("实例{}响应失败，内容为：{}", moduleInstance, result.toJSONString());
                            } else {
                                return true;
                            }
                        }
                    } catch (Exception ex) {
                        //如果callable中出现异常，在此进行日志打印
                        log.error(String.format("调用实例%s出现异常，异常内容为：%s，", moduleInstance, ex.getMessage()), e);
                    }
                    return false;
                }).count();

                if (succeedCount > 0) {
                    log.info("消息 id={} 广播消费成功", messageId);
                    return 1;
                } else {
                    log.error("messageID={} 再次消费失败，期待重试", messageId);
                    return 2;
                }
            } catch (Exception e) {
                e.printStackTrace();
                log.error("messageID=" + messageId + " 再次消费失败，期待重试", e);
                return 2;
            } finally {
                countDownLatch.countDown();
            }
        }
    }
}
