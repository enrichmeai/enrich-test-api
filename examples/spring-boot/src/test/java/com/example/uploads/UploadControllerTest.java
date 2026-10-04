package com.example.uploads;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.enrichmeai.test.core.cloud.CloudProvider;
import com.enrichmeai.test.core.cloud.CloudServiceType;
import com.enrichmeai.test.core.cloud.ConnectionProperties;
import com.enrichmeai.test.core.cloud.TestCloudConfig;
import com.enrichmeai.test.core.cloud.capability.BlobStorage;
import com.enrichmeai.test.core.cloud.spi.CloudAdapter;
import com.enrichmeai.test.core.cloud.spi.CloudAdapters;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Epic 8, Story 8.3. The application starts with its S3 client pointed at the emulator the library
 * started, configured only from {@code connectionProperties()}: no vendor SDK type, nothing from
 * {@code test-cloud-aws} and no Testcontainers type appear here. The provider is chosen with the
 * library's own {@link CloudProvider} enum, as {@code @WithCloud} does.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UploadControllerTest {

  private static final String BUCKET = "uploads-example";

  private static final CloudAdapter CLOUD =
      CloudAdapters.get(
          CloudProvider.AWS, TestCloudConfig.builder().regionOrLocation("eu-west-1").build());

  private static final Map<String, String> CONNECTION = CLOUD.connectionProperties();

  private static final BlobStorage STORAGE = CLOUD.blobStorage();

  /** The only glue: the library's neutral keys copied into the application's own keys. */
  @DynamicPropertySource
  static void pointTheApplicationAtTheEmulator(DynamicPropertyRegistry registry) {
    registry.add("app.storage.bucket", () -> BUCKET);
    registry.add("app.storage.region", () -> CONNECTION.get(ConnectionProperties.REGION));
    registry.add(
        "app.storage.endpoint",
        () -> CONNECTION.get(ConnectionProperties.endpoint(CloudServiceType.STORAGE)));
    registry.add(
        "app.storage.access-key", () -> CONNECTION.get(ConnectionProperties.CREDENTIALS_KEY));
    registry.add(
        "app.storage.secret-key", () -> CONNECTION.get(ConnectionProperties.CREDENTIALS_SECRET));
  }

  @BeforeAll
  static void createTheBucket() {
    STORAGE.ensureBucket(BUCKET);
  }

  @AfterAll
  static void removeTheBucket() {
    STORAGE.listKeys(BUCKET, "").forEach(k -> STORAGE.deleteObject(BUCKET, k));
    STORAGE.deleteBucket(BUCKET);
  }

  @Value("${local.server.port}")
  private int port;

  @Test
  void anUploadThroughTheApplicationLandsInTheEmulatorsBucket() throws Exception {
    byte[] body = "hello from the app".getBytes(StandardCharsets.UTF_8);

    HttpResponse<Void> response =
        HttpClient.newHttpClient()
            .send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/files/hello.txt"))
                    .header("Content-Type", "application/octet-stream")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build(),
                HttpResponse.BodyHandlers.discarding());

    assertEquals(201, response.statusCode());
    assertArrayEquals(body, STORAGE.getObject(BUCKET, "hello.txt"));
  }
}
