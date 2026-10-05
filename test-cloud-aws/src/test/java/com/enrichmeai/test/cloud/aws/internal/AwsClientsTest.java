package com.enrichmeai.test.cloud.aws.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.enrichmeai.test.core.cloud.CloudMode;
import com.enrichmeai.test.core.cloud.CloudProvider;
import com.enrichmeai.test.core.cloud.TestCloudConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Story 1.3, AC-1 and AC-2: the LIVE-mode branch of each client factory, and region defaulting. No
 * Docker and no AWS account: building a client resolves neither credentials nor the network, so
 * nothing here makes a call. Each client is closed unused.
 */
class AwsClientsTest {

  private static TestCloudConfig live(String region) {
    return TestCloudConfig.builder()
        .provider(CloudProvider.AWS)
        .mode(CloudMode.LIVE)
        .regionOrLocation(region)
        .build();
  }

  @Test
  void liveMode_buildsEachClient_inTheConfiguredRegion_withNoEndpointOverride() {
    TestCloudConfig cfg = live("eu-west-2");
    try (S3Client s3 = AwsClients.s3(cfg);
        SqsClient sqs = AwsClients.sqs(cfg);
        SnsClient sns = AwsClients.sns(cfg);
        DynamoDbClient ddb = AwsClients.dynamodb(cfg)) {
      assertEquals(Region.EU_WEST_2, s3.serviceClientConfiguration().region());
      assertEquals(Region.EU_WEST_2, sqs.serviceClientConfiguration().region());
      assertEquals(Region.EU_WEST_2, sns.serviceClientConfiguration().region());
      assertEquals(Region.EU_WEST_2, ddb.serviceClientConfiguration().region());
      assertTrue(s3.serviceClientConfiguration().endpointOverride().isEmpty());
      assertTrue(ddb.serviceClientConfiguration().endpointOverride().isEmpty());
    }
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   "})
  void aMissingRegion_defaultsToUsEast1(String region) {
    TestCloudConfig cfg = live(region);
    assertEquals("us-east-1", AwsClients.region(cfg));
    try (S3Client s3 = AwsClients.s3(cfg)) {
      assertEquals(Region.US_EAST_1, s3.serviceClientConfiguration().region());
    }
  }

  @Test
  void aSuppliedRegion_isUsedAsIs() {
    assertEquals("ap-southeast-2", AwsClients.region(live("ap-southeast-2")));
  }
}
