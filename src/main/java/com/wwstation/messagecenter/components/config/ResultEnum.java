package com.wwstation.messagecenter.components.config;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 自定义的通用回执信息封装枚举
 *
 * @author william
 * @description
 * @Date: 2020-12-07 12:23
 */
@Getter
@AllArgsConstructor
public enum ResultEnum {
    UNKNOWN_ERROR("500", 500, "未知异常"),
    UNAUTHORIZED("401", 401, "没有权限访问"),
    AUTHORIZE_FAILED("401", 401, "授权失败或令牌校验失败"),
    TOKEN_EXPIRED("401", 401, "令牌过期,请重新登陆"),
    LOGIN_FAIL("500", 500, "用户名或密码错误"),
    VALIDATE_FAILED("412", 412, "参数校验失败"),
    PROPERTY_NOT_FOUND("412",412,"没有找到相关的数据"),
    DB_ERROR("500", 500, "数据库操作异常"),
    OK("200", 200, "成功"),
    FEIGN_FAILED("400", 400, "服务调用失败，可能没有可用的服务");


    private String status;
    private Integer intStatus;
    private String messsage;
}
