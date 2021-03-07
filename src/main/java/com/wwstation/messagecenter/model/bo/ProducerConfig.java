package com.wwstation.messagecenter.model.bo;

import lombok.Data;

/**
 * 生产者配置
 * @author william
 * @description
 * @Date: 2021-03-05 15:16
 */
@Data
public class ProducerConfig {
    private String topic;
    private String instanceId;
}
