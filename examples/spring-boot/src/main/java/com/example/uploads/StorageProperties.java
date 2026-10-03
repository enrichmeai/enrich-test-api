package com.example.uploads;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The application's own configuration keys. In production {@code endpoint} and the key pair are
 * unset and the SDK's defaults apply; the test fills them from the emulator.
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
    String bucket, String region, String endpoint, String accessKey, String secretKey) {}
