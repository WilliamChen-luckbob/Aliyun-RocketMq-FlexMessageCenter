package com.wwstation.messagecenter.utils;

import cn.hutool.core.util.ReUtil;

import java.util.List;
import java.util.regex.Pattern;

/**
 * feignClient调用过程中的一些小工具
 *
 * @author william
 * @description
 * @Date: 2020-12-30 16:49
 */
public class FeignUtils {
    public static String getResultFromRestTemplateRequestError(String localizedMessage) {
        return ReUtil.get("(?<=\\[)\\{.+\\}(?=\\])", localizedMessage, 0);
    }

    public static String getResultFromFeignClientRequestError(String message){

        String pattern = "(?<=\"]: [\")([^\\}]*)(?=\"]\")";
        List<String> allGroups = ReUtil.getAllGroups(Pattern.compile(pattern), message, true);
        return ReUtil.get(pattern, message, 0);
    }

}
