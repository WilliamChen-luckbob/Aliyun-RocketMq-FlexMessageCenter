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
 * @since 2021-03-08
 */
@Data
  @EqualsAndHashCode(callSuper = false)
    @ApiModel(value="DeadMessage对象", description="")
public class DeadMessage extends Model<DeadMessage> {

    private static final long serialVersionUID = 1L;

      @TableId(value = "id", type = IdType.AUTO)
      private Long id;

    private String mqId;

    private Long consumerConfigId;

      @ApiModelProperty(value = "消息内容JSON String")
      private String message;

      @ApiModelProperty(value = "送入死信表的时间")
      private LocalDateTime deadTime;


    @Override
    protected Serializable pkVal() {
          return this.id;
      }

}
