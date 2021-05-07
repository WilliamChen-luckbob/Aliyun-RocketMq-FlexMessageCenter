package com.wwstation.messagecenter.controllers;

import com.alibaba.fastjson.JSONObject;
import com.wwstation.messagecenter.components.master.ConsumerMaster;
import com.wwstation.messagecenter.components.master.ProducerMaster;
import com.wwstation.messagecenter.exceptions.Asserts;
import com.wwstation.messagecenter.model.bo.CreateConsumerConfigForm;
import com.wwstation.messagecenter.model.po.ConsumerConfig;
import com.wwstation.messagecenter.service.MPBasicConfigService;
import com.wwstation.messagecenter.service.MPConsumerConfigService;
import com.wwstation.messagecenter.utils.ConvertUtil;
import com.wwstation.messagecenter.utils.StandardResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * @author william
 * @description
 * @Date: 2021-05-06 19:34
 */
@RestController
@RequestMapping("/v1/admin")
@Api(tags = "消息队列代理中心管理控制器")
@Slf4j
public class MQAdminController {
    @Autowired
    MPConsumerConfigService consumerConfigService;
    @Autowired
    MPBasicConfigService basicConfigService;
//    @Autowired
//    ConsumerMaster consumerMaster;
//    @Autowired
//    ProducerMaster producerMaster;

    @ApiOperation(value = "添加消费者配置")
    @PostMapping("/consumer")
    public StandardResult createConsumerConfig(@RequestBody @Valid CreateConsumerConfigForm reqForm) {
        if (StringUtils.isEmpty(reqForm.getAsyncCallbackHandlerOnFailed()) && StringUtils.isEmpty(reqForm.getAsyncCallbackHandlerOnSucceed())) {
            log.info("传入同步发送配置");
        } else if (StringUtils.isNotEmpty(reqForm.getAsyncCallbackHandlerOnFailed()) && StringUtils.isNotEmpty(reqForm.getAsyncCallbackHandlerOnSucceed())) {
            log.info("传入异步发送配置");
        } else {
            Asserts.fail("参数异常！asyncCallbackHandlerOnSucceed与asyncCallbackHandlerOnFailed要么都填写要么都不填写！");
        }

        ConsumerConfig consumerConfig = ConvertUtil.convert(reqForm, ConsumerConfig.class);

        consumerConfigService.save(consumerConfig);

        return StandardResult.succeed("修改成功,暂不支持立即刷新，请等待线程池自动扫描...至多30秒，可使用查询接口查询扫描状态", null);
    }

    @ApiOperation(value = "查询正在工作的线程池")
    @GetMapping("/threadPool")
    public StandardResult getConsumerStatus() {
        JSONObject result = new JSONObject();
        result.put("aliveConsumer", ConsumerMaster.aliveRetryerWorkers.keySet());
        result.put("aliveProducer", ProducerMaster.aliveProducer.keySet());
        return StandardResult.succeed(result);
    }
}
