package com.enrichmeai.test.core.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.enrichmeai.test.core.cloud.CloudMode;
import com.enrichmeai.test.core.cloud.CloudProvider;
import com.enrichmeai.test.core.cloud.capability.NoSqlTable;
import com.enrichmeai.test.core.cloud.capability.PubSub;
import com.enrichmeai.test.core.junit.support.FakeCloudAdapter;
import com.enrichmeai.test.core.junit.support.FakeNoSqlTable;
import com.enrichmeai.test.core.junit.support.FakePubSub;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Story 7.1, AC-1 and AC-6: the injected {@code PubSub} and {@code NoSqlTable} are the extension's
 * tracking wrappers, and every method on them reaches the fake underneath. What happens to the
 * resources after the class is {@link CloudExtensionCleanupTest}'s job.
 */
@WithCloud(provider = CloudProvider.AWS, mode = CloudMode.EMULATOR)
class WithCloudTrackingTest {

  @Test
  void pubSub_isWrapped_andEveryMethodReachesTheFake(PubSub pubsub) {
    FakePubSub fake = FakeCloudAdapter.pubSubFake();
    assertFalse(pubsub instanceof FakePubSub, "the extension should inject its wrapper");

    pubsub.ensureTopic("tracking-topic");
    assertTrue(fake.topics().contains("tracking-topic"));

    pubsub.ensureSubscription("tracking-topic", "tracking-sub");
    assertTrue(fake.subscriptions("tracking-topic").contains("tracking-sub"));

    pubsub.publish("tracking-topic", "m1");
    pubsub.publish("tracking-topic", "m2");
    assertEquals(Optional.of("m1"), pubsub.receive("tracking-sub"));
    assertEquals(Optional.of("m2"), pubsub.receive("tracking-sub", Duration.ofMillis(10)));
    assertEquals(Optional.empty(), pubsub.receive("tracking-sub"));

    pubsub.deleteTopic("tracking-topic");
    assertFalse(fake.topics().contains("tracking-topic"));
  }

  @Test
  void noSqlTable_isWrapped_andEveryMethodReachesTheFake(NoSqlTable table) {
    FakeNoSqlTable fake = FakeCloudAdapter.noSqlTableFake();
    assertFalse(table instanceof FakeNoSqlTable, "the extension should inject its wrapper");

    table.ensureTable("tracking-orders", "id");
    assertEquals("id", fake.partitionKeyOf("tracking-orders"));
    assertNull(fake.sortKeyOf("tracking-orders"));

    table.ensureTable("tracking-events", "id", "ts");
    assertEquals("id", fake.partitionKeyOf("tracking-events"));
    assertEquals("ts", fake.sortKeyOf("tracking-events"));

    Map<String, Object> order = new LinkedHashMap<>();
    order.put("id", "o1");
    order.put("name", "widget");
    table.putItem("tracking-orders", order);
    assertEquals("widget", table.getItem("tracking-orders", "o1").get("name"));

    Map<String, Object> event = new LinkedHashMap<>();
    event.put("id", "e1");
    event.put("ts", "t1");
    table.putItem("tracking-events", event);
    assertNotNull(table.getItem("tracking-events", "e1", "t1"));

    List<Map<String, Object>> scanned = table.scan("tracking-orders");
    assertEquals(1, scanned.size());
    List<Map<String, Object>> queried = table.query("tracking-orders", "o1");
    assertEquals(1, queried.size());
    assertEquals("widget", queried.get(0).get("name"));

    table.deleteItem("tracking-orders", "o1");
    assertNull(table.getItem("tracking-orders", "o1"));
    table.deleteItem("tracking-events", "e1", "t1");
    assertNull(table.getItem("tracking-events", "e1", "t1"));

    table.deleteTable("tracking-events");
    assertFalse(fake.tables().contains("tracking-events"));
    // tracking-orders is left for afterAll; CloudExtensionCleanupTest asserts that path.
  }
}
