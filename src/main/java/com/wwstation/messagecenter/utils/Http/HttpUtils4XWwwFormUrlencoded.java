package com.wwstation.messagecenter.utils.Http;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.StringJoiner;

/**
 * @author william
 * @description
 * @Date: 2021-02-02 18:30
 */
@Component
public class HttpUtils4XWwwFormUrlencoded implements HttpUtils {
    private static String getActualUrl(HttpBean httpBean) {
        String url = httpBean.getUrl();
        if (!url.startsWith("http")) {
            url = "http://" + url;
        }
        StringJoiner sj = new StringJoiner("&", "?", "");
        Map<String, Object> parameters = httpBean.getParams();
        if (CollectionUtil.isNotEmpty(parameters)) {
            parameters.entrySet().stream().forEach(e -> {
                sj.add(e.getKey() + "=" + e.getValue());
            });
        }
        return url + sj.toString();
    }

    private static HttpEntity getHttpEntity(HttpBean httpBean) {
        //拼装headers+body组装成httpentity
        HttpHeaders headers = new HttpHeaders();

        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAcceptCharset(Lists.newArrayList(StandardCharsets.UTF_8));

        //如果主动传入header不为空，装载除了contentType之外的header
        if (CollectionUtil.isNotEmpty(httpBean.getHeaders())) {
            httpBean.getHeaders()
                .entrySet()
                .stream()
                .filter(e -> !e.getKey().startsWith("content") &&
                    !e.getKey().toLowerCase().endsWith("type"))
                .forEach(e -> headers.set(e.getKey(), e.getValue()));
        }

        //转为MultiValueMap
        Map<String, Object> bodyObject = httpBean.getBody();
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        if (CollectionUtil.isNotEmpty(bodyObject)) {
            bodyObject.entrySet()
                .stream()
                .forEach(e -> {
                    body.add(e.getKey(), e.getValue());
                });
        }

        if (httpBean.getAuthPassword() != null && httpBean.getAuthUsername() != null) {
            headers.setBasicAuth(httpBean.getAuthUsername(), httpBean.getAuthPassword());
        }

        return new HttpEntity(body, headers);
    }

    @Override
    public JSONObject execute(RestTemplate template, HttpBean httpBean) throws Exception {
        if (httpBean.getMethod() == null) {
            HttpMethod method = HttpMethod.valueOf("GET");
        }

        //拼装param
        String actualUrl = getActualUrl(httpBean);
        //组装httpentity
        HttpEntity httpEntity = getHttpEntity(httpBean);

        ResponseEntity<JSONObject> exchange = null;

        exchange = template.exchange(actualUrl,
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
