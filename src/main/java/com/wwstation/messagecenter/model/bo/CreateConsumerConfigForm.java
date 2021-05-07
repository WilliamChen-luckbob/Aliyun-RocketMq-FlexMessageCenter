package com.wwstation.messagecenter.model.bo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Pattern;

/**
 * @author william
 * @description
 * @Date: 2021-05-06 19:36
 */
@Data
@ApiModel(value = "添加消费者配置入参")
public class CreateConsumerConfigForm {
    @ApiModelProperty(value = "消费者名称（此消息的名称）全局唯一")
    @NotEmpty(message = "自定义消费者名称不能为空")
    private String consumerName;

    @ApiModelProperty(value = "功能描述")
    private String description;

    @NotEmpty(message = "topic不能为空")
    private String topic;

    @ApiModelProperty(value = "MQ实例名称")
    @NotEmpty(message = "MQ实例名称不能为空")
    private String instanceId;

    @ApiModelProperty(value = "消费组ID")
    @NotEmpty(message = "消费组ID不能为空")
    private String groupId;

    @ApiModelProperty(value = "业务标记tag")
    @NotEmpty(message = "业务标记tag不能为空")
    private String tag;

    @ApiModelProperty(value = "实际被消费者调用的模块名")
    @NotEmpty(message = "实际被消费者调用的模块名不能为空")
    private String moduleName;

    @ApiModelProperty(value = "实际被消费者调用的处理服务url")
    @NotEmpty(message = "实际被消费者调用的处理服务url不能为空")
    private String processUrl;

    @ApiModelProperty(value = "是否是微服务间的内部调用1-内部调用 0-外部调用")
    @NotEmpty(message = "是否是微服务间的内部调用不能为空")
    private Boolean isInnerProcessor;

    @ApiModelProperty(value = "消费方式（暂不支持广播）")
    @Pattern(regexp = "(PULL|BROADCAST|)",message = "目前只支持PULL=主动拉取，BROADCAST=广播模式")
    private String consumeType;

    @ApiModelProperty(value = "异步回调成功处理接口url，留空默认使用同步发送")
    private String asyncCallbackHandlerOnSucceed;

    @ApiModelProperty(value = "异步回调失败处理接口url，留空默认使用同步发送")
    private String asyncCallbackHandlerOnFailed;
}
