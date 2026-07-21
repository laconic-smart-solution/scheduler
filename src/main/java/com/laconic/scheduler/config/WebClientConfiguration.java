package com.laconic.scheduler.config;

import io.netty.channel.ChannelOption;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * Shared {@link WebClient} for outbound webhook calls.
 */
@Configuration
public class WebClientConfiguration {

    @Bean
    public WebClient webhookWebClient(
            WebClient.Builder builder,
            @Value("${scheduler.webhook.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${scheduler.webhook.response-timeout-ms:10000}") long responseTimeoutMs) {
        HttpClient httpClient =
                HttpClient.create()
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeoutMs)
                        .responseTimeout(Duration.ofMillis(responseTimeoutMs));

        return builder.clientConnector(new ReactorClientHttpConnector(httpClient)).build();
    }
}
