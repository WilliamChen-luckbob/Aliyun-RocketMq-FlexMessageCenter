package com.wwstation.messagecenter.model.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author william
 * @since 2021-03-08
 */
@Data
  @EqualsAndHashCode(callSuper = false)
    @ApiModel(value="FailedMessage对象", description="")
public class FailedMessage extends Model<FailedMessage> {

    private static final long serialVersionUID = 1L;

      @TableId(value = "id", type = IdType.AUTO)
      private Long id;

      @ApiModelProperty(value = "rocketMQID")
      private String mqId;

      @ApiModelProperty(value = "consumer_config表id")
      private Long consumerConfigId;

      @ApiModelProperty(value = "消息内容json")
      private String message;

      @ApiModelProperty(value = "创建时间")
      private LocalDateTime createTime;

      @ApiModelProperty(value = "重试次数")
      private Integer retryTimes;

      @ApiModelProperty(value = "下一次预计启动时间")
      private LocalDateTime nextRetryTime;


    @Override
    protected Serializable pkVal() {
          return this.id;
      }

}
