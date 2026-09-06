---
title: enrich-test-api
type: product-brief
status: draft
created: '2026-09-06'
updated: '2026-09-06'
---

# Product Brief: enrich-test-api

## Executive Summary

`enrich-test-api` is a Java test library that lets a team write integration tests against cloud services without a cloud account. A test asks for a `BlobStorage` or a `Queue`, and the library hands it one backed by a LocalStack container that it started. The test never names AWS.

The value is in the seam. Capability interfaces live in a module with no vendor SDK on its classpath. The AWS SDK lives in a separate module discovered at runtime through `java.util.ServiceLoader`. A test compiled against the core does not know which cloud it will run on, so the same test can later run against a second provider by putting a different jar on the classpath.

Today one provider is implemented, AWS, and only in emulator mode. This is a private alpha at `0.3.0-alpha1-private.1`, not published to Maven Central.

## The Problem

Teams testing cloud-backed Java services choose between three bad options.

Mocking the SDK tests the mock. `when(s3Client.getObject(any())).thenReturn(...)` passes whether or not the bucket policy, the key encoding, or the pagination logic is right. The test goes green and the service still breaks.

Testing against a real account is slow, costs money, needs credentials in CI, and makes tests order-dependent because they share mutable state. It also cannot run on a laptop offline.

Wiring Testcontainers and LocalStack by hand works, but every team writes the same 200 lines: start the container, build a client pointed at its endpoint, wait for readiness, create the bucket, clean up after. That code is copied between repositories and rots independently in each one.

The cost is that integration tests either do not exist or do not mean anything. This repository is its own evidence: before September 2026 it had four integration test classes and a Cucumber suite that matched no configured Maven plugin, so none of them had ever run, while the README claimed they passed.

## The Solution

A test declares what it needs and gets it.

```java
@WithCloud(provider = AWS, mode = EMULATOR)
class OrderServiceIT {
  @Test
  void storesTheReceipt(BlobStorage storage) {
    storage.ensureBucket("receipts");
    storage.putObject("receipts", "o-1.json", body, "application/json");
    assertTrue(storage.exists("receipts", "o-1.json"));
  }
}
```

The JUnit 5 extension resolves the parameter, the SPI finds the AWS adapter on the classpath, the adapter starts LocalStack once per JVM and shares it. The same capability is available to Cucumber steps, so a `.feature` file can drive the same infrastructure in provider-neutral language.

## What Makes This Different

The differentiator is the dependency direction, not the emulator. Testcontainers already provides LocalStack. What this adds is that `test-core` has no vendor SDK, so a test written against it has no compile-time knowledge of a provider. Swapping providers is a classpath change.

Being honest about the moat: this is a thin, well-shaped layer over Testcontainers and the AWS SDK, not novel technology. Its advantage is that the seam is enforced by module boundaries and by an Enforcer rule, so it cannot quietly erode.

## Who This Serves

**Primary: the Java backend engineer** writing tests for a service that talks to S3, SQS, SNS or DynamoDB. They want a test that fails when the code is wrong and passes when it is right, without a cloud account. Success is that they write a test in the same time a mock would have taken, and it catches a real bug.

**Secondary: the platform or QA engineer** standardising testing across several repositories. They want one dependency and one documented pattern instead of 200 copied lines per repository. Success is that a new service gets working integration tests on day one.

**Secondary: the BDD-oriented team** that keeps acceptance criteria in `.feature` files. They want steps that read as business language, not as AWS API calls.

## Success Criteria

- A team new to the library writes a first passing integration test from the README alone, without reading source.
- Adding a second provider adapter requires no change to `test-core` and no change to any test written against it.
- `mvn verify` on a laptop with Docker running is the only command needed. It is, today.
- The library catches a class of bug that mocks cannot: a test fails because of real service semantics, such as SQS visibility timeouts or DynamoDB key handling.

## Scope

**In for the current version.** Four capabilities against AWS in emulator mode: BlobStorage, Queue, PubSub, NoSqlTable. A JUnit 5 extension. Provider-neutral Cucumber glue. A quality gate set that runs on every build.

**Explicitly out.** Azure and GCP adapters. `CloudMode.LIVE` against a real account. Publishing to Maven Central. The `SECRETS` and `KMS` values declared in `CloudServiceType` have no interface behind them.

## Vision

If this succeeds it becomes the default way a Java team tests cloud-backed code: one dependency, provider-neutral tests, no account, no mocks. The capability set grows to cover secrets and key management, and second and third adapters prove the seam holds. The measure of success is that the provider-neutral tests written today still compile and pass unchanged against an adapter that did not exist when they were written.
