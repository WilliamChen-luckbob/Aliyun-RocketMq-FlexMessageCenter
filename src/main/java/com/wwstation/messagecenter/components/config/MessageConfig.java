package com.wwstation.messagecenter.components.config;

import cn.hutool.core.collection.CollectionUtil;
import com.google.common.collect.Sets;
import com.wwstation.messagecenter.model.bo.ProducerConfig;
import com.wwstation.messagecenter.model.po.BasicConfig;
import com.wwstation.messagecenter.model.po.ConsumerConfig;
import com.wwstation.messagecenter.service.MPBasicConfigService;
import com.wwstation.messagecenter.service.MPConsumerConfigService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.map.HashedMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.dao.DataAccessException;

import javax.annotation.PostConstruct;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 根据https://github.com/spring-projects/spring-boot/issues/8649
 * 只能使用DependsOn来指定在flyway后执行
 * @author william
 * @description
 * @Date: 2021-03-05 10:53
 */
@Configuration
//@DependsOn({"flyway", "flywayInitializer"})
@Slf4j
public class MessageConfig {
    @Autowired
    private MPBasicConfigService basicConfigService;
    @Autowired
    private MPConsumerConfigService consumerConfigService;

    private AtomicBoolean inRefreshing;
    private Map<String, ConsumerConfig> consumerConfig;
    private List<ProducerConfig> producerConfig;
    private BasicConfig basicConfig;

    /**
     * 刷新配置的线程随程序启动
     * 由于配置默认为不经常变动，因此此处的刷新频率默认为30秒
     */
    @PostConstruct
    private void refeshConfig() {
        this.producerConfig = new ArrayList<>();
        this.consumerConfig = new HashMap<>();
        inRefreshing = new AtomicBoolean(false);
        new Thread(() -> {
            refresh();
        }, "配置刷新线程").start();

    }

    /**
     * 尝试获取消费者配置
     *
     * @return
     * @throws Exception
     */
    public Map<String, ConsumerConfig> getConsumerConfig() throws Exception {
        while (inRefreshing.get()) {
            TimeUnit.MILLISECONDS.sleep(10);
        }
        return consumerConfig;
    }

    /**
     * 尝试获取生产者配置
     *
     * @return
     * @throws Exception
     */
    public List<ProducerConfig> getProducerConfig() throws Exception {
        while (inRefreshing.get()) {
            TimeUnit.MILLISECONDS.sleep(10);
        }
        return producerConfig;
    }

    /**
     * 尝试获取基础配置
     *
     * @return
     * @throws Exception
     */
    public BasicConfig getBasicConfig() throws Exception {
        while (inRefreshing.get()) {
            TimeUnit.MILLISECONDS.sleep(10);
        }
        return basicConfig;
    }


    private void refresh() {
        try {
            while (true) {
                if (inRefreshing.get()) {
                    TimeUnit.SECONDS.sleep(3);
                    continue;
                }
                doRefresh();
                //刷新完毕后线程等待30秒再次尝试刷新
                TimeUnit.SECONDS.sleep(30);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void doRefresh() {
        inRefreshing.set(true);
        log.info("正在定时刷新配置信息...");
        this.producerConfig.clear();
        this.consumerConfig.clear();

        try {
            List<BasicConfig> newList = basicConfigService.list();
            if (CollectionUtil.isEmpty(newList)){
                log.error("没有找到对应的基础配置，请确定数据库是否已经启动并写入初始配置！");
                return;
            }
            this.basicConfig= newList.get(0);

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
            log.info("定时刷新配置完毕，配置已更新");
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
            inRefreshing.set(false);
        }
    }
}
