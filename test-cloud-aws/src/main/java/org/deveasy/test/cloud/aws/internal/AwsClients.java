/*
 * AWS SDK v2 client factory for emulator/live modes.
 */
package org.deveasy.test.cloud.aws.internal;

import java.net.URI;
import org.deveasy.test.core.cloud.CloudMode;
import org.deveasy.test.core.cloud.TestCloudConfig;
import org.testcontainers.containers.localstack.LocalStackContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

public final class AwsClients {

  private AwsClients() {}

  public static S3Client s3(TestCloudConfig cfg) {
    if (cfg.mode() == CloudMode.EMULATOR) {
      LocalStackContainer ls = LocalStackHolder.ensureStartedS3();
      Region region = Region.of(defaultRegion(cfg));
      URI endpoint = ls.getEndpointOverride(LocalStackContainer.Service.S3);
      AwsCredentialsProvider creds =
          StaticCredentialsProvider.create(
              AwsBasicCredentials.create(ls.getAccessKey(), ls.getSecretKey()));
      return S3Client.builder()
          .endpointOverride(endpoint)
          .region(region)
          .credentialsProvider(creds)
          .forcePathStyle(true)
          .build();
    } else {
      // LIVE mode: use default provider chain; optional region from config
      Region region = Region.of(defaultRegion(cfg));
      return S3Client.builder()
          .region(region)
          .credentialsProvider(DefaultCredentialsProvider.create())
          .build();
    }
  }

  public static SqsClient sqs(TestCloudConfig cfg) {
    if (cfg.mode() == CloudMode.EMULATOR) {
      LocalStackContainer ls = LocalStackHolder.ensureStartedS3(); // same holder starts SQS
      Region region = Region.of(defaultRegion(cfg));
      URI endpoint = ls.getEndpointOverride(LocalStackContainer.Service.SQS);
      AwsCredentialsProvider creds =
          StaticCredentialsProvider.create(
              AwsBasicCredentials.create(ls.getAccessKey(), ls.getSecretKey()));
      return SqsClient.builder()
          .endpointOverride(endpoint)
          .region(region)
          .credentialsProvider(creds)
          .build();
    } else {
      Region region = Region.of(defaultRegion(cfg));
      return SqsClient.builder()
          .region(region)
          .credentialsProvider(DefaultCredentialsProvider.create())
          .build();
    }
  }

  private static String defaultRegion(TestCloudConfig cfg) {
    String r = cfg.regionOrLocation();
    return (r == null || r.isBlank()) ? "us-east-1" : r;
  }

  public static SnsClient sns(TestCloudConfig cfg) {
    if (cfg.mode() == CloudMode.EMULATOR) {
      LocalStackContainer ls = LocalStackHolder.ensureStartedSns();
      Region region = Region.of(defaultRegion(cfg));
      URI endpoint = ls.getEndpointOverride(LocalStackContainer.Service.SNS);
      AwsCredentialsProvider creds =
          StaticCredentialsProvider.create(
              AwsBasicCredentials.create(ls.getAccessKey(), ls.getSecretKey()));
      return SnsClient.builder()
          .endpointOverride(endpoint)
          .region(region)
          .credentialsProvider(creds)
          .build();
    } else {
      Region region = Region.of(defaultRegion(cfg));
      return SnsClient.builder()
          .region(region)
          .credentialsProvider(DefaultCredentialsProvider.create())
          .build();
    }
  }

  public static DynamoDbClient dynamodb(TestCloudConfig cfg) {
    if (cfg.mode() == CloudMode.EMULATOR) {
      LocalStackContainer ls = LocalStackHolder.ensureStartedDynamoDB();
      Region region = Region.of(defaultRegion(cfg));
      URI endpoint = ls.getEndpointOverride(LocalStackContainer.Service.DYNAMODB);
      AwsCredentialsProvider creds =
          StaticCredentialsProvider.create(
              AwsBasicCredentials.create(ls.getAccessKey(), ls.getSecretKey()));
      return DynamoDbClient.builder()
          .endpointOverride(endpoint)
          .region(region)
          .credentialsProvider(creds)
          .build();
    } else {
      Region region = Region.of(defaultRegion(cfg));
      return DynamoDbClient.builder()
          .region(region)
          .credentialsProvider(DefaultCredentialsProvider.create())
          .build();
    }
  }
}
