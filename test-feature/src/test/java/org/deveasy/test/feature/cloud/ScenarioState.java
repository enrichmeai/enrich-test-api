package org.deveasy.test.feature.cloud;

import java.util.HashMap;
import java.util.Map;

/** Simple thread-local scenario state for step glue to share data. */
public final class ScenarioState {
  private static final ThreadLocal<Map<String, Object>> TL = ThreadLocal.withInitial(HashMap::new);

  private ScenarioState() {}

  public static void put(String key, Object value) {
    TL.get().put(key, value);
  }

  @SuppressWarnings("unchecked")
  public static <T> T get(String key) {
    return (T) TL.get().get(key);
  }

  public static void clear() {
    TL.get().clear();
  }
}
