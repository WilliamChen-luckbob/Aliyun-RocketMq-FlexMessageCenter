package com.wwstation.messagecenter.utils;

import cn.hutool.core.util.ReUtil;

/**
 * 调用过程中的一些小工具
 *
 * @author william
 * @description
 * @Date: 2020-12-30 16:49
 */
public class FeedBackUtils {
    /**
     * 尝试从异常中匹配明确的提示信息
     * @param localizedMessage
     * @return
     */
    public static String getResultFromError(String localizedMessage) {
        return ReUtil.get("(?<=\\[)\\{.+\\}(?=\\])", localizedMessage, 0);
    }


}
