package com.wwstation.messagecenter.controllers;

import com.alibaba.fastjson.JSONObject;
import com.wwstation.messagecenter.components.master.ProducerMaster;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
    public Boolean sendMessage(
            @RequestParam(name = "name", required = true) String name,
            @RequestParam(name = "key", required = false) String key,
            @RequestBody(required = false) JSONObject jsonObject) throws Exception {
        return producerMaster.send(name,
                key,
                jsonObject == null ? "{}" : jsonObject.toJSONString());
    }

}
