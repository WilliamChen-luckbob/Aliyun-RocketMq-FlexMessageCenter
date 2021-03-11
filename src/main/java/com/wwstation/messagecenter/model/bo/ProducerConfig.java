package com.wwstation.messagecenter.model.bo;

import lombok.Data;

import java.io.Serializable;

/**
 * 生产者配置
 * @author william
 * @description
 * @Date: 2021-03-05 15:16
 */
@Data
public class ProducerConfig implements Serializable {
    private static final long serialVersionUID = -6594234250498537679L;
    private String topic;
    private String instanceId;
}
