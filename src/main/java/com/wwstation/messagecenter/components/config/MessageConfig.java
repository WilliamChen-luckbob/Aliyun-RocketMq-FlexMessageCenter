package com.wwstation.messagecenter.components.config;

import com.google.common.collect.Sets;
import com.wwstation.messagecenter.model.bo.ProducerConfig;
import com.wwstation.messagecenter.model.po.BasicConfig;
import com.wwstation.messagecenter.model.po.ConsumerConfig;
import com.wwstation.messagecenter.service.MPBasicConfigService;
import com.wwstation.messagecenter.service.MPConsumerConfigService;
import org.apache.commons.collections.map.HashedMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * @author william
 * @description
 * @Date: 2021-03-05 10:53
 */
@Configuration

public class MessageConfig {
    @Autowired
    private MPBasicConfigService basicConfigService;
    @Autowired
    private MPConsumerConfigService consumerConfigService;

    @Value("${rocketmq.accessKey}")
    private String accessKey;
    @Value("${rocketmq.secretKey}")
    private String secretKey;
    @Value("${rocketmq.nameSrvAddr}")
    private String nameSrvAddr;
    @Value("${rocketmq.instanceId}")
    private String instanceId;

    private AtomicBoolean inRefreshing;
    private Map<String, ConsumerConfig> consumerConfig;
    private List<ProducerConfig> producerConfig;

    public Map<String, ConsumerConfig> getConsumerConfig() throws Exception {
        while (inRefreshing.get()) {
            TimeUnit.MILLISECONDS.sleep(10);
        }
        return consumerConfig;
    }

    public List<ProducerConfig> getProducerConfig() throws Exception {
        while (inRefreshing.get()) {
            TimeUnit.MILLISECONDS.sleep(10);
        }
        return producerConfig;
    }

    public BasicConfig getBasicConfig() throws Exception {
        BasicConfig basicConfig = new BasicConfig();
        basicConfig.setAccessKey(this.accessKey);
        basicConfig.setNameServerAddr(this.nameSrvAddr);
        basicConfig.setSecretKey(this.secretKey);
        return basicConfig;
    }

    @PostConstruct
    private void refeshConfig() {
        this.producerConfig = new ArrayList<>();
        this.consumerConfig = new HashedMap();
        inRefreshing = new AtomicBoolean(false);
        new Thread(() -> {
            try {
                refresh();
            } catch (InterruptedException e) {
                e.printStackTrace();
                inRefreshing.set(false);
            }
        }, "配置刷新线程").start();
    }

    private void refresh() throws InterruptedException {
        while (true) {
            inRefreshing.set(true);

            this.producerConfig.clear();
            this.consumerConfig.clear();

            try {
                List<ConsumerConfig> list = consumerConfigService.list();
                this.consumerConfig = list.stream().collect(Collectors.toMap(
                        k -> k.getConsumerName(),
                        v -> v
                ));

                //k=topic v=instanceId
                Map<String, Set<String>> producerConfigRaw = new HashedMap();
                list.stream()
                        .forEach(e -> {
                            String topic = e.getTopic();
                            String instanceId = e.getInstanceId();

                            if (producerConfigRaw.containsKey(topic)) {
                                Set<String> instanceIds = producerConfigRaw.get(topic);
                                if (!instanceIds.contains(instanceId)) {
                                    instanceIds.add(instanceId);
                                }
                            } else {
                                producerConfigRaw.put(topic, Sets.newHashSet(instanceId));
                            }
                        });

                producerConfigRaw.entrySet().stream()
                        .forEach(e -> {
                            e.getValue().stream().forEach(p -> {
                                ProducerConfig producerConfig = new ProducerConfig();
                                producerConfig.setTopic(e.getKey());
                                producerConfig.setInstanceId(p);
                                this.producerConfig.add(producerConfig);
                            });
                        });
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                inRefreshing.set(false);
            }

            //定时刷新
            TimeUnit.SECONDS.sleep(30);
        }

    }

    public String getInstanceId() {
        return instanceId;
    }
}
