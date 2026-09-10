/*
 * AWS S3 implementation of BlobStorage capability (minimal v0.3).
 */
package com.enrichmeai.test.cloud.aws;

import com.enrichmeai.test.cloud.aws.internal.AwsClients;
import com.enrichmeai.test.cloud.aws.internal.LocalStackHolder;
import com.enrichmeai.test.core.cloud.CloudMode;
import com.enrichmeai.test.core.cloud.TestCloudConfig;
import com.enrichmeai.test.core.cloud.capability.BlobStorage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketLocationConstraint;
import software.amazon.awssdk.services.s3.model.CreateBucketConfiguration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

public final class AwsBlobStorage implements BlobStorage {

  private final TestCloudConfig cfg;
  private final S3Client s3;

  public AwsBlobStorage(TestCloudConfig cfg) {
    this.cfg = cfg;
    // Ensure emulator up-front if in EMULATOR mode to improve error messages
    if (cfg.mode() == CloudMode.EMULATOR) {
      LocalStackHolder.ensureStartedS3();
    }
    this.s3 = AwsClients.s3(cfg);
  }

  @Override
  public void ensureBucket(String name) {
    try {
      HeadBucketRequest head = HeadBucketRequest.builder().bucket(name).build();
      s3.headBucket(head);
      return;
    } catch (S3Exception e) {
      if (e.statusCode() != 404) {
        throw e;
      }
    }
    String region = cfg.regionOrLocation();
    if (region == null || region.isBlank()) {
      region = "us-east-1";
    }
    CreateBucketRequest.Builder builder = CreateBucketRequest.builder().bucket(name);
    if (!"us-east-1".equals(region)) {
      builder.createBucketConfiguration(
          CreateBucketConfiguration.builder()
              .locationConstraint(BucketLocationConstraint.fromValue(region))
              .build());
    }
    s3.createBucket(builder.build());
  }

  @Override
  public void deleteBucket(String name) {
    // Best-effort: delete all objects then the bucket
    ListObjectsV2Request listReq = ListObjectsV2Request.builder().bucket(name).build();
    ListObjectsV2Response list;
    try {
      do {
        list = s3.listObjectsV2(listReq);
        if (list.hasContents()) {
          for (S3Object obj : list.contents()) {
            deleteObject(name, obj.key());
          }
        }
        listReq = listReq.toBuilder().continuationToken(list.nextContinuationToken()).build();
      } while (list.isTruncated());
    } catch (S3Exception e) {
      // ignore if bucket doesn't exist
      if (e.statusCode() != 404) throw e;
    }
    try {
      s3.deleteBucket(DeleteBucketRequest.builder().bucket(name).build());
    } catch (S3Exception e) {
      if (e.statusCode() != 404) throw e;
    }
  }

  @Override
  public void putObject(String bucket, String key, byte[] data, String contentType) {
    PutObjectRequest req =
        PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build();
    s3.putObject(req, RequestBody.fromBytes(data));
  }

  @Override
  public void putObject(String bucket, String key, InputStream data, String contentType) {
    try {
      byte[] bytes = data.readAllBytes();
      putObject(bucket, key, bytes, contentType);
    } catch (IOException e) {
      throw new RuntimeException("Failed to read input stream for putObject", e);
    }
  }

  @Override
  public byte[] getObject(String bucket, String key) {
    try {
      GetObjectRequest req = GetObjectRequest.builder().bucket(bucket).key(key).build();
      ResponseBytes<GetObjectResponse> resp = s3.getObjectAsBytes(req);
      return resp.asByteArray();
    } catch (S3Exception e) {
      // return null when not found as per interface contract
      String code = e.awsErrorDetails() != null ? e.awsErrorDetails().errorCode() : null;
      if (e.statusCode() == 404 || "NoSuchKey".equals(code)) {
        return null;
      }
      throw e;
    }
  }

  @Override
  public void deleteObject(String bucket, String key) {
    try {
      s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    } catch (S3Exception e) {
      if (e.statusCode() != 404) throw e;
    }
  }

  @Override
  public List<String> listKeys(String bucket, String prefix) {
    String p = (prefix == null) ? "" : prefix;
    List<String> keys = new ArrayList<>();
    ListObjectsV2Request req = ListObjectsV2Request.builder().bucket(bucket).prefix(p).build();
    ListObjectsV2Response res;
    do {
      res = s3.listObjectsV2(req);
      if (res.hasContents()) {
        for (S3Object obj : res.contents()) {
          keys.add(obj.key());
        }
      }
      req = req.toBuilder().continuationToken(res.nextContinuationToken()).build();
    } while (res.isTruncated());
    return keys;
  }

  @Override
  public boolean exists(String bucket, String key) {
    try {
      s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
      return true;
    } catch (S3Exception e) {
      if (e.statusCode() == 404) return false;
      throw e;
    }
  }

  // Convenience helpers (not part of interface) for examples
  public void putJson(String bucket, String key, String json) {
    putObject(bucket, key, json.getBytes(StandardCharsets.UTF_8), "application/json");
  }

  public String getString(String bucket, String key) {
    byte[] b = getObject(bucket, key);
    return b == null ? null : new String(b, StandardCharsets.UTF_8);
  }
}
