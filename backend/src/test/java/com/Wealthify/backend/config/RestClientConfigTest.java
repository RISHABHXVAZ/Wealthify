package com.Wealthify.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class RestClientConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(RestClientConfig.class);

    @Test
    @DisplayName("Default timeouts: 5000ms connect timeout, 10000ms read timeout")
    void testDefaultTimeouts() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(RestTemplate.class);
            assertThat(context).hasSingleBean(RestClientConfig.class);

            RestClientConfig config = context.getBean(RestClientConfig.class);
            assertThat(config.getConnectTimeoutMs()).isEqualTo(5000);
            assertThat(config.getReadTimeoutMs()).isEqualTo(10000);

            RestTemplate restTemplate = context.getBean(RestTemplate.class);
            assertThat(restTemplate.getRequestFactory()).isInstanceOf(SimpleClientHttpRequestFactory.class);

            SimpleClientHttpRequestFactory factory = (SimpleClientHttpRequestFactory) restTemplate.getRequestFactory();
            assertThat(ReflectionTestUtils.getField(factory, "connectTimeout")).isEqualTo(5000);
            assertThat(ReflectionTestUtils.getField(factory, "readTimeout")).isEqualTo(10000);
        });
    }

    @Test
    @DisplayName("Property overrides: custom timeouts successfully applied to RestTemplate")
    void testPropertyOverrides() {
        contextRunner
                .withPropertyValues(
                        "app.ai.http.connect-timeout-ms=2500",
                        "app.ai.http.read-timeout-ms=7500"
                )
                .run(context -> {
                    RestClientConfig config = context.getBean(RestClientConfig.class);
                    assertThat(config.getConnectTimeoutMs()).isEqualTo(2500);
                    assertThat(config.getReadTimeoutMs()).isEqualTo(7500);

                    RestTemplate restTemplate = context.getBean(RestTemplate.class);
                    SimpleClientHttpRequestFactory factory = (SimpleClientHttpRequestFactory) restTemplate.getRequestFactory();
                    assertThat(ReflectionTestUtils.getField(factory, "connectTimeout")).isEqualTo(2500);
                    assertThat(ReflectionTestUtils.getField(factory, "readTimeout")).isEqualTo(7500);
                });
    }

    @Test
    @DisplayName("Defensive fallback: non-positive timeouts fall back to positive defaults to prevent infinite hangs")
    void testDefensiveFallbackForNonPositiveTimeouts() {
        contextRunner
                .withPropertyValues(
                        "app.ai.http.connect-timeout-ms=0",
                        "app.ai.http.read-timeout-ms=-100"
                )
                .run(context -> {
                    RestClientConfig config = context.getBean(RestClientConfig.class);
                    assertThat(config.getConnectTimeoutMs()).isEqualTo(RestClientConfig.DEFAULT_CONNECT_TIMEOUT_MS);
                    assertThat(config.getReadTimeoutMs()).isEqualTo(RestClientConfig.DEFAULT_READ_TIMEOUT_MS);

                    RestTemplate restTemplate = context.getBean(RestTemplate.class);
                    SimpleClientHttpRequestFactory factory = (SimpleClientHttpRequestFactory) restTemplate.getRequestFactory();
                    assertThat(ReflectionTestUtils.getField(factory, "connectTimeout")).isEqualTo(5000);
                    assertThat(ReflectionTestUtils.getField(factory, "readTimeout")).isEqualTo(10000);
                });
    }

    @Test
    @DisplayName("createRestTemplate helper configures factory timeouts accurately")
    void testCreateRestTemplateHelper() {
        RestTemplate customTemplate = RestClientConfig.createRestTemplate(300, 600);
        assertThat(customTemplate.getRequestFactory()).isInstanceOf(SimpleClientHttpRequestFactory.class);

        SimpleClientHttpRequestFactory factory = (SimpleClientHttpRequestFactory) customTemplate.getRequestFactory();
        assertThat(ReflectionTestUtils.getField(factory, "connectTimeout")).isEqualTo(300);
        assertThat(ReflectionTestUtils.getField(factory, "readTimeout")).isEqualTo(600);
    }
}
