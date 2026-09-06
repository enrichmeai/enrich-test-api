package steps;

import java.util.HashMap;
import java.util.Map;
import org.deveasy.test.core.cloud.CloudProvider;
import org.deveasy.test.core.cloud.TestCloudConfig;

/** Simple per-scenario shared state holder for Cucumber step definitions. */
public class ScenarioState {
  private CloudProvider provider;
  private TestCloudConfig config;
  private String currentBucket;
  private String currentQueue;
  private String currentTopic;
  private String currentTable;
  private Map<String, Object> lastResponse = new HashMap<>();

  public CloudProvider getProvider() {
    return provider;
  }

  public void setProvider(CloudProvider provider) {
    this.provider = provider;
  }

  public TestCloudConfig getConfig() {
    return config;
  }

  public void setConfig(TestCloudConfig config) {
    this.config = config;
  }

  public String getCurrentBucket() {
    return currentBucket;
  }

  public void setCurrentBucket(String currentBucket) {
    this.currentBucket = currentBucket;
  }

  public String getCurrentQueue() {
    return currentQueue;
  }

  public void setCurrentQueue(String currentQueue) {
    this.currentQueue = currentQueue;
  }

  public String getCurrentTopic() {
    return currentTopic;
  }

  public void setCurrentTopic(String currentTopic) {
    this.currentTopic = currentTopic;
  }

  public String getCurrentTable() {
    return currentTable;
  }

  public void setCurrentTable(String currentTable) {
    this.currentTable = currentTable;
  }

  public Map<String, Object> getLastResponse() {
    return lastResponse;
  }

  public void setLastResponse(Map<String, Object> lastResponse) {
    this.lastResponse = lastResponse;
  }

  public void reset() {
    currentBucket = null;
    currentQueue = null;
    currentTopic = null;
    currentTable = null;
    lastResponse.clear();
  }
}
