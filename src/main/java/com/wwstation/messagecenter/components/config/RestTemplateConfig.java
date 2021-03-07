package com.wwstation.messagecenter.components.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * @author william
 * @description
 * @Date: 2021-03-07 11:28
 */
@Configuration
public class RestTemplateConfig {
    @Bean(value = "BalancedRestTemplate")
    @LoadBalanced
    public RestTemplate getBalancedRestTemplate(){
        return new RestTemplate();
    }
    @Bean(value = "RestTemplate")
    public RestTemplate getRestTemplate(){
        return new RestTemplate();
    }

}
