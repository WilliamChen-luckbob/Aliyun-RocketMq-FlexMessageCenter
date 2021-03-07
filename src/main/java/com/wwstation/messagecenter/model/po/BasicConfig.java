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
 * 
 * </p>
 *
 * @author william
 * @since 2021-03-07
 */
@Data
  @EqualsAndHashCode(callSuper = false)
    @ApiModel(value="BasicConfig对象", description="")
public class BasicConfig extends Model<BasicConfig> {

    private static final long serialVersionUID = 1L;

      @TableId(value = "id", type = IdType.AUTO)
      private Long id;

    private String nameServerAddr;

    private String accessKey;

    private String secretKey;


    @Override
    protected Serializable pkVal() {
          return this.id;
      }

}
