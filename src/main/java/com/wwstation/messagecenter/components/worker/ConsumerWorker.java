package com.wwstation.messagecenter.components.worker;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.mq.http.MQClient;
import com.aliyun.mq.http.MQConsumer;
import com.aliyun.mq.http.common.AckMessageException;
import com.aliyun.mq.http.model.Message;
import com.wwstation.messagecenter.model.bo.HttpBean;
import com.wwstation.messagecenter.model.po.ConsumerConfig;
import com.wwstation.messagecenter.utils.FeedBackUtils;
import com.wwstation.messagecenter.utils.HttpUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 消费者消费线程
 * 消息的实际拉取和处理者
 * 由master创建和管理
 * 每一个worker监听一个消费者组
 *
 * @author william
 * @description
 * @Date: 2021-03-05 10:41
 */
@Slf4j
public class ConsumerWorker implements Runnable {
    private RestTemplate restTemplate;
    private HttpUtils httpUtil;
    private MQClient mqClient;
    private String mqInstanceId;
    private String topic;
    private String groupId;
    private String tag;
    private String moduleName;
    private String url;
    private Boolean isInnerProcessor;

    public ConsumerWorker(HttpUtils httpUtil,
                          RestTemplate restTemplate,
                          MQClient mqClient,
                          ConsumerConfig config) {
        this.restTemplate = restTemplate;
        this.httpUtil = httpUtil;
        this.mqClient = mqClient;
        this.mqInstanceId = config.getInstanceId();
        this.topic = config.getTopic();
        this.groupId = config.getGroupId();
        this.tag = config.getTag();
        this.moduleName = config.getModuleName();
        this.url = config.getProcessUrl();
        this.isInnerProcessor = config.getIsInnerProcessor();
    }

    @Override
    public void run() {
        String threadName = Thread.currentThread().getName();

        MQConsumer consumer = mqClient.getConsumer(this.mqInstanceId,
                this.topic,
                this.groupId,
                this.tag
        );

        while (true) {
            //停止位判定
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            //业务逻辑
//            log.info("当前线程：{}，正在活跃", threadName);
            List<Message> messages = null;
            try {
                // 长轮询消费消息
                // 长轮询表示如果topic没有消息则请求会在服务端挂住3s，3s内如果有消息可以消费则立即返回
                messages = consumer.consumeMessage(
                        3,// 一次最多消费3条(最多可设置为16条)
                        3// 长轮询时间3秒（最多可设置为30秒）
                );
            } catch (Throwable e) {
                e.printStackTrace();
                try {
                    TimeUnit.SECONDS.sleep(2);
                } catch (InterruptedException e1) {
                    break;
                }
            }
            // 没有消息
            if (messages == null || messages.isEmpty()) {
                continue;
            }
            //待提交的数据
            List<String> handles = new ArrayList<String>();

            // 处理业务逻辑
            for (Message message : messages) {
                System.out.println("Receive message: " + message);
                String body = message.getMessageBodyString();
                try {
                    HttpBean httpBean = new HttpBean();
                    httpBean.setMethod(HttpMethod.POST);
                    httpBean.setUrl(isInnerProcessor ? moduleName + url : url);
                    httpBean.setBody(JSONObject.parseObject(body));

                    JSONObject execute = httpUtil.execute(restTemplate, httpBean);

                    if (execute.getString("status").equals("200")) {
                        log.info("消息 id={} 消费成功", message.getMessageId());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    log.error("{}消费者消费失败，消息ID={}", threadName, message.getMessageId());
                    log.error(FeedBackUtils.getResultFromError(e.getLocalizedMessage()));
                    log.info(JSONObject.toJSONString(message));
                } finally {
                    //所有的消息均要置为消费成功
                    handles.add(message.getReceiptHandle());
                }
            }

            // 尝试提交消费结果，只处理消费成功的
            // Message.nextConsumeTime前若不确认消息消费成功，则消息会重复消费
            // 消息句柄有时间戳，同一条消息每次消费拿到的都不一样
            if (CollectionUtil.isNotEmpty(handles)) {
                try {
                    consumer.ackMessage(handles);
                } catch (Throwable e) {
                    // 某些消息的句柄可能超时了会导致确认不成功
                    if (e instanceof AckMessageException) {
                        AckMessageException errors = (AckMessageException) e;
                        System.out.println("Ack message fail, requestId is:" + errors.getRequestId() + ", fail handles:");
                        if (errors.getErrorMessages() != null) {
                            for (String errorHandle : errors.getErrorMessages().keySet()) {
                                System.out.println("Handle:" + errorHandle + ", ErrorCode:" + errors.getErrorMessages().get(errorHandle).getErrorCode()
                                        + ", ErrorMsg:" + errors.getErrorMessages().get(errorHandle).getErrorMessage());
                            }
                        }
                        continue;
                    }
                    e.printStackTrace();
                }
            }

            //轮询时间
            try {
                TimeUnit.SECONDS.sleep(2);
            } catch (InterruptedException e) {
                //线程在等待下一次执行时优雅的中断
                break;
            }
        }
        log.info("线程{}被优雅的停止了", threadName);

    }
}
