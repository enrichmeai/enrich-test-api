package org.deveasy.test.core.cloud.capability;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.deveasy.test.core.cloud.Capability;
import org.junit.jupiter.api.Test;

public class NoSqlTableApiTest {

  @Test
  public void interfaceExistsAndExtendsCapability() throws Exception {
    Class<?> clazz = Class.forName("org.deveasy.test.core.cloud.capability.NoSqlTable");
    assertTrue(clazz.isInterface(), "NoSqlTable must be an interface");
    assertTrue(Capability.class.isAssignableFrom(clazz), "NoSqlTable must extend Capability");
  }

  @Test
  public void hasRequiredMethodSignatures() throws Exception {
    Class<?> c = Class.forName("org.deveasy.test.core.cloud.capability.NoSqlTable");
    List<String> methodSigs =
        Arrays.stream(c.getMethods())
            .filter(m -> m.getDeclaringClass().equals(c))
            .map(NoSqlTableApiTest::signature)
            .collect(Collectors.toList());

    // Table management
    assertTrue(methodSigs.contains(sig(void.class, "ensureTable", String.class, String.class)));
    assertTrue(
        methodSigs.contains(
            sig(void.class, "ensureTable", String.class, String.class, String.class)));
    assertTrue(methodSigs.contains(sig(void.class, "deleteTable", String.class)));

    // Item operations
    assertTrue(methodSigs.contains(sig(void.class, "putItem", String.class, Map.class)));
    assertTrue(methodSigs.contains(sig(Map.class, "getItem", String.class, String.class)));
    assertTrue(
        methodSigs.contains(sig(Map.class, "getItem", String.class, String.class, String.class)));
    assertTrue(methodSigs.contains(sig(void.class, "deleteItem", String.class, String.class)));
    assertTrue(
        methodSigs.contains(
            sig(void.class, "deleteItem", String.class, String.class, String.class)));

    // Query/scan
    assertTrue(methodSigs.contains(sig(List.class, "scan", String.class)));
    assertTrue(methodSigs.contains(sig(List.class, "query", String.class, String.class)));
  }

  private static String signature(Method m) {
    return sig(m.getReturnType(), m.getName(), m.getParameterTypes());
  }

  private static String sig(Class<?> ret, String name, Class<?>... params) {
    return ret.getName()
        + " "
        + name
        + "("
        + Arrays.stream(params).map(Class::getName).collect(Collectors.joining(","))
        + ")";
  }
}
