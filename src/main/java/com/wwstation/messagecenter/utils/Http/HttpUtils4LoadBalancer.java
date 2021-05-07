package com.wwstation.messagecenter.utils.Http;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSONObject;
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
 * 使用负载均衡的内部服务动态调用
 * @author william
 * @description
 * @Date: 2021-03-07 11:02
 */
@Component
public class HttpUtils4LoadBalancer implements HttpUtils{
    @Override
    public JSONObject execute(RestTemplate template, HttpBean httpBean) throws Exception {
        if (httpBean.getMethod() == null) {
            HttpMethod method = HttpMethod.valueOf("GET");
            httpBean.setMethod(method);
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
        String url = httpBean.getUrl();

        StringJoiner sj = new StringJoiner("&", "?", "");
        Map<String, Object> parameters = httpBean.getParams();
        if (CollectionUtil.isNotEmpty(parameters)) {
            parameters.entrySet().stream().forEach(e -> {
                sj.add(e.getKey() + "=" + e.getValue());
            });
        }

        if (!url.startsWith("http://")) {
            url = "http://" + url;
        }

        return url + sj.toString();
    }
}
