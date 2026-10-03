package com.example.uploads;

import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

@Configuration
class StorageConfig {

  @Bean(destroyMethod = "close")
  S3Client s3(StorageProperties p) {
    S3ClientBuilder b = S3Client.builder().region(Region.of(p.region()));
    if (p.endpoint() != null && !p.endpoint().isBlank()) {
      b.endpointOverride(URI.create(p.endpoint())).forcePathStyle(true);
    }
    if (p.accessKey() != null && !p.accessKey().isBlank()) {
      b.credentialsProvider(
          StaticCredentialsProvider.create(
              AwsBasicCredentials.create(p.accessKey(), p.secretKey())));
    } else {
      b.credentialsProvider(DefaultCredentialsProvider.create());
    }
    return b.build();
  }
}
