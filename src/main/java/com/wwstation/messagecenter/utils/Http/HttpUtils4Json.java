package com.wwstation.messagecenter.utils.Http;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSONObject;
import com.wwstation.messagecenter.utils.StandardResult;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.StringJoiner;

/**
 * @author william
 * @description
 * @Date: 2021-01-22 17:44
 */
@Component
public class HttpUtils4Json implements HttpUtils {
    /**
     * 推送基本的 HTTP POST 请求
     *
     * @param restTemplate 当前项目中的restTemplate
     * @param httpBean     包装类
     * @return
     * @throws Exception 抛出异常由上级调用者处理
     */
    public static StandardResult<JSONObject> post(RestTemplate restTemplate, HttpBean httpBean) throws Exception {
        HttpMethod method = HttpMethod.valueOf("POST");

        //拼装param
        String actualUrl = getActualUrl(httpBean);
        //组装httpentity
        HttpEntity httpEntity = getHttpEntity(httpBean);

        ResponseEntity<JSONObject> exchange = null;

        exchange = restTemplate.exchange(actualUrl,
            httpBean.getMethod(),
            httpEntity,
            JSONObject.class);

        JSONObject response = exchange.getBody();

        return StandardResult.succeed(response);
    }

    private static HttpEntity getHttpEntity(HttpBean httpBean) {
        //拼装headers+body组装成httpentity
        HttpHeaders headers = new HttpHeaders();
        Map<String, Object> body = null;

        //此类只发applicationJson
        headers.setContentType(MediaType.APPLICATION_JSON);
        //如果主动传入header不为空，装载除了contentType之外的header
        if (CollectionUtil.isNotEmpty(httpBean.getHeaders())) {
            httpBean.getHeaders()
                .entrySet()
                .stream()
                .filter(e -> !e.getKey().startsWith("content") &&
                    !e.getKey().toLowerCase().endsWith("type"))
                .forEach(e -> headers.set(e.getKey(), e.getValue()));
        }
        //json/application的直接返回body
        body = httpBean.getBody();

        if (httpBean.getAuthPassword() != null && httpBean.getAuthUsername() != null) {
            headers.setBasicAuth(httpBean.getAuthUsername(), httpBean.getAuthPassword());
        }

        return new HttpEntity(body, headers);

    }

    private static String getActualUrl(HttpBean httpBean) {
        StringJoiner sj = new StringJoiner("&", "?", "");
        Map<String, Object> parameters = httpBean.getParams();
        if (CollectionUtil.isNotEmpty(parameters)) {
            parameters.entrySet().stream().forEach(e -> {
                sj.add(e.getKey() + "=" + e.getValue());
            });
        }
        return httpBean.getUrl() + sj.toString();
    }

    public JSONObject execute(RestTemplate restTemplate, HttpBean httpBean) throws Exception {
        if (httpBean.getMethod() == null) {
            HttpMethod method = HttpMethod.valueOf("GET");
        }

        //拼装param
        String actualUrl = getActualUrl(httpBean);
        //组装httpentity
        HttpEntity httpEntity = getHttpEntity(httpBean);

        ResponseEntity<JSONObject> exchange = null;

        exchange = restTemplate.exchange(actualUrl,
            httpBean.getMethod(),
            httpEntity,
            JSONObject.class);
        if (!exchange.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException(String.format("http请求发送失败 %s", exchange.getBody().toJSONString()));
        }
        JSONObject response = exchange.getBody();

        return response;
    }


}
