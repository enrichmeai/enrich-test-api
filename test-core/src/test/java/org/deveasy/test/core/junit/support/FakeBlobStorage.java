package org.deveasy.test.core.junit.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.deveasy.test.core.cloud.capability.BlobStorage;

/** Very small in-memory BlobStorage used for testing the CloudExtension injection. */
public final class FakeBlobStorage implements BlobStorage {
  private final Map<String, Map<String, byte[]>> buckets = new ConcurrentHashMap<>();

  @Override
  public void ensureBucket(String name) {
    buckets.computeIfAbsent(name, k -> new ConcurrentHashMap<>());
  }

  @Override
  public void deleteBucket(String name) {
    buckets.remove(name);
  }

  @Override
  public void putObject(String bucket, String key, byte[] data, String contentType) {
    ensureBucket(bucket);
    buckets.get(bucket).put(key, Arrays.copyOf(data, data.length));
  }

  @Override
  public void putObject(String bucket, String key, InputStream data, String contentType) {
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      data.transferTo(baos);
      putObject(bucket, key, baos.toByteArray(), contentType);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public byte[] getObject(String bucket, String key) {
    Map<String, byte[]> map = buckets.get(bucket);
    if (map == null) return null;
    byte[] v = map.get(key);
    return v == null ? null : Arrays.copyOf(v, v.length);
  }

  @Override
  public void deleteObject(String bucket, String key) {
    Map<String, byte[]> map = buckets.get(bucket);
    if (map != null) map.remove(key);
  }

  @Override
  public List<String> listKeys(String bucket, String prefix) {
    Map<String, byte[]> map = buckets.get(bucket);
    if (map == null) return Collections.emptyList();
    String p = prefix == null ? "" : prefix;
    List<String> keys = new ArrayList<>();
    for (String k : map.keySet()) {
      if (p.isEmpty() || k.startsWith(p)) keys.add(k);
    }
    Collections.sort(keys);
    return keys;
  }

  @Override
  public boolean exists(String bucket, String key) {
    Map<String, byte[]> map = buckets.get(bucket);
    return map != null && map.containsKey(key);
  }
}
