package org.deveasy.test.core.junit;

import static org.junit.jupiter.api.Assertions.*;

import org.deveasy.test.core.cloud.CloudMode;
import org.deveasy.test.core.cloud.CloudProvider;
import org.deveasy.test.core.cloud.capability.BlobStorage;
import org.junit.jupiter.api.Test;

@WithCloud(
    provider = CloudProvider.AWS,
    mode = CloudMode.EMULATOR,
    services = {})
class WithCloudInjectionTest {

  @Test
  void injectsBlobStorage_andAllowsPutGet(BlobStorage storage) {
    String bucket = "junit-bucket";
    String key = "k1";
    byte[] data = "hello".getBytes();

    storage.ensureBucket(bucket);
    storage.putObject(bucket, key, data, "text/plain");
    byte[] out = storage.getObject(bucket, key);

    assertNotNull(out, "object should be retrievable");
    assertArrayEquals(data, out);
  }
}
