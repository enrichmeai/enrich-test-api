package org.deveasy.test.feature.cloud;

import io.cucumber.java.en.Given;
import java.util.ServiceLoader;
import org.deveasy.test.core.cloud.CloudMode;
import org.deveasy.test.core.cloud.CloudProvider;
import org.deveasy.test.core.cloud.TestCloudConfig;
import org.deveasy.test.core.cloud.spi.CloudAdapter;

/**
 * Cucumber steps to select cloud provider/mode/region and initialize the adapter via ServiceLoader.
 */
public class CloudSelectionSteps {

  private static final TestContext CTX = new TestContext();

  @Given("cloud provider is {string}")
  public void cloudProviderIs(String p) {
    CTX.provider = CloudProvider.valueOf(p.toUpperCase());
  }

  @Given("cloud mode is {string}")
  public void cloudModeIs(String m) {
    CTX.mode = CloudMode.valueOf(m.toUpperCase());
  }

  @Given("cloud region is {string}")
  public void cloudRegionIs(String r) {
    CTX.region = r;
    // Lazily initialize adapter once all fields are known
    if (CTX.adapter == null) {
      TestCloudConfig cfg =
          TestCloudConfig.builder()
              .provider(CTX.provider)
              .mode(CTX.mode)
              .regionOrLocation(CTX.region)
              .build();
      CloudAdapter adapter =
          ServiceLoader.load(CloudAdapter.class).stream()
              .map(ServiceLoader.Provider::get)
              .filter(a -> a.provider() == CTX.provider)
              .findFirst()
              .orElseThrow(
                  () -> new IllegalStateException("No CloudAdapter found for " + CTX.provider));
      adapter.initialize(cfg);
      CTX.adapter = adapter;
    }
  }

  public static CloudAdapter adapter() {
    if (CTX.adapter == null) {
      throw new IllegalStateException(
          "Cloud adapter not initialized. Ensure selection steps ran (provider/mode/region).");
    }
    return CTX.adapter;
  }

  private static final class TestContext {
    CloudProvider provider = CloudProvider.AWS;
    CloudMode mode = CloudMode.EMULATOR;
    String region = "us-east-1";
    CloudAdapter adapter;
  }
}
