package steps;

import static org.junit.jupiter.api.Assertions.*;

import com.enrichmeai.test.core.cloud.CloudMode;
import com.enrichmeai.test.core.cloud.CloudProvider;
import com.enrichmeai.test.core.cloud.TestCloudConfig;
import org.junit.jupiter.api.Test;

public class ConfigStepsTest {

  @Test
  void providerSelectionAndConfigDefaultsWork() {
    ConfigSteps steps = new ConfigSteps();

    steps.cloudProviderIs("aws");
    assertEquals(CloudProvider.AWS, steps.getState().getProvider());

    steps.cloudModeIs("emulator");
    TestCloudConfig cfg = steps.getState().getConfig();
    assertNotNull(cfg);
    assertEquals(CloudProvider.AWS, cfg.provider());
    assertEquals(CloudMode.EMULATOR, cfg.mode());
    assertEquals("eu-west-1", cfg.regionOrLocation());

    // Now override region and ensure provider/mode are preserved
    steps.cloudRegionIs("us-east-1");
    TestCloudConfig cfg2 = steps.getState().getConfig();
    assertEquals(CloudProvider.AWS, cfg2.provider());
    assertEquals(CloudMode.EMULATOR, cfg2.mode());
    assertEquals("us-east-1", cfg2.regionOrLocation());
  }

  @Test
  void resetClearsTransientFieldsOnly() {
    ConfigSteps steps = new ConfigSteps();
    steps.cloudProviderIs("aws");
    steps.cloudModeIs("emulator");

    ScenarioState s = steps.getState();
    s.setCurrentBucket("b");
    s.setCurrentQueue("q");
    s.setCurrentTopic("t");
    s.setCurrentTable("tbl");
    s.getLastResponse().put("ok", true);

    // simulate Cucumber @Before
    steps.resetState();

    assertNull(s.getCurrentBucket());
    assertNull(s.getCurrentQueue());
    assertNull(s.getCurrentTopic());
    assertNull(s.getCurrentTable());
    assertTrue(s.getLastResponse().isEmpty());
    // provider/config remain
    assertEquals(CloudProvider.AWS, s.getProvider());
    assertNotNull(s.getConfig());
  }
}
