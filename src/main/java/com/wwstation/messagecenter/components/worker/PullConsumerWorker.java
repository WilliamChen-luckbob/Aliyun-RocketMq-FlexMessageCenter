package com.wwstation.messagecenter.components.worker;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.mq.http.MQClient;
import com.aliyun.mq.http.MQConsumer;
import com.aliyun.mq.http.common.AckMessageException;
import com.aliyun.mq.http.model.Message;
import com.wwstation.messagecenter.exceptions.GlobalApiException;
import com.wwstation.messagecenter.model.po.ConsumerConfig;
import com.wwstation.messagecenter.model.po.FailedMessage;
import com.wwstation.messagecenter.service.MPFailedMessageService;
import com.wwstation.messagecenter.utils.FeignUtils;
import com.wwstation.messagecenter.utils.Http.HttpBean;
import com.wwstation.messagecenter.utils.Http.HttpUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
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
public class PullConsumerWorker implements Runnable {
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
    private Long configId;
    private MPFailedMessageService failedMessageService;
    private ConsumerConfig config;

    public PullConsumerWorker(HttpUtils httpUtil,
                              RestTemplate restTemplate,
                              MQClient mqClient,
                              ConsumerConfig config,
                              MPFailedMessageService failedMessageService) {
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
        this.failedMessageService = failedMessageService;
        this.configId = config.getId();
        this.config=config;
    }

    @Override
    public void run() {
        String threadName = Thread.currentThread().getName();

        //用于防止在consume的时候被打断抛出导致线程中断失败
        Boolean interruptedWhileConsuming = false;

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
            List<Message> messages = null;

            interruptedWhileConsuming = false;

            try {
                // 长轮询消费消息
                // 长轮询表示如果topic没有消息则请求会在服务端挂住3s，3s内如果有消息可以消费则立即返回
                messages = consumer.consumeMessage(
                    5,// 一次最多消费3条(最多可设置为16条)
                    3// 长轮询时间3秒（最多可设置为30秒）
                );
            } catch (Throwable e) {
                if (e.getMessage().contains("java.lang.InterruptedException")) {
                    interruptedWhileConsuming = true;
                } else {
                    e.printStackTrace();
                }
            }
            // 没有消息
            if (messages == null || messages.isEmpty()) {
                try {
                    //如果在拉取时被终止，这里直接可以停止线程
                    if (interruptedWhileConsuming) {
                        break;
                    }
                    //此处休息2秒进行下次拉取，如果此时被打断，将允许优雅退出
                    TimeUnit.SECONDS.sleep(2);
                } catch (InterruptedException e1) {
                    break;
                }
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
                    JSONObject body2Send = JSONObject.parseObject(body);

                    //如果是异步发送的消息，需要在消费时告知消费者当前messageID
                    if (StringUtils.isNotEmpty(config.getAsyncCallbackHandlerOnSucceed())) {
                        body2Send.put("messageId", message.getMessageId());
                    }
                    httpBean.setBody(body2Send);

                    //发送请求
                    JSONObject execute = execute = httpUtil.execute(restTemplate, httpBean);

                    if (execute.getString("status").equals("200")) {
                        log.info("消息 id={} 消费成功", message.getMessageId());
                    } else {
                        if (execute != null) {
                            throw new GlobalApiException(execute.getString("note"));
                        }
                        throw new GlobalApiException("消费时发生未知异常！");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    log.error("{}消费者消费失败，消息ID={}", threadName, message.getMessageId());
                    log.error("当前请求的服务接口为{}{}", moduleName, url);
                    log.error(FeignUtils.getResultFromRestTemplateRequestError(e.getLocalizedMessage()));
                    log.info(JSONObject.toJSONString(message));

                    LocalDateTime now = LocalDateTime.now();
                    FailedMessage failedMessage = new FailedMessage();
                    failedMessage.setMqId(message.getMessageId());
                    failedMessage.setConsumerConfigId(configId);
                    failedMessage.setRetryTimes(0);
                    failedMessage.setCreateTime(now);
                    failedMessage.setMessage(message.getMessageBodyString());
                    failedMessage.setNextRetryTime(now.plusSeconds(12L));
                    failedMessageService.save(failedMessage);
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
        log.info("线程{}被优雅的停止了!消费者配置为:\n{}", threadName, JSONUtil.toJsonPrettyStr(config));
    }
}
