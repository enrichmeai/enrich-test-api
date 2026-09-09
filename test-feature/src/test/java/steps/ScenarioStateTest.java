package steps;

import static org.junit.jupiter.api.Assertions.*;

import com.enrichmeai.test.core.cloud.CloudMode;
import com.enrichmeai.test.core.cloud.CloudProvider;
import com.enrichmeai.test.core.cloud.TestCloudConfig;
import java.util.HashMap;
import org.junit.jupiter.api.Test;

public class ScenarioStateTest {

  @Test
  void gettersSettersAndResetWork() {
    ScenarioState s = new ScenarioState();
    s.setProvider(CloudProvider.AWS);
    s.setConfig(
        TestCloudConfig.builder()
            .provider(CloudProvider.AWS)
            .mode(CloudMode.EMULATOR)
            .regionOrLocation("eu-west-1")
            .build());
    s.setCurrentBucket("b1");
    s.setCurrentQueue("q1");
    s.setCurrentTopic("t1");
    s.setCurrentTable("table1");
    HashMap<String, Object> resp = new HashMap<>();
    resp.put("status", 200);
    s.setLastResponse(resp);

    assertEquals(CloudProvider.AWS, s.getProvider());
    assertNotNull(s.getConfig());
    assertEquals("b1", s.getCurrentBucket());
    assertEquals("q1", s.getCurrentQueue());
    assertEquals("t1", s.getCurrentTopic());
    assertEquals("table1", s.getCurrentTable());
    assertEquals(1, s.getLastResponse().size());

    s.reset();

    assertNull(s.getCurrentBucket());
    assertNull(s.getCurrentQueue());
    assertNull(s.getCurrentTopic());
    assertNull(s.getCurrentTable());
    assertEquals(0, s.getLastResponse().size());

    // provider and config are intentionally not reset here by design
    assertEquals(CloudProvider.AWS, s.getProvider());
    assertNotNull(s.getConfig());
  }
}
