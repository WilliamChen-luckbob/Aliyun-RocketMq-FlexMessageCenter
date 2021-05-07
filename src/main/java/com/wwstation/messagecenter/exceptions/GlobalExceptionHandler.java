package com.wwstation.messagecenter.exceptions;

import com.wwstation.messagecenter.components.config.ResultEnum;
import com.wwstation.messagecenter.utils.StandardResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @author william
 * @description
 * @Date: 2020-12-11 18:14
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    /**
     * 全局处理：捕获主动抛出的普通异常
     *
     * @param ex
     * @return
     */
    @ResponseStatus(HttpStatus.OK)
    @ExceptionHandler(GlobalApiException.class)
    public StandardResult handleGlobalApiException(GlobalApiException ex) {
        log.error(String.format("全局异常处理提示：%s,堆栈信息:",ex.getMessage()), ex);
        return StandardResult.fail(ResultEnum.UNKNOWN_ERROR, ex.getMessage());
    }



    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public StandardResult handleUnknownException(Exception ex) throws Throwable {
        log.error(String.format("未捕获的异常！%s", String.format("全局异常处理提示：%s,堆栈信息:",ex.getMessage())), ex);
        return StandardResult.fail(ResultEnum.UNKNOWN_ERROR, "出现了神奇的错误，还没有捕捉到！");
    }


}
