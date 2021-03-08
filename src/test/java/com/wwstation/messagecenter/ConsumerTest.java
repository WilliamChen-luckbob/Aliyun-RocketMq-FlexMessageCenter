package com.wwstation.messagecenter;

import com.wwstation.messagecenter.components.config.MessageConfig;
import com.wwstation.messagecenter.components.worker.ConsumerWorker;
import com.wwstation.messagecenter.utils.HttpUtils4LoadBalancer;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.client.RestTemplate;

/**
 * @author william
 * @description
 * @Date: 2021-03-08 23:04
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class ConsumerTest {
    @InjectMocks
    private ConsumerWorker consumerWorker;
    @Mock
    MessageConfig config;
    @Mock
    private DiscoveryClient discoveryClient;
    @Mock
    private HttpUtils4LoadBalancer httpUtil;
    @Mock
    @Qualifier(value = "BalancedRestTemplate")
    private RestTemplate balancedRestTemplate;
    @Mock
    @Qualifier(value = "RestTemplate")
    private RestTemplate restTemplate;

    @Before
    public void init(){

    }
}
