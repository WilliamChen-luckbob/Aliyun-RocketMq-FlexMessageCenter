package com.wwstation.messagecenter.model.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.baomidou.mybatisplus.annotation.TableId;
import java.io.Serializable;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 * 消费者配置表
 * </p>
 *
 * @author william
 * @since 2021-03-07
 */
@Data
  @EqualsAndHashCode(callSuper = false)
    @ApiModel(value="ConsumerConfig对象", description="消费者配置表")
public class ConsumerConfig extends Model<ConsumerConfig> {

    private static final long serialVersionUID = 1L;

      @TableId(value = "id", type = IdType.AUTO)
      private Long id;

      @ApiModelProperty(value = "消费者名称（此消息的名称）唯一")
      private String consumerName;

      @ApiModelProperty(value = "功能描述")
      private String description;

    private String topic;

      @ApiModelProperty(value = "MQ实例名称")
      private String instanceId;

      @ApiModelProperty(value = "消费组名称")
      private String groupId;

    private String tag;

      @ApiModelProperty(value = "实际被消费者调用的处理服务")
      private String moduleName;

      @ApiModelProperty(value = "实际被消费者调用的处理服务url")
      private String processUrl;

      @ApiModelProperty(value = "是否是微服务间的内部调用1-内部调用 0-外部调用")
      private Boolean isInnerProcessor;


    @Override
    protected Serializable pkVal() {
          return this.id;
      }

}
