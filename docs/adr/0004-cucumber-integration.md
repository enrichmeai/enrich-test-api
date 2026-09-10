# 4. Provider‑Neutral BDD with Cucumber and JUnit Platform

Date: 2025-11-01
Status: Accepted

## Context
We want executable specifications that are readable by QA/Dev and reusable across cloud providers. The suite should be provider‑neutral and discover provider adapters via SPI at runtime. Tests need to run under JUnit Platform and integrate with IDEs and CI.

## Decision
Adopt Cucumber running on JUnit Platform. Place `.feature` files under `test-feature/src/test/resources/features/` and glue in `com.enrichmeai.test.feature.cloud`. Provide a JUnit Platform suite (`CucumberQueueSuite`, etc.) that:
- Scans classpath resource path `features`.
- Uses glue package `com.enrichmeai.test.feature.cloud`.
- Discovers provider implementations via Java `ServiceLoader` of `CloudAdapter` brought onto the test classpath by adding the provider module (e.g., `test-cloud-aws`) as a test‑scope dependency of `test-feature`.

Selection and configuration:
- Parameterize provider, mode (EMULATOR vs REAL), and region via selection steps and environment/props.
- Share cross‑scenario state via a lightweight `ScenarioState` object.
- Step definitions call provider‑neutral capability interfaces from `test-core`.

## Consequences
+ Human‑readable specs that double as documentation.
+ High reuse: the same features validate any provider that implements the capabilities.
+ Works seamlessly in CI via JUnit Platform with standard reports.
- Requires discipline to keep steps provider‑neutral.
- Debugging glue across modules can be slightly more involved for new contributors.

---
