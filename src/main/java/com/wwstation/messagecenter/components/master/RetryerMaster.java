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
 * ??????????????????
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
        //????????????DB??????failed?????????
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
                    log.error("?????????????????????????????????????????????????????????...");
                } else {
                    log.error(e.getMessage());
                    e.printStackTrace();
                }
            } finally {
                TimeUnit.SECONDS.sleep(interval);
                //??????????????????????????????????????????????????????????????????
                inProcessing.set(false);
            }

        }
    }

    /**
     * ?????????????????????????????????masterProcess
     * ????????????sql??????????????????????????????
     *
     * @throws Exception
     */
    @Transactional(rollbackFor = SQLException.class)
    public void masterProcess() throws Exception {
        //????????????????????????????????????
        //k=????????????id v=??????????????????
        Map<Long, FailedMessage> failedMessageMap = failedMessageService.list(new QueryWrapper<FailedMessage>()
            .le("next_retry_time", LocalDateTime.now()))
            .stream()
            .collect(Collectors.toMap(
                k -> k.getId(),
                v -> v));
        if (CollectionUtil.isEmpty(failedMessageMap)) {
            log.debug("???????????????????????????");
            return;
        }
        //k=???????????????ID v=?????????????????????
        Map<Long, ConsumerConfig> consumerConfigMap = consumerConfigService.listByIds(failedMessageMap.values().stream().map(e -> e.getConsumerConfigId()).collect(Collectors.toList()))
            .stream()
            .collect(Collectors.toMap(
                k -> k.getId(),
                v -> v));
        //????????????????????????????????????????????????????????????????????????
        Map<String, Integer> inNeedServiceInstanceCount = consumerConfigMap.values().stream().map(e -> e.getModuleName()).distinct().collect(Collectors.toMap(
            k -> k,
            v -> {
                return discoveryClient.getInstances(v).size();
            }));

        //??????????????????????????????message
        countDownLatch = new CountDownLatch(failedMessageMap.size());
        currentTime = LocalDateTime.now();
        Map<Long, FutureTask<Integer>> workerMap = new HashedMap();

        for (FailedMessage failedMessage : failedMessageMap.values()) {
            Long consumerConfigId = failedMessage.getConsumerConfigId();
            String mqId = failedMessage.getMqId();
            ConsumerConfig consumerConfig = consumerConfigMap.get(consumerConfigId);
            if (consumerConfig == null) {
                log.error("?????????????????????????????????messageID={}?????????????????????????????????????????????????????????ID={}", mqId, consumerConfigId);
                countDownLatch.countDown();
                continue;
            }
            String moduleName = consumerConfig.getModuleName();
            if (!inNeedServiceInstanceCount.containsKey(moduleName) || inNeedServiceInstanceCount.get(moduleName) == 0) {
                log.error("????????????messageID={}??????????????????????????????????????????????????????????????????????????????????????????????????????");
                countDownLatch.countDown();
                continue;
            }

            //?????????????????????????????????????????????????????????????????????????????????????????????????????????
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
                log.error("????????????????????????{}??????????????????", consumerConfig.getConsumeType());
            }


            //???????????????????????????
            workerMap.put(failedMessage.getId(), futureTask);
            retryerPool.submit(futureTask);
        }
        countDownLatch.await();

        //???????????????????????????????????????
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

        //????????????
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
     * ?????????????????????????????????????????????????????????????????????
     */
    public class RetryerWorker implements Callable<Integer> {
        //?????? 0-??????????????????
        //?????? 1-????????????
        //?????? 2-????????????

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
                    log.info("?????? id={} ??????????????????", messageId);
                    return 1;
                }
                log.error("messageID={} ??????????????????"+ execute.toJSONString() +"???????????????", messageId);
                return 2;
            } catch (Exception e) {
                e.printStackTrace();
                log.error("messageID=" + messageId + " ?????????????????????????????????", e);
                return 2;
            } finally {
                countDownLatch.countDown();
            }
        }
    }

    /**
     * ?????????????????????????????????????????????????????????????????????????????????
     */
    public class BroadCastRetryerWorker implements Callable<Integer> {
        //?????? 0-??????????????????
        //?????? 1-????????????
        //?????? 2-????????????

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
                //????????????,????????????????????????????????????????????????????????????????????????????????????????????????
                //??????????????????????????????????????????
                List<ServiceInstance> availableInstances = discoveryClient.getInstances(moduleName);
                Integer count = availableInstances.size();
                CountDownLatch countDownLatch = new CountDownLatch(count);
                log.info("????????????????????????????????????????????????...");

//                    redisUtils.set(String.format(Constants.BROADCAST_ACK_NUM,message.getMessageId()),count);
                Map<String, FutureTask<JSONObject>> futureTasks = new HashMap<>();
                for (ServiceInstance instance : availableInstances) {
                    //????????????httpBean??????????????????????????????????????????????????????????????????url??????????????????????????????????????????????????????
                    HttpBean httpBean = new HttpBean();
                    httpBean.setMethod(HttpMethod.POST);
                    httpBean.setBody(JSONObject.parseObject(body));
                    httpBean.setUrl(instance.getUri().toString() + url);
                    HttpBean clone = SerializationUtils.clone(httpBean);
                    FutureTask<JSONObject> futureTask = new FutureTask<>(new Callable<JSONObject>() {
                        @Override
                        public JSONObject call() throws Exception {
                            try {
                                log.info("?????????{}??????????????????...", clone.getUrl());
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


                //?????????????????????????????????????????????????????????????????????
                Long succeedCount = futureTasks.entrySet().stream().filter(e -> {
                    JSONObject result = null;
                    String moduleInstance = e.getKey();
                    try {
                        //????????????result
                        result = e.getValue().get();
                        String status = result.getString("status");
                        if (status == null) {
                            throw new NullPointerException("status ???null???");
                        } else {
                            if (!status.equals("200")) {
                                log.error("??????{}???????????????????????????{}", moduleInstance, result.toJSONString());
                            } else {
                                return true;
                            }
                        }
                    } catch (Exception ex) {
                        //??????callable??????????????????????????????????????????
                        log.error(String.format("????????????%s?????????????????????????????????%s???", moduleInstance, ex.getMessage()), e);
                    }
                    return false;
                }).count();

                if (succeedCount > 0) {
                    log.info("?????? id={} ??????????????????", messageId);
                    return 1;
                } else {
                    log.error("messageID={} ?????????????????????????????????", messageId);
                    return 2;
                }
            } catch (Exception e) {
                e.printStackTrace();
                log.error("messageID=" + messageId + " ?????????????????????????????????", e);
                return 2;
            } finally {
                countDownLatch.countDown();
            }
        }
    }
}
