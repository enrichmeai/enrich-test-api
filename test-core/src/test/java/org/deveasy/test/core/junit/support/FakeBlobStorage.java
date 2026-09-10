package org.deveasy.test.core.junit.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.deveasy.test.core.cloud.capability.BlobStorage;

/** Very small in-memory BlobStorage used for testing the CloudExtension injection and cleanup. */
public final class FakeBlobStorage implements BlobStorage {
  private final Map<String, Map<String, byte[]>> buckets = new ConcurrentHashMap<>();
  private final List<String> deleteAttempts = new CopyOnWriteArrayList<>();
  private final Set<String> failDeletes = ConcurrentHashMap.newKeySet();

  /** Names of the buckets that currently exist. */
  public Set<String> buckets() {
    return Collections.unmodifiableSet(buckets.keySet());
  }

  /** Every bucket name {@link #deleteBucket} was called with, in order, whether or not it threw. */
  public List<String> deleteAttempts() {
    return Collections.unmodifiableList(deleteAttempts);
  }

  /** Makes {@link #deleteBucket} throw for the named bucket, leaving it in place. */
  public void failDeleteOf(String name) {
    failDeletes.add(name);
  }

  @Override
  public void ensureBucket(String name) {
    buckets.computeIfAbsent(name, k -> new ConcurrentHashMap<>());
  }

  @Override
  public void deleteBucket(String name) {
    deleteAttempts.add(name);
    if (failDeletes.contains(name)) {
      throw new IllegalStateException("simulated failure deleting bucket '" + name + "'");
    }
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
