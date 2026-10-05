package com.enrichmeai.test.core.cloud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Story 1.6: {@link TestCloudConfig} reads back what was built, and cannot be changed after. */
class TestCloudConfigTest {

  @Test
  void defaults_areAwsInEmulatorMode_withNothingElseSet() {
    TestCloudConfig c = TestCloudConfig.builder().build();
    assertEquals(CloudProvider.AWS, c.provider());
    assertEquals(CloudMode.EMULATOR, c.mode());
    assertNull(c.regionOrLocation());
    assertNull(c.projectOrAccount());
    assertEquals(Map.of(), c.overrides());
  }

  @Test
  void everyField_readsBackFromTheBuiltValue() {
    TestCloudConfig c =
        TestCloudConfig.builder()
            .provider(CloudProvider.GCP)
            .mode(CloudMode.LIVE)
            .regionOrLocation("europe-west2")
            .projectOrAccount("my-project")
            .override("endpoint.storage", "http://localhost:4443")
            .build();
    assertEquals(CloudProvider.GCP, c.provider());
    assertEquals(CloudMode.LIVE, c.mode());
    assertEquals("europe-west2", c.regionOrLocation());
    assertEquals("my-project", c.projectOrAccount());
    assertEquals(Map.of("endpoint.storage", "http://localhost:4443"), c.overrides());
    assertEquals("http://localhost:4443", c.override("endpoint.storage"));
    assertNull(c.override("absent"));
  }

  @Test
  void providerAndMode_areRequired() {
    assertEquals(
        "provider",
        assertThrows(
                NullPointerException.class, () -> TestCloudConfig.builder().provider(null).build())
            .getMessage());
    assertEquals(
        "mode",
        assertThrows(NullPointerException.class, () -> TestCloudConfig.builder().mode(null).build())
            .getMessage());
  }

  @Test
  void aBuiltConfig_isNotChangedByLaterBuilderCalls_orThroughItsOverrides() {
    TestCloudConfig.Builder b = TestCloudConfig.builder().override("k", "v1");
    TestCloudConfig built = b.build();
    b.override("k", "v2").override("other", "x");
    assertEquals(Map.of("k", "v1"), built.overrides());
    assertThrows(UnsupportedOperationException.class, () -> built.overrides().put("k", "v3"));
  }

  @Test
  void theType_hasOnlyFinalFields_andNoMutator() {
    for (Field f : TestCloudConfig.class.getDeclaredFields()) {
      if (f.isSynthetic()) continue;
      assertTrue(Modifier.isFinal(f.getModifiers()), f.getName() + " must be final");
    }
    for (Method m : TestCloudConfig.class.getDeclaredMethods()) {
      if (m.isSynthetic() || !Modifier.isPublic(m.getModifiers())) continue;
      if (Modifier.isStatic(m.getModifiers())) continue; // builder()
      assertNotEquals(void.class, m.getReturnType(), m.getName() + " looks like a mutator");
      assertTrue(!m.getName().startsWith("set"), m.getName() + " looks like a setter");
    }
  }
}
