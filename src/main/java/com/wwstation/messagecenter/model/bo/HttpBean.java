package com.wwstation.messagecenter.model.bo;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.http.HttpMethod;

import java.util.HashMap;
import java.util.Map;

/**
 * @author william
 * @description
 * @Date: 2021-01-22 17:46
 */
@Data
@AllArgsConstructor
public class HttpBean {
    private String url;
    private HttpMethod method;
    private Map<String, String> headers;
    private Map<String, Object> params;
    private Map<String, Object> body;
    private String authUsername;
    private String authPassword;
    public HttpBean(String url) {
        this.url = url;
        headers = new HashMap<>();
        params = new HashMap<>();
        body = new HashMap<>();
    }
    public HttpBean() {
        headers = new HashMap<>();
        params = new HashMap<>();
        body = new HashMap<>();
    }
}
