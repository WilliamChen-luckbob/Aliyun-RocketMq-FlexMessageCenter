package com.wwstation.messagecenter.model.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 * 
 * </p>
 *
 * @author william
 * @since 2021-03-07
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

      @ApiModelProperty(value = "消息内容json")
      private String message;

    private LocalDateTime createTime;


    @Override
    protected Serializable pkVal() {
          return this.id;
      }

}
