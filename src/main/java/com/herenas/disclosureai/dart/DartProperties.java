package com.herenas.disclosureai.dart;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dart")
public record DartProperties(String apiKey, String baseUrl) {
}
