package com.example.uploads;

import io.smallrye.config.ConfigMapping;
import java.util.Optional;

/**
 * The application's own configuration keys. In production {@code endpoint} and the key pair are
 * unset and the SDK's defaults apply; the test fills them from the emulator.
 */
@ConfigMapping(prefix = "app.storage")
public interface StorageConfig {
  String bucket();

  String region();

  Optional<String> endpoint();

  Optional<String> accessKey();

  Optional<String> secretKey();
}
