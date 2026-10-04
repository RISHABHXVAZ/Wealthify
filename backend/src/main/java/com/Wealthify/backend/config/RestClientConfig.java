package com.Wealthify.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
@Slf4j
public class RestClientConfig {

    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;
    public static final int DEFAULT_READ_TIMEOUT_MS = 10000;

    @Value("${app.ai.http.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${app.ai.http.read-timeout-ms:10000}")
    private int readTimeoutMs;

    public int getConnectTimeoutMs() {
        return connectTimeoutMs > 0 ? connectTimeoutMs : DEFAULT_CONNECT_TIMEOUT_MS;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs > 0 ? readTimeoutMs : DEFAULT_READ_TIMEOUT_MS;
    }

    @Bean
    public RestTemplate restTemplate() {
        int effectiveConnectTimeout = getConnectTimeoutMs();
        int effectiveReadTimeout = getReadTimeoutMs();

        log.info("Configuring RestTemplate with connectTimeout={}ms, readTimeout={}ms",
                effectiveConnectTimeout, effectiveReadTimeout);

        return createRestTemplate(effectiveConnectTimeout, effectiveReadTimeout);
    }

    public static RestTemplate createRestTemplate(int connectTimeoutMs, int readTimeoutMs) {
        int effectiveConnect = connectTimeoutMs > 0 ? connectTimeoutMs : DEFAULT_CONNECT_TIMEOUT_MS;
        int effectiveRead = readTimeoutMs > 0 ? readTimeoutMs : DEFAULT_READ_TIMEOUT_MS;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(effectiveConnect));
        factory.setReadTimeout(Duration.ofMillis(effectiveRead));
        return new RestTemplate(factory);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}