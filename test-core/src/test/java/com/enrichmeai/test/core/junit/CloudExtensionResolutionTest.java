package com.enrichmeai.test.core.junit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import com.enrichmeai.test.core.cloud.CloudMode;
import com.enrichmeai.test.core.cloud.CloudProvider;
import com.enrichmeai.test.core.cloud.capability.BlobStorage;
import com.enrichmeai.test.core.cloud.capability.NoSqlTable;
import com.enrichmeai.test.core.cloud.capability.PubSub;
import com.enrichmeai.test.core.cloud.capability.Queue;
import com.enrichmeai.test.core.junit.support.FakeCloudAdapter;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

/**
 * Story 1.1: how {@code CloudExtension} configures itself and resolves parameters, including the
 * paths a user only meets when something is missing: no {@code @WithCloud}, an adapter that
 * supports no capability, and a parameter type the extension does not provide.
 *
 * <p>The fixtures are launched through the JUnit Platform, as in {@link CloudExtensionCleanupTest},
 * so each failure is observed the way a user's build would report it.
 */
class CloudExtensionResolutionTest {

  @BeforeEach
  void freshFakes() {
    FakeCloudAdapter.reset();
  }

  @AfterEach
  void capabilitiesBackOn() {
    // withoutCapabilities() is static: leave the shared adapter as other test classes expect it.
    FakeCloudAdapter.reset();
  }

  @Test
  void aClassWithoutWithCloud_failsAsAContainer_withAConfigurationError() {
    TestExecutionSummary summary = launch(ExtendedWithoutWithCloud.class);

    assertEquals(1, summary.getContainersFailedCount());
    assertEquals(0, summary.getTestsSucceededCount());
    Throwable cause = onlyFailure(summary);
    assertInstanceOf(ExtensionConfigurationException.class, cause);
    assertTrue(cause.getMessage().contains("@WithCloud must be present"), cause.getMessage());
  }

  @Test
  void anAdapterWithoutCapabilities_failsEachParameter_namingTheCapability() {
    FakeCloudAdapter.withoutCapabilities();

    TestExecutionSummary summary = launch(AsksForEachCapability.class);

    assertEquals(4, summary.getTestsFoundCount());
    assertEquals(4, summary.getTestsFailedCount());
    // afterAll has nothing to release and must not fail the class.
    assertEquals(0, summary.getContainersFailedCount());
    for (TestExecutionSummary.Failure f : summary.getFailures()) {
      assertInstanceOf(ParameterResolutionException.class, f.getException());
    }
    Set<String> messages =
        summary.getFailures().stream()
            .map(f -> f.getException().getMessage())
            .collect(Collectors.toSet());
    assertEquals(
        Set.of(
            "BlobStorage capability not available for provider",
            "Queue capability not available for provider",
            "PubSub capability not available for provider",
            "NoSqlTable capability not available for provider"),
        messages);
  }

  /**
   * Story 1.1, AC-1. JUnit never reaches this branch itself: {@code supportsParameter} answers
   * false for a type the extension does not provide, so JUnit reports its own error first. The
   * extension is therefore driven directly, with a {@code String} parameter and an empty store.
   */
  @Test
  void resolvingATypeTheExtensionDoesNotProvide_failsNamingTheType() throws Exception {
    CloudExtension extension = new CloudExtension();
    ParameterContext parameter = parameterContext(Fixtures.class, "takesAString", String.class);
    ExtensionContext context = emptyContext();

    assertFalse(extension.supportsParameter(parameter, context));
    ParameterResolutionException e =
        assertThrows(
            ParameterResolutionException.class,
            () -> extension.resolveParameter(parameter, context));
    assertTrue(e.getMessage().contains("java.lang.String"), e.getMessage());
  }

  @Test
  void theWrappersPassEveryOtherCallThroughToTheAdapter() {
    TestExecutionSummary summary = launch(UsesTheWholeSurface.class);

    assertEquals(1, summary.getTestsSucceededCount());
    assertEquals(0, summary.getTotalFailureCount(), () -> describe(summary));
  }

  // ---- harness ---------------------------------------------------------------------------------

  private static TestExecutionSummary launch(Class<?> fixture) {
    LauncherDiscoveryRequest request =
        LauncherDiscoveryRequestBuilder.request().selectors(selectClass(fixture)).build();
    SummaryGeneratingListener listener = new SummaryGeneratingListener();
    LauncherFactory.create().execute(request, listener);
    return listener.getSummary();
  }

  private static Throwable onlyFailure(TestExecutionSummary summary) {
    assertEquals(1, summary.getFailures().size(), () -> describe(summary));
    return summary.getFailures().get(0).getException();
  }

  private static String describe(TestExecutionSummary summary) {
    return summary.getFailures().stream()
        .map(f -> f.getTestIdentifier().getDisplayName() + ": " + f.getException())
        .collect(Collectors.joining("; "));
  }

  /** Methods whose parameters stand in for a test method's, for driving the extension directly. */
  static class Fixtures {
    void takesAString(String notACapability) {}
  }

  private static ParameterContext parameterContext(Class<?> owner, String method, Class<?> type)
      throws NoSuchMethodException {
    Parameter p = owner.getDeclaredMethod(method, type).getParameters()[0];
    return (ParameterContext)
        Proxy.newProxyInstance(
            CloudExtensionResolutionTest.class.getClassLoader(),
            new Class<?>[] {ParameterContext.class},
            (proxy, m, args) -> {
              if (m.getName().equals("getParameter")) return p;
              if (m.getName().equals("getIndex")) return 0;
              throw new UnsupportedOperationException(m.getName());
            });
  }

  /** An extension context whose store holds nothing, as before {@code beforeAll} has run. */
  private static ExtensionContext emptyContext() {
    ExtensionContext.Store store =
        (ExtensionContext.Store)
            Proxy.newProxyInstance(
                CloudExtensionResolutionTest.class.getClassLoader(),
                new Class<?>[] {ExtensionContext.Store.class},
                (proxy, m, args) -> {
                  if (m.getName().equals("get")) return null;
                  throw new UnsupportedOperationException(m.getName());
                });
    return (ExtensionContext)
        Proxy.newProxyInstance(
            CloudExtensionResolutionTest.class.getClassLoader(),
            new Class<?>[] {ExtensionContext.class},
            (proxy, m, args) -> {
              if (m.getName().equals("getStore")) return store;
              throw new UnsupportedOperationException(m.getName());
            });
  }

  // ---- fixtures --------------------------------------------------------------------------------

  @ExtendWith(CloudExtension.class)
  static class ExtendedWithoutWithCloud {
    @Test
    void neverRuns() {}
  }

  @WithCloud(provider = CloudProvider.AWS, mode = CloudMode.EMULATOR)
  static class AsksForEachCapability {
    @Test
    void storage(BlobStorage storage) {}

    @Test
    void queue(Queue queue) {}

    @Test
    void pubsub(PubSub pubsub) {}

    @Test
    void table(NoSqlTable table) {}
  }

  @WithCloud(provider = CloudProvider.AWS, mode = CloudMode.EMULATOR)
  static class UsesTheWholeSurface {
    @Test
    void storageAndQueue(BlobStorage storage, Queue queue) {
      byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
      storage.ensureBucket("b");
      storage.putObject("b", "k/1", new ByteArrayInputStream(body), "text/plain");
      assertTrue(storage.exists("b", "k/1"));
      assertEquals(List.of("k/1"), storage.listKeys("b", "k/"));
      assertArrayEquals(body, storage.getObject("b", "k/1"));
      storage.deleteObject("b", "k/1");
      assertFalse(storage.exists("b", "k/1"));

      queue.ensureQueue("q");
      queue.send("q", "one");
      queue.send("q", "two");
      assertEquals(Optional.of("one"), queue.receive("q"));
      assertEquals(Optional.of("two"), queue.receive("q", Duration.ofMillis(10)));
      assertEquals(Optional.empty(), queue.receive("q"));
    }
  }
}
