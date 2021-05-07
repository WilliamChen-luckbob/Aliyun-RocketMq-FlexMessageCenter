package com.wwstation.messagecenter.controllers;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONValidator;
import com.wwstation.messagecenter.components.master.ProducerMaster;
import com.wwstation.messagecenter.exceptions.Asserts;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 外部调用rocketMQ生产者生产消息
 *
 * @author william
 * @description
 * @Date: 2020-12-29 13:50
 */
@RestController
@RequestMapping("/producer")
@Slf4j
public class ProducerController {
    @Autowired
    ProducerMaster producerMaster;

    @PostMapping("/message/sendMessage")
    public String sendMessage(@RequestBody(required = true) String jsonString) throws Exception {
        JSONObject jsonObject = JSONObject.parseObject(jsonString);
        String name = jsonObject.getString("name");
        String key = jsonObject.getString("key");
        String data = jsonObject.getString("data");

        if (!JSONValidator.from(data).validate()){
            Asserts.fail("格式校验失败！data字段请使用正确的JSON格式！");
        }

        if (StringUtils.isEmpty(name)) {
            log.error("生产者调用时name不能为空");
            throw new RuntimeException("生产者失败！name 不能为空");
        }
        return producerMaster.send(name,
            key,
            StringUtils.isEmpty(data) ? "{}" : data);
    }

    @PostMapping("/message/sendAsyncMessage")
    public String sendAsyncMessage(@RequestBody(required = true) String jsonString) throws Exception {
        JSONObject jsonObject = JSONObject.parseObject(jsonString);
        String name = jsonObject.getString("name");
        String key = jsonObject.getString("key");
        String data = jsonObject.getString("data");

        if (!JSONValidator.from(data).validate()){
            Asserts.fail("格式校验失败！data字段请使用正确的JSON格式！");
        }

        if (StringUtils.isEmpty(name)) {
            log.error("生产者调用时name不能为空");
            throw new RuntimeException("生产者失败！name 不能为空");
        }
        return producerMaster.asyncSend(name,
            key,
            StringUtils.isEmpty(data) ? "{}" : data);
    }
}
