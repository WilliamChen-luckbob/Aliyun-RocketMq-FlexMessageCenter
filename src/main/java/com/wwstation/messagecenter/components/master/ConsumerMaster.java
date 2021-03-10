package com.wwstation.messagecenter.components.master;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.mq.http.MQClient;
import com.wwstation.messagecenter.components.config.MessageConfig;
import com.wwstation.messagecenter.components.worker.ConsumerWorker;
import com.wwstation.messagecenter.model.po.BasicConfig;
import com.wwstation.messagecenter.model.po.ConsumerConfig;
import com.wwstation.messagecenter.service.MPFailedMessageService;
import com.wwstation.messagecenter.utils.HttpUtils4LoadBalancer;
import javafx.application.Application;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 消费者监听线程
 * 项目启动后开始拉取配置
 * 创建消费者线程并监听这些线程
 *
 * @author william
 * @description
 * @Date: 2021-03-05 10:39
 */
@Component
@Slf4j
public class ConsumerMaster {
    @Autowired
    MessageConfig config;
    @Autowired
    private DiscoveryClient discoveryClient;
    @Autowired
    private HttpUtils4LoadBalancer httpUtil;
    @Autowired
    @Qualifier(value = "BalancedRestTemplate")
    private RestTemplate balancedRestTemplate;
    @Autowired
    @Qualifier(value = "RestTemplate")
    private RestTemplate restTemplate;
    @Autowired
    MPFailedMessageService failedMessageService;

    //存活的服务
    private Set<String> aliveServiceInstances;
    //存活的消费者
    private Map<String, Thread> aliveConsumerWorkers;
    //存活的消费者对应的配置（用于比对是否出现更改）
    private Map<String, ConsumerConfig> aliveConsumerConfigs;
    private MQClient mqClient;
    //master轮询处理失败次数，在一定次数后将停止轮询，直到后端开发排查完毕修复重启
    private Integer failedRetryTimes;

    @PostConstruct
    public void initialize() throws Exception {
        new Thread(() -> {
            try {
                run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "consumerMaster").start();
    }

    private void run() throws Exception {
        //创建新的消费者客户端
        BasicConfig basicConfig = config.getBasicConfig();
        //如果基础配置获取失败，轮询直到获取成功
        //todo 多基础配置的设定暂时不考虑
        while (basicConfig == null) {
            log.error("正在等待获取基础配置");
            TimeUnit.SECONDS.sleep(5);
            basicConfig = config.getBasicConfig();
        }
        //一些初始化
        mqClient = new MQClient(basicConfig.getNameServerAddr(),
            basicConfig.getAccessKey(),
            basicConfig.getSecretKey());
        if (aliveServiceInstances == null) {
            aliveServiceInstances = new HashSet<>();
        }
        if (aliveConsumerWorkers == null) {
            aliveConsumerWorkers = new HashMap<>();
        }
        if (aliveConsumerConfigs == null) {
            aliveConsumerConfigs = new HashMap<>();
        }
        failedRetryTimes = 0;

        //开始长轮询监听
        while (failedRetryTimes <= 20) {
            Map<String, ConsumerConfig> consumerConfig = config.getConsumerConfig();
            try {
                //遍历配置，依次尝试启动消费线程
                for (Map.Entry<String, ConsumerConfig> entry : consumerConfig.entrySet()) {
                    aliveServiceInstances.clear();

                    //配置中的关键信息
                    ConsumerConfig currentConsumerConfig = entry.getValue();
                    String consumerName = currentConsumerConfig.getConsumerName();
                    String configuredEurekaServiceName = currentConsumerConfig.getModuleName();

                    //1. 查询存活服务实例列表是否存在指定的服务
                    if (aliveServiceInstances.contains(configuredEurekaServiceName)) {
                        //1.1 若存在，说明前面已经有过消息使用了相同的服务实例，已经加载
                        //1.1.1 尝试修改或新建消费者线程
                        processConsumer(consumerName, currentConsumerConfig);
                    } else {
                        //1.2 若不存在，说明前面没有消息使用过这个服务实例，没有加载过，从注册中心尝试查询
                        if (discoveryClient.getInstances(configuredEurekaServiceName).size() >= 0) {
                            //1.2.1 注册中心有数据
                            aliveServiceInstances.add(configuredEurekaServiceName);
                            //尝试新建或修改对应的消费线程
                            processConsumer(consumerName, currentConsumerConfig);
                        } else {
                            //1.2.2 注册中心没有实例，此消费者不可存活
                            if (aliveConsumerWorkers.containsKey(consumerName)) {
                                log.info("{}的消费者服务实例{}已经全部下线或配置被修改，尝试关闭此监听线程，以免产生过多的消费失败数据堆积或消费异常",
                                    consumerName,
                                    configuredEurekaServiceName);
                                deleteConsumer(consumerName);
                            }
                        }
                    }
                }
                failedRetryTimes = 0;
            } catch (Exception e) {
                log.error("consumer master 在长轮询时出现了不能处理的异常，consumer master 正在重试");
                e.printStackTrace();
                failedRetryTimes += 1;
            } finally {
                //轮询间隔
                TimeUnit.SECONDS.sleep(5);
            }
        }
        masterShutDown();
    }

    private void masterShutDown() {
        if (CollectionUtil.isNotEmpty(aliveConsumerWorkers)){
            for (Thread worker : aliveConsumerWorkers.values()) {
                worker.interrupt();
            }
        }
    }

    private void processConsumer(String consumerName, ConsumerConfig currentConfig) throws Exception {
        //在此之前已确保服务实例均存在

        if (aliveConsumerWorkers.containsKey(consumerName)) {
            //1 如果有存活的消费者，判定是否存在配置数据修改
            ConsumerConfig existingConfig = aliveConsumerConfigs.get(consumerName);
            if (!isSameConfig(existingConfig, currentConfig)) {
                //1.1 存在修改，尝试关闭现存的消费者线程并重新创建新的监听线程
                log.info("发现DB中的消费者{}配置出现变化，重新启动消费者线程", consumerName);
                deleteConsumer(consumerName);
                createNewConsumer(mqClient, currentConfig, consumerName);
            }
            return;
        }
        //2 如果没有存活的消费者，直接新建
        createNewConsumer(mqClient, currentConfig, consumerName);
    }

    private Boolean isSameConfig(ConsumerConfig existingConfig, ConsumerConfig currentConfig) {
        return JSONObject.toJSONString(existingConfig).equals(JSONObject.toJSONString(currentConfig));
    }

    private void deleteConsumer(String consumerName) throws InterruptedException {
        Thread thread = aliveConsumerWorkers.get(consumerName);
        thread.interrupt();
        while (thread.isAlive()) {
            TimeUnit.MILLISECONDS.sleep(20);
        }
        aliveConsumerWorkers.remove(consumerName);
        aliveConsumerConfigs.remove(consumerName);
    }

    private void createNewConsumer(MQClient mqClient, ConsumerConfig currentConsumerConfig, String consumerName) {
        //存活消费者列表中不存在这个消费线程则创建消费线程
        ConsumerWorker consumerWorker = new ConsumerWorker(
            httpUtil,
            currentConsumerConfig.getIsInnerProcessor() ? balancedRestTemplate : restTemplate,
            mqClient,
            currentConsumerConfig,
            failedMessageService);
        Thread thread = new Thread(consumerWorker, consumerName);
        aliveConsumerWorkers.put(consumerName, thread);
        aliveConsumerConfigs.put(consumerName, currentConsumerConfig);
        thread.start();
        log.info("消费线程{}启动,消费者配置为:\n{}", consumerName, JSONUtil.toJsonPrettyStr(currentConsumerConfig));
    }
}
