/*
 * Copyright 2025
 * Apache License, Version 2.0
 */
package com.enrichmeai.test.cloud.aws;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.enrichmeai.test.core.cloud.CloudMode;
import com.enrichmeai.test.core.cloud.CloudProvider;
import com.enrichmeai.test.core.cloud.CloudServiceType;
import com.enrichmeai.test.core.cloud.ConnectionProperties;
import com.enrichmeai.test.core.cloud.TestCloudConfig;
import com.enrichmeai.test.core.cloud.capability.BlobStorage;
import com.enrichmeai.test.core.cloud.capability.Queue;
import com.enrichmeai.test.core.cloud.spi.CloudAdapter;
import com.enrichmeai.test.core.cloud.spi.CloudAdapters;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Epic 8: an application under test, configured only from {@code connectionProperties()}, reaches
 * the same LocalStack the capabilities use. The SDK clients here stand in for the application's.
 */
class AwsConnectionPropertiesIT {

  private static final TestCloudConfig CONFIG =
      TestCloudConfig.builder()
          .provider(CloudProvider.AWS)
          .mode(CloudMode.EMULATOR)
          .regionOrLocation("eu-west-1")
          .build();

  private final CloudAdapter adapter = CloudAdapters.get(CloudProvider.AWS, CONFIG);
  private final Map<String, String> p = adapter.connectionProperties();

  @Test
  void everyKeyOfTheContractIsPresentInEmulatorMode() {
    assertEquals("aws", p.get(ConnectionProperties.PROVIDER));
    assertEquals("emulator", p.get(ConnectionProperties.MODE));
    assertEquals("eu-west-1", p.get(ConnectionProperties.REGION));
    assertNotNull(p.get(ConnectionProperties.CREDENTIALS_KEY));
    assertNotNull(p.get(ConnectionProperties.CREDENTIALS_SECRET));
    for (CloudServiceType t :
        new CloudServiceType[] {
          CloudServiceType.STORAGE,
          CloudServiceType.QUEUE,
          CloudServiceType.PUBSUB,
          CloudServiceType.NOSQL
        }) {
      URI.create(p.get(ConnectionProperties.endpoint(t)));
    }
  }

  @Test
  void anS3ClientBuiltFromTheMapSeesWhatTheCapabilityWrote() {
    String bucket = "conn-props-" + UUID.randomUUID().toString().substring(0, 8);
    BlobStorage storage = adapter.blobStorage();
    storage.ensureBucket(bucket);
    try (S3Client s3 =
        S3Client.builder()
            .endpointOverride(
                URI.create(p.get(ConnectionProperties.endpoint(CloudServiceType.STORAGE))))
            .region(Region.of(p.get(ConnectionProperties.REGION)))
            .credentialsProvider(credentials())
            .forcePathStyle(true)
            .build()) {
      s3.putObject(b -> b.bucket(bucket).key("k"), RequestBody.fromString("from the app"));
      assertArrayEquals(
          "from the app".getBytes(StandardCharsets.UTF_8), storage.getObject(bucket, "k"));
    } finally {
      storage.deleteObject(bucket, "k");
      storage.deleteBucket(bucket);
    }
  }

  @Test
  void anSqsClientBuiltFromTheMapReachesTheCapabilitysQueue() {
    String name = "conn-props-" + UUID.randomUUID().toString().substring(0, 8);
    Queue queue = adapter.queue();
    queue.ensureQueue(name);
    try (SqsClient sqs =
        SqsClient.builder()
            .endpointOverride(
                URI.create(p.get(ConnectionProperties.endpoint(CloudServiceType.QUEUE))))
            .region(Region.of(p.get(ConnectionProperties.REGION)))
            .credentialsProvider(credentials())
            .build()) {
      String url = sqs.getQueueUrl(b -> b.queueName(name)).queueUrl();
      sqs.sendMessage(b -> b.queueUrl(url).messageBody("hello"));
      assertEquals(Optional.of("hello"), queue.receive(name, Duration.ofSeconds(5)));
    } finally {
      queue.deleteQueue(name);
    }
  }

  private StaticCredentialsProvider credentials() {
    return StaticCredentialsProvider.create(
        AwsBasicCredentials.create(
            p.get(ConnectionProperties.CREDENTIALS_KEY),
            p.get(ConnectionProperties.CREDENTIALS_SECRET)));
  }
}
