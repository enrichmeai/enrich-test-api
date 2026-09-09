package org.deveasy.test.core.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.deveasy.test.core.cloud.CloudMode;
import org.deveasy.test.core.cloud.CloudProvider;
import org.deveasy.test.core.cloud.capability.BlobStorage;
import org.deveasy.test.core.cloud.capability.NoSqlTable;
import org.deveasy.test.core.cloud.capability.PubSub;
import org.deveasy.test.core.cloud.capability.Queue;
import org.deveasy.test.core.junit.support.FakeCloudAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

/**
 * Stories 7.1 and 7.2: what {@code CloudExtension.afterAll} does to the resources a test class
 * created, and what it does when releasing one of them fails.
 *
 * <p>Each fixture below is a static nested test class that neither Surefire nor Jupiter discovers
 * on its own; it runs only when a test here launches it through the JUnit Platform, which is the
 * only way to have {@code afterAll} complete before the assertions run.
 */
class CloudExtensionCleanupTest {

  private static final Logger CLEANUP_LOG = Logger.getLogger(CloudExtension.class.getName());

  private final List<LogRecord> records = new CopyOnWriteArrayList<>();
  private final Handler capture =
      new Handler() {
        @Override
        public void publish(LogRecord record) {
          records.add(record);
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}
      };

  @BeforeEach
  void freshFakesAndLogCapture() {
    FakeCloudAdapter.reset();
    CLEANUP_LOG.addHandler(capture);
  }

  @AfterEach
  void stopLogCapture() {
    CLEANUP_LOG.removeHandler(capture);
  }

  // ---- Story 7.1 -------------------------------------------------------------------------------

  @Test
  void topicsAndTables_areReleasedAfterTheClass() {
    TestExecutionSummary summary = launch(CreatesTopicAndTable.class);
    assertRanClean(summary);

    assertFalse(FakeCloudAdapter.pubSubFake().topics().contains("topic-a"));
    assertFalse(FakeCloudAdapter.noSqlTableFake().tables().contains("table-a"));
    assertEquals(List.of("topic-a"), FakeCloudAdapter.pubSubFake().deleteAttempts());
    assertEquals(List.of("table-a"), FakeCloudAdapter.noSqlTableFake().deleteAttempts());
  }

  @Test
  void deletingInsideTheTest_untracks_soAfterAllDoesNotDeleteTwice() {
    TestExecutionSummary summary = launch(DeletesInsideTheTest.class);
    assertRanClean(summary);

    assertEquals(List.of("topic-d"), FakeCloudAdapter.pubSubFake().deleteAttempts());
    assertEquals(List.of("table-d"), FakeCloudAdapter.noSqlTableFake().deleteAttempts());
  }

  @Test
  void rejectedEnsureTable_isNotEnrolledForDeletion() {
    FakeCloudAdapter.noSqlTableFake().ensureTable("foreign", "someoneElsesKey");
    FakeCloudAdapter.noSqlTableFake().rejectEnsureOf("foreign");

    TestExecutionSummary summary = launch(AsksForAForeignTable.class);
    assertRanClean(summary);

    assertTrue(FakeCloudAdapter.noSqlTableFake().tables().contains("foreign"));
    assertEquals(List.of(), FakeCloudAdapter.noSqlTableFake().deleteAttempts());
  }

  @Test
  void aTableCreatedByOneClass_isAbsentWhenTheNextClassRuns() {
    TestExecutionSummary first = launch(ClassA.class);
    assertRanClean(first);
    assertFalse(FakeCloudAdapter.noSqlTableFake().tables().contains("orders"));

    TestExecutionSummary second = launch(ClassB.class);
    assertRanClean(second);
    assertFalse(FakeCloudAdapter.noSqlTableFake().tables().contains("orders"));
  }

  // ---- Story 7.2 -------------------------------------------------------------------------------

  @Test
  void aFailedRelease_isLoggedWithNameAndCause_andDoesNotStopTheRest() {
    FakeCloudAdapter.blobStorageFake().failDeleteOf("b-fails");

    TestExecutionSummary summary = launch(CreatesOneOfEverything.class);
    assertRanClean(summary);

    // Only the failing bucket is left; the later bucket and every later kind were still released.
    assertEquals(Set.of("b-fails"), FakeCloudAdapter.blobStorageFake().buckets());
    assertEquals(List.of("b-fails", "b-ok"), FakeCloudAdapter.blobStorageFake().deleteAttempts());
    assertEquals(Set.of(), FakeCloudAdapter.queueFake().queues());
    assertEquals(Set.of(), FakeCloudAdapter.pubSubFake().topics());
    assertEquals(Set.of(), FakeCloudAdapter.noSqlTableFake().tables());

    List<LogRecord> warnings = warnings();
    assertEquals(1, warnings.size(), "exactly one release failed");
    LogRecord record = warnings.get(0);
    assertTrue(record.getMessage().contains("bucket"), record.getMessage());
    assertTrue(record.getMessage().contains("b-fails"), record.getMessage());
    assertTrue(record.getThrown() instanceof IllegalStateException);
    assertTrue(record.getThrown().getMessage().contains("simulated failure deleting bucket"));
  }

  @Test
  void aFailedRelease_doesNotChangeTheTestResult() {
    FakeCloudAdapter.noSqlTableFake().failDeleteOf("tbl-fails");

    TestExecutionSummary summary = launch(CreatesATableThatWillNotDelete.class);
    assertRanClean(summary);
    assertEquals(1, warnings().size());
  }

  @Test
  void aTestThatFailsOnItsOwn_reportsExactlyOneFailure_andIsStillCleanedUp() {
    TestExecutionSummary summary = launch(FailsOnItsOwn.class);
    assertEquals(1, summary.getTestsFoundCount());
    assertEquals(1, summary.getTestsFailedCount());
    assertEquals(1, summary.getTotalFailureCount());
    assertEquals(Set.of(), FakeCloudAdapter.blobStorageFake().buckets());
    assertEquals(0, warnings().size());
  }

  @Test
  void aCleanRelease_logsNothing() {
    TestExecutionSummary summary = launch(CreatesTopicAndTable.class);
    assertRanClean(summary);
    assertEquals(0, warnings().size());
  }

  // ---- harness ---------------------------------------------------------------------------------

  private static TestExecutionSummary launch(Class<?> fixture) {
    LauncherDiscoveryRequest request =
        LauncherDiscoveryRequestBuilder.request().selectors(selectClass(fixture)).build();
    SummaryGeneratingListener listener = new SummaryGeneratingListener();
    LauncherFactory.create().execute(request, listener);
    return listener.getSummary();
  }

  private static void assertRanClean(TestExecutionSummary summary) {
    assertEquals(1, summary.getTestsFoundCount(), "the fixture's one test must have been found");
    assertEquals(1, summary.getTestsSucceededCount());
    assertEquals(0, summary.getTotalFailureCount());
  }

  private List<LogRecord> warnings() {
    return records.stream().filter(r -> r.getLevel() == Level.WARNING).toList();
  }

  // ---- fixtures --------------------------------------------------------------------------------

  @WithCloud(provider = CloudProvider.AWS, mode = CloudMode.EMULATOR)
  static class CreatesTopicAndTable {
    @Test
    void createsResources(PubSub pubsub, NoSqlTable table) {
      pubsub.ensureTopic("topic-a");
      table.ensureTable("table-a", "id");
      assertTrue(FakeCloudAdapter.pubSubFake().topics().contains("topic-a"));
      assertTrue(FakeCloudAdapter.noSqlTableFake().tables().contains("table-a"));
    }
  }

  @WithCloud(provider = CloudProvider.AWS, mode = CloudMode.EMULATOR)
  static class DeletesInsideTheTest {
    @Test
    void createsAndDeletes(PubSub pubsub, NoSqlTable table) {
      pubsub.ensureTopic("topic-d");
      pubsub.deleteTopic("topic-d");
      table.ensureTable("table-d", "id");
      table.deleteTable("table-d");
      assertFalse(FakeCloudAdapter.pubSubFake().topics().contains("topic-d"));
      assertFalse(FakeCloudAdapter.noSqlTableFake().tables().contains("table-d"));
    }
  }

  @WithCloud(provider = CloudProvider.AWS, mode = CloudMode.EMULATOR)
  static class AsksForAForeignTable {
    @Test
    void ensureTableIsRejected(NoSqlTable table) {
      assertThrows(IllegalStateException.class, () -> table.ensureTable("foreign", "id"));
    }
  }

  @WithCloud(provider = CloudProvider.AWS, mode = CloudMode.EMULATOR)
  static class ClassA {
    @Test
    void createsOrdersKeyedOnId(NoSqlTable table) {
      table.ensureTable("orders", "id");
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", "a");
      table.putItem("orders", item);
      assertEquals("id", FakeCloudAdapter.noSqlTableFake().partitionKeyOf("orders"));
    }
  }

  @WithCloud(provider = CloudProvider.AWS, mode = CloudMode.EMULATOR)
  static class ClassB {
    @Test
    void createsOrdersKeyedOnOrderId(NoSqlTable table) {
      assertFalse(
          FakeCloudAdapter.noSqlTableFake().tables().contains("orders"),
          "class A's table must be gone before class B runs");
      table.ensureTable("orders", "orderId");
      assertEquals("orderId", FakeCloudAdapter.noSqlTableFake().partitionKeyOf("orders"));
    }
  }

  @WithCloud(provider = CloudProvider.AWS, mode = CloudMode.EMULATOR)
  static class CreatesOneOfEverything {
    @Test
    void createsResources(BlobStorage storage, Queue queue, PubSub pubsub, NoSqlTable table) {
      storage.ensureBucket("b-fails");
      storage.ensureBucket("b-ok");
      queue.ensureQueue("q-ok");
      pubsub.ensureTopic("t-ok");
      table.ensureTable("tbl-ok", "id");
      assertEquals(2, FakeCloudAdapter.blobStorageFake().buckets().size());
    }
  }

  @WithCloud(provider = CloudProvider.AWS, mode = CloudMode.EMULATOR)
  static class CreatesATableThatWillNotDelete {
    @Test
    void createsTable(NoSqlTable table) {
      table.ensureTable("tbl-fails", "id");
      assertTrue(FakeCloudAdapter.noSqlTableFake().tables().contains("tbl-fails"));
    }
  }

  @WithCloud(provider = CloudProvider.AWS, mode = CloudMode.EMULATOR)
  static class FailsOnItsOwn {
    @Test
    void createsBucketThenFails(BlobStorage storage) {
      storage.ensureBucket("b-x");
      fail("this test fails for its own reasons");
    }
  }
}
