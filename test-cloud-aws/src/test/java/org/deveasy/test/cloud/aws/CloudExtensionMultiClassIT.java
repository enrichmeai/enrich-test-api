/*
 * Copyright 2025
 * Apache License, Version 2.0
 */
package org.deveasy.test.cloud.aws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.deveasy.test.cloud.aws.internal.AwsClients;
import org.deveasy.test.core.cloud.CloudMode;
import org.deveasy.test.core.cloud.CloudProvider;
import org.deveasy.test.core.cloud.TestCloudConfig;
import org.deveasy.test.core.cloud.capability.NoSqlTable;
import org.deveasy.test.core.cloud.capability.PubSub;
import org.deveasy.test.core.junit.WithCloud;
import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.Topic;

/**
 * Epic 7 end to end, against LocalStack: two test classes in one JVM, sharing one emulator. Class A
 * creates a table keyed on {@code id} and a topic; class B asks for the same table name keyed on
 * {@code orderId}. Before Epic 7 class B silently received A's table and died in {@code putItem}
 * with a raw {@code ValidationException}. Now A's resources are gone by the time B runs, and B gets
 * the table it asked for.
 *
 * <p>The two classes are static nested fixtures that Failsafe does not select on its own; they run
 * only through the launcher below, in order.
 */
class CloudExtensionMultiClassIT {

  private static final String REGION = "eu-west-1";
  private static final String SUFFIX =
      UUID.randomUUID().toString().replace("-", "").substring(0, 8);
  private static final String TABLE = "dev-easy-multiclass-orders-" + SUFFIX;
  private static final String TOPIC = "dev-easy-multiclass-topic-" + SUFFIX;

  @Test
  void resourcesFromClassA_areGoneWhenClassBRuns_andClassBGetsItsOwnSchema() {
    TestExecutionSummary a = launch(ClassA.class);
    assertEquals(1, a.getTestsSucceededCount(), failures(a));
    assertEquals(0, a.getTotalFailureCount(), failures(a));

    assertThrows(
        ResourceNotFoundException.class,
        () -> describe(TABLE),
        "class A's table must be gone after its afterAll");
    assertFalse(topicExists(TOPIC), "class A's topic must be gone after its afterAll");

    TestExecutionSummary b = launch(ClassB.class);
    assertEquals(1, b.getTestsSucceededCount(), failures(b));
    assertEquals(0, b.getTotalFailureCount(), failures(b));

    assertThrows(
        ResourceNotFoundException.class,
        () -> describe(TABLE),
        "class B's table must be gone after its afterAll");
  }

  // ---- fixtures --------------------------------------------------------------------------------

  @WithCloud(provider = CloudProvider.AWS, mode = CloudMode.EMULATOR, region = REGION)
  static class ClassA {
    @Test
    void createsOrdersKeyedOnId(NoSqlTable table, PubSub pubsub) {
      table.ensureTable(TABLE, "id");
      pubsub.ensureTopic(TOPIC);
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", "a");
      table.putItem(TABLE, item);
      assertNotNull(table.getItem(TABLE, "a"));
      assertEquals("id", hashKeyOf(TABLE));
    }
  }

  @WithCloud(provider = CloudProvider.AWS, mode = CloudMode.EMULATOR, region = REGION)
  static class ClassB {
    @Test
    void createsOrdersKeyedOnOrderId(NoSqlTable table) {
      table.ensureTable(TABLE, "orderId");
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("orderId", "b");
      table.putItem(TABLE, item);
      assertNotNull(table.getItem(TABLE, "b"));
      assertEquals("orderId", hashKeyOf(TABLE));
    }
  }

  // ---- harness ---------------------------------------------------------------------------------

  private static TestExecutionSummary launch(Class<?> fixture) {
    LauncherDiscoveryRequest request =
        LauncherDiscoveryRequestBuilder.request().selectors(selectClass(fixture)).build();
    SummaryGeneratingListener listener = new SummaryGeneratingListener();
    LauncherFactory.create().execute(request, listener);
    return listener.getSummary();
  }

  private static String failures(TestExecutionSummary summary) {
    StringBuilder sb = new StringBuilder();
    for (TestExecutionSummary.Failure f : summary.getFailures()) {
      sb.append(f.getTestIdentifier().getDisplayName())
          .append(": ")
          .append(f.getException())
          .append('\n');
    }
    return sb.toString();
  }

  private static TestCloudConfig config() {
    return TestCloudConfig.builder()
        .provider(CloudProvider.AWS)
        .mode(CloudMode.EMULATOR)
        .regionOrLocation(REGION)
        .build();
  }

  private static software.amazon.awssdk.services.dynamodb.model.TableDescription describe(
      String tableName) {
    DynamoDbClient ddb = AwsClients.dynamodb(config());
    return ddb.describeTable(DescribeTableRequest.builder().tableName(tableName).build()).table();
  }

  private static String hashKeyOf(String tableName) {
    for (KeySchemaElement e : describe(tableName).keySchema()) {
      if (e.keyType() == KeyType.HASH) return e.attributeName();
    }
    return null;
  }

  private static boolean topicExists(String name) {
    SnsClient sns = AwsClients.sns(config());
    for (Topic t : sns.listTopics().topics()) {
      if (t.topicArn() != null && t.topicArn().endsWith(":" + name)) return true;
    }
    return false;
  }
}
