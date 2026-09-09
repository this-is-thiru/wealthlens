package com.thiru.wealthlens.brokercharges.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.charges")
public record ChargeEngineProperties(boolean engineEnabled, boolean shadowRecording, boolean authoritative) {}
