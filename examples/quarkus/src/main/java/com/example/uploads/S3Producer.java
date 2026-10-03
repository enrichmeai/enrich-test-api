package com.example.uploads;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import java.net.URI;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

@ApplicationScoped
class S3Producer {

  @Produces
  @ApplicationScoped
  S3Client s3(StorageConfig config) {
    S3ClientBuilder b =
        S3Client.builder()
            .httpClientBuilder(UrlConnectionHttpClient.builder())
            .region(Region.of(config.region()));
    config.endpoint().ifPresent(e -> b.endpointOverride(URI.create(e)).forcePathStyle(true));
    if (config.accessKey().isPresent()) {
      b.credentialsProvider(
          StaticCredentialsProvider.create(
              AwsBasicCredentials.create(
                  config.accessKey().get(), config.secretKey().orElseThrow())));
    } else {
      b.credentialsProvider(DefaultCredentialsProvider.create());
    }
    return b.build();
  }

  void close(@Disposes S3Client s3) {
    s3.close();
  }
}
