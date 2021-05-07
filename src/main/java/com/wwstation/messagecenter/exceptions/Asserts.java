package com.wwstation.messagecenter.exceptions;

import com.wwstation.messagecenter.components.config.ResultEnum;

/**
 * 全局断言处理类，抛出指定的全局异常，并交给全局异常处理类进行处理
 *
 * @author william
 * @description
 * @Date: 2020-12-11 18:15
 */
public class Asserts {
    public static void fail(String message) {
        throw new GlobalApiException(message);
    }

    public static void fail(ResultEnum errorCode) {
        throw new GlobalApiException(errorCode);
    }

    public static void fail(ResultEnum errorCode, String message) {
        throw new GlobalApiException(errorCode.getStatus(), message);
    }
}
