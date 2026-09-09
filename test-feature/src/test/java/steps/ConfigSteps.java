package steps;

import com.enrichmeai.test.core.cloud.CloudMode;
import com.enrichmeai.test.core.cloud.CloudProvider;
import com.enrichmeai.test.core.cloud.TestCloudConfig;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;

/** Background/setup steps for selecting cloud provider/mode/region. */
public class ConfigSteps {

  private final ScenarioState state = new ScenarioState();

  // Expose for unit tests
  ScenarioState getState() {
    return state;
  }

  @Before
  public void resetState() {
    state.reset();
  }

  @Given("cloud provider is {string}")
  public void cloudProviderIs(String providerName) {
    state.setProvider(CloudProvider.valueOf(providerName.toUpperCase()));
  }

  @Given("cloud mode is {string}")
  public void cloudModeIs(String modeName) {
    CloudMode mode = CloudMode.valueOf(modeName.toUpperCase());
    String region = "eu-west-1"; // default
    TestCloudConfig config =
        TestCloudConfig.builder()
            .provider(state.getProvider())
            .mode(mode)
            .regionOrLocation(region)
            .build();
    state.setConfig(config);
  }

  @Given("cloud region is {string}")
  public void cloudRegionIs(String region) {
    // Update config with new region
    TestCloudConfig existing = state.getConfig();
    TestCloudConfig config =
        TestCloudConfig.builder()
            .provider(state.getProvider())
            .mode(existing.mode())
            .regionOrLocation(region)
            .build();
    state.setConfig(config);
  }
}
