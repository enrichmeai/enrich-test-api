package com.example.uploads;

import com.enrichmeai.test.core.cloud.CloudProvider;
import com.enrichmeai.test.core.cloud.CloudServiceType;
import com.enrichmeai.test.core.cloud.ConnectionProperties;
import com.enrichmeai.test.core.cloud.TestCloudConfig;
import com.enrichmeai.test.core.cloud.capability.BlobStorage;
import com.enrichmeai.test.core.cloud.spi.CloudAdapter;
import com.enrichmeai.test.core.cloud.spi.CloudAdapters;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Epic 8, Story 8.3. Quarkus calls {@link #start()} before the application starts and applies the
 * map it returns as configuration. The only glue is the library's provider-neutral keys copied into
 * the application's own {@code app.storage.*} keys: no vendor SDK type, nothing from {@code
 * test-cloud-aws} and no Testcontainers type. The provider is chosen with the library's own {@link
 * CloudProvider} enum, as {@code @WithCloud} does.
 */
public class EmulatorResource implements QuarkusTestResourceLifecycleManager {

  static final String BUCKET = "uploads-example";
  static final String SEED_KEY = "seeded-by-the-library.txt";
  static final String SEED = "written through enrich-test-api's BlobStorage";

  private BlobStorage storage;

  @Override
  public Map<String, String> start() {
    CloudAdapter cloud =
        CloudAdapters.get(
            CloudProvider.AWS, TestCloudConfig.builder().regionOrLocation("eu-west-1").build());
    Map<String, String> c = cloud.connectionProperties();

    // A file the library writes, which the test then reads through the application: proof the
    // application talks to this emulator and not to some other one.
    storage = cloud.blobStorage();
    storage.ensureBucket(BUCKET);
    storage.putObject(BUCKET, SEED_KEY, SEED.getBytes(StandardCharsets.UTF_8), "text/plain");

    return Map.of(
        "app.storage.bucket", BUCKET,
        "app.storage.region", c.get(ConnectionProperties.REGION),
        "app.storage.endpoint", c.get(ConnectionProperties.endpoint(CloudServiceType.STORAGE)),
        "app.storage.access-key", c.get(ConnectionProperties.CREDENTIALS_KEY),
        "app.storage.secret-key", c.get(ConnectionProperties.CREDENTIALS_SECRET));
  }

  @Override
  public void stop() {
    if (storage != null) {
      storage.listKeys(BUCKET, "").forEach(k -> storage.deleteObject(BUCKET, k));
      storage.deleteBucket(BUCKET);
    }
  }
}
