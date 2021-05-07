package com.wwstation.messagecenter.utils;

import com.wwstation.messagecenter.components.config.ResultEnum;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author william
 * @description
 * @Date: 2020-12-22 17:05
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel
public class StandardResult<T> implements Serializable {
    private static final long serialVersionUID = 1856401789526776784L;
    @ApiModelProperty(value = "响应码")
    private Integer status;
    @ApiModelProperty(value = "响应提示")
    private String note;
    @ApiModelProperty(value = "响应数据")
    private T data;

    public static StandardResult fail() {
        return new StandardResult(ResultEnum.UNKNOWN_ERROR.getIntStatus(),
            ResultEnum.UNKNOWN_ERROR.getMesssage(),
            null);
    }

    public static StandardResult fail(String message) {
        return new StandardResult(ResultEnum.UNKNOWN_ERROR.getIntStatus(),
            message,
            null);
    }

    public static StandardResult fail(Integer code, String message) {
        return new StandardResult(code,
            message,
            null);
    }

    public static StandardResult fail(ResultEnum resultEnum, String message) {
        return new StandardResult(resultEnum.getIntStatus(), message, null);
    }

    public static StandardResult succeed() {
        return new StandardResult(ResultEnum.OK.getIntStatus(), ResultEnum.OK.getMesssage(), null);
    }


    public static <T> StandardResult succeed(T data) {
        return new StandardResult(ResultEnum.OK.getIntStatus(), ResultEnum.OK.getMesssage(), data);
    }

    public static <T> StandardResult succeed(String msg, T data) {
        return new StandardResult(ResultEnum.OK.getIntStatus(), msg, data);
    }

}
