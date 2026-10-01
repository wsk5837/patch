package com.gazellio.platform.config;

import org.springframework.context.annotation.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class HttpClientConfig {
  @Bean RestClient.Builder restClientBuilder(){
    SimpleClientHttpRequestFactory f=new SimpleClientHttpRequestFactory();
    f.setConnectTimeout(5000);
    f.setReadTimeout(30000);
    return RestClient.builder().requestFactory(f);
  }
}
