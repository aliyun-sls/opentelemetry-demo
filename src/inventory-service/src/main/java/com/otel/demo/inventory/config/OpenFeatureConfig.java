package com.otel.demo.inventory.config;

import dev.openfeature.contrib.providers.flagd.FlagdOptions;
import dev.openfeature.contrib.providers.flagd.FlagdProvider;
import dev.openfeature.sdk.OpenFeatureAPI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PostConstruct;

@Configuration
public class OpenFeatureConfig {

    @Value("${flagd.host:flagd}")
    private String flagdHost;

    @Value("${flagd.port:8013}")
    private int flagdPort;

    @PostConstruct
    public void initializeOpenFeature() {
        FlagdOptions options = FlagdOptions.builder()
                .host(flagdHost)
                .port(flagdPort)
                .tls(false)
                .build();

        FlagdProvider flagdProvider = new FlagdProvider(options);
        OpenFeatureAPI.getInstance().setProvider(flagdProvider);
    }
} 