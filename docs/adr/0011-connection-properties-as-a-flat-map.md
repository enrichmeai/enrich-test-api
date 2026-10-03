# 11. Expose the emulator's connection as a flat property map

Date: 2026-10-03
Status: Accepted (Joseph, 2026-10-03: "go with option A for Epic 8"). Settles Story 8.1.

## Context

Epic 8 (`docs/specs/epics.md`): nothing in the provider-neutral API exposes an endpoint or a
credential, so a Spring Boot, Quarkus or Micronaut application under test cannot be pointed at the
emulator this library starts. The only route was `com.enrichmeai.test.cloud.aws.internal.LocalStackHolder`,
which costs a consumer an `internal` package, a direct dependency on `test-cloud-aws`, and
Testcontainers types in their tests. Story 8.1 offered three shapes: A, a flat property map; B, typed
accessors keyed on `CloudServiceType` plus a credentials type; C, a `ConnectionDetails` capability.

## Decision

**Option A.** `CloudAdapter` gains `default Map<String, String> connectionProperties()`. The default
throws `UnsupportedOperationException` naming the adapter, so an adapter that has not implemented it
fails loudly instead of handing back an empty map that reads as "no endpoint". The map is unmodifiable.

**The keys are the contract**, published as constants in `com.enrichmeai.test.core.cloud.ConnectionProperties`
so they are not retyped as strings in every test:

| Key | Value | Present |
|---|---|---|
| `cloud.provider` | `aws`, `azure`, `gcp` | always |
| `cloud.mode` | `emulator`, `live` | always |
| `cloud.region` | region or location | when the provider has one |
| `cloud.credentials.key` | the identifier half (AWS access key id; Azure account name) | when the client must not use the platform's default credentials |
| `cloud.credentials.secret` | the secret half | whenever `cloud.credentials.key` is |
| `cloud.endpoint.<service>` | endpoint URI; `<service>` is `CloudServiceType` in lower case (`storage`, `queue`, `pubsub`, `nosql`) | for each implemented service, when the client must not use the provider's default endpoint (always in emulator mode) |

An adapter may add keys under its own `<provider>.` prefix. Those are not part of the contract.

The AWS adapter, in emulator mode, starts LocalStack if needed and returns the keys from the
container: the same endpoint per service, region (the configured one, else `us-east-1`, as the
clients use) and access key pair that the capabilities use. In live mode it returns provider, mode and
region only: the application uses AWS's default endpoints and credential chain.

The map is reachable without the JUnit extension, through `CloudAdapters.get(provider, config)`. That
is what a Spring `@DynamicPropertySource` (a static method) and a Quarkus
`QuarkusTestResourceLifecycleManager#start()` (which returns a `Map<String, String>`) need. No
framework type enters `test-core` or `test-cloud-aws`.

## Consequences

+ A framework test configures its application from the map without importing anything under
  `internal`, a vendor SDK type or Testcontainers. Story 8.3 proves it with a worked example.
+ No new type appears in the SPI's signatures. `ConnectionProperties` only holds key constants.
- The keys are a string contract with no compiler behind it. Renaming one is a breaking change that
  only the tests in `ConnectionPropertiesTest` and the adapters' tests catch.
- Two credential keys fit an access-key or account-key model. A provider whose emulator uses another
  auth model (a token, a service-account file) has to add `<provider>.` keys. Epic 3's second adapter
  is what tests this, as Story 8.1 said it would.
- `SECRETS` and `KMS` have no endpoint key until an adapter implements them (Story 6.4).
