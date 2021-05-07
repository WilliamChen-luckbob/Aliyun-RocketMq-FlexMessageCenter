package com.wwstation.messagecenter.components.config;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 消费类型枚举
 *
 * @author william
 * @description
 * @Date: 2021-04-28 17:40
 */
@Getter
@AllArgsConstructor
public enum ConsumeTypeEnum {
    //    'NORMAL','BROADCAST','PULL'
    NORMAL("NORMAL", "(暂不支持)消费者被动接收数据并随机推送到任意负载均衡的服务上"),

    PULL("PULL", "消费者主动轮询并随机推送到任意负载均衡的服务上"),

    BROADCAST("BROADCAST", "消费者主动轮询并将数据广播到注册中心列表中的所有服务上"),

    DEFAULT("", "缺省时默认使用PULL进行处理");

    private String type;
    private String desc;

    /**
     * 判定输入的字符串是否匹配枚举
     *
     * @param inputString
     * @param targetEnum
     * @return
     */
    public static Boolean typeMatch(String inputString, ConsumeTypeEnum targetEnum) {
        return targetEnum.getType().equals(inputString);
    }
}
