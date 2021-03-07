package com.wwstation.messagecenter.utils;

import com.alibaba.fastjson.JSONObject;
import com.wwstation.messagecenter.model.bo.HttpBean;
import org.springframework.web.client.RestTemplate;

/**
 * @author william
 * @description
 * @Date: 2021-03-07 18:51
 */
public interface HttpUtils {
    /**
     * 执行http请求
     *
     * @param template RestTemplate
     * @param httpBean httpBean
     * @return 响应的json串
     * @throws Exception 如果发生任何异常或请求结果不为200，将报错
     */
    JSONObject execute(RestTemplate template, HttpBean httpBean) throws Exception;
}
