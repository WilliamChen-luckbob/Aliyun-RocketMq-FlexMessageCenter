package com.wwstation.messagecenter.components.master;

import cn.hutool.core.collection.CollectionUtil;
import com.aliyun.mq.http.MQClient;
import com.wwstation.messagecenter.components.config.MessageConfig;
import com.wwstation.messagecenter.components.worker.ConsumerWorker;
import com.wwstation.messagecenter.model.po.BasicConfig;
import com.wwstation.messagecenter.model.po.ConsumerConfig;
import com.wwstation.messagecenter.utils.HttpUtils4LoadBalancer;
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

    //存活的服务
    private Set<String> aliveServiceInstances;
    //存活的消费者
    private Map<String, Thread> aliveConsumerWorkers;

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
        //todo 目前基础配置写死，后期也要进行代码刷新
        BasicConfig basicConfig = config.getBasicConfig();

        MQClient mqClient = new MQClient(basicConfig.getNameServerAddr(),
                basicConfig.getAccessKey(),
                basicConfig.getSecretKey());

        if (aliveServiceInstances == null) {
            aliveServiceInstances = new HashSet<>();
        }
        if (aliveConsumerWorkers == null) {
            aliveConsumerWorkers = new HashMap<>();
        }

        while (true) {
            Map<String, ConsumerConfig> consumerConfig = config.getConsumerConfig();
            try {
                //遍历配置，依次尝试启动消费线程
                for (Map.Entry<String, ConsumerConfig> entry : consumerConfig.entrySet()) {
                    aliveServiceInstances.clear();

                    //配置中的关键信息
                    ConsumerConfig currentConsumerConfig = entry.getValue();
                    String consumerName = currentConsumerConfig.getConsumerName();
                    String configuredEurekaServiceName = currentConsumerConfig.getModuleName();

                    //判断当前配置所需的消费服务是否存活
                    if (!aliveServiceInstances.contains(configuredEurekaServiceName)) {
                        //如果存活服务没有记录，尝试查询注册中心
                        List<ServiceInstance> instances = discoveryClient.getInstances(configuredEurekaServiceName);
                        if (CollectionUtil.isNotEmpty(instances)) {
                            //注册中心查出，存活服务添加
                            aliveServiceInstances.add(configuredEurekaServiceName);

                            if (!aliveConsumerWorkers.containsKey(consumerName)) {
                                //消费线程不存在则创建消费线程
                                ConsumerWorker consumerWorker = new ConsumerWorker(
                                        httpUtil,
                                        currentConsumerConfig.getIsInnerProcessor() ? balancedRestTemplate : restTemplate,
                                        mqClient,
                                        currentConsumerConfig);
                                Thread thread = new Thread(consumerWorker, consumerName);
                                aliveConsumerWorkers.put(consumerName, thread);
                                thread.start();
                                log.info("消费线程{}启动", consumerName);
                            }
                            continue;
                        }

                        //不存在实例，直接查看是否有消费线程，有则关闭之
                        if (aliveConsumerWorkers.containsKey(consumerName)) {
                            log.info("{}的消费者服务实例{}已经全部下线，尝试关闭此监听线程，以免产生过多的消费失败数据堆积",
                                    consumerName,
                                    configuredEurekaServiceName);
                            Thread thread = aliveConsumerWorkers.get(consumerName);
                            thread.interrupt();
                            while (thread.isAlive()) {
                                TimeUnit.MILLISECONDS.sleep(20);
                            }
                            aliveConsumerWorkers.remove(consumerName);
                        }

                        continue;
                    }

                    //存活服务匹配且存在实例，查看消费线程是否存在

                    //不存在则创建消费线程
                    log.info("消费线程启动");


                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                //轮询间隔
                TimeUnit.SECONDS.sleep(5);
            }
        }

    }
}
