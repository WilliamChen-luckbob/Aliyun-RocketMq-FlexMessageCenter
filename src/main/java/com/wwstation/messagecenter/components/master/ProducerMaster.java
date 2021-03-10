package com.wwstation.messagecenter.components.master;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.json.JSONUtil;
import com.aliyun.mq.http.MQClient;
import com.aliyun.mq.http.MQProducer;
import com.aliyun.mq.http.model.TopicMessage;
import com.wwstation.messagecenter.components.config.MessageConfig;
import com.wwstation.messagecenter.model.bo.ProducerConfig;
import com.wwstation.messagecenter.model.po.BasicConfig;
import com.wwstation.messagecenter.model.po.ConsumerConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 生产者监听线程
 * 项目启动后开始初始化相关的生产者线程，并持有本地生产者消息队列
 *
 * @author william
 * @description
 * @Date: 2021-03-05 14:50
 */
@Component
@Slf4j
public class ProducerMaster {
    @Autowired
    MessageConfig config;

    private Map<String, MQProducer> aliveProducer;

    @PostConstruct
    public void initialize() throws Exception {
        if (aliveProducer == null) {
            aliveProducer = new HashMap<>();
        }

        new Thread(() -> {
            try {
                run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "producerMaster").start();
    }

    private void run() throws Exception {
        BasicConfig basicConfig = config.getBasicConfig();
        //如果基础配置获取失败，轮询直到获取成功
        //todo 多基础配置的设定暂时不考虑
        while (basicConfig == null) {
            log.error("正在等待获取基础配置");
            TimeUnit.SECONDS.sleep(5);
            basicConfig = config.getBasicConfig();
        }
        MQClient mqClient = new MQClient(basicConfig.getNameServerAddr(),
            basicConfig.getAccessKey(),
            basicConfig.getSecretKey());

        while (true) {
            List<ProducerConfig> producerConfig = config.getProducerConfig();
            try {
                //判定配置中是否已经有数据不在存活的worker中，如有则需要剔除
                List<String> newProducerNames = producerConfig.stream()
                    .map(e -> e.getTopic() + "+" + e.getInstanceId())
                    .collect(Collectors.toList());
                List<String> producers2Kill = aliveProducer.keySet().stream()
                    .filter(e -> !newProducerNames.contains(e)).collect(Collectors.toList());
                if (CollectionUtil.isNotEmpty(producers2Kill)) {
                    for (String producerName : producers2Kill) {
                        MQProducer producer = aliveProducer.get(producerName);
                        producer = null;
                        aliveProducer.remove(producerName);
                        log.info("由于配置更新，生产者{}不再需要，已经下线", producerName);
                    }
                }

                //遍历配置，每一对topic+instanceID要新建一套生产者，依次启动,判定是否有新的生产者
                for (ProducerConfig pConfig : producerConfig) {
                    String producerName = String.format("%s+%s", pConfig.getTopic(), pConfig.getInstanceId());
                    if (!aliveProducer.containsKey(producerName)) {
                        //启动对应的生产者
                        MQProducer newProducer = mqClient.getProducer(pConfig.getInstanceId(), pConfig.getTopic());
                        aliveProducer.put(producerName, newProducer);
                        log.info("生产者{}启动,生产者配置为:\n{}", producerName, JSONUtil.toJsonPrettyStr(pConfig));
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                //轮询间隔
                TimeUnit.SECONDS.sleep(5);
            }
        }

    }

    /**
     * 发送消息
     *
     * @param consumerName 期望发送并被哪组接收
     * @return
     */
    public boolean send(String consumerName, String key, String dataJson) throws Exception {
        Map<String, ConsumerConfig> consumerConfigs = config.getConsumerConfig();
        ConsumerConfig consumerConfig = consumerConfigs.get(consumerName);

        String topic = consumerConfig.getTopic();
        String instanceId = consumerConfig.getInstanceId();
        String tag = consumerConfig.getTag();
        String producerName = topic + "+" + instanceId;

        TopicMessage msg = new TopicMessage();
        if (StringUtils.isNotEmpty(key)) {
            msg.setMessageKey(key);
        }
        if (StringUtils.isNotEmpty(tag)) {
            msg.setMessageTag(tag);
        }

        msg.setMessageBody(dataJson);

        try {
            MQProducer producer = aliveProducer.get(producerName);
            TopicMessage topicMessage = producer.publishMessage(msg);
            log.info("消息发送成功！messageId={},messagebody={}",
                topicMessage.getMessageId(),
                topicMessage.getMessageBodyString());
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            log.error(String.format(
                "消息发送失败：未知错误！topic=%s，tag=%s，key=%s，msg=%s",
                topic,
                tag,
                key,
                dataJson));
        }
        return false;
    }
}
