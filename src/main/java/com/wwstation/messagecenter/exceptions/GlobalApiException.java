package com.wwstation.messagecenter.exceptions;

import com.wwstation.messagecenter.components.config.ResultEnum;

/**
 * 主动抛出的全局异常
 *
 * @author william
 * @description
 * @Date: 2020-12-11 18:14
 */
public class GlobalApiException extends RuntimeException  {
    private String status;

    public GlobalApiException(String code, String message) {
        super(message);
        this.status = code;
    }

    public GlobalApiException(ResultEnum errorCode) {
        super(errorCode.getMesssage());
        this.status = errorCode.getStatus();
    }

    public GlobalApiException(String message) {
        super(message);
        this.status = String.valueOf(ResultEnum.UNKNOWN_ERROR.getStatus());
    }
}
