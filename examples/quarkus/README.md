# Quarkus example: point your application at the emulator

A small Quarkus 3.40 service (`PUT` and `GET /files/{name}` against an S3 bucket) and one
`@QuarkusTest` that runs it against the LocalStack emulator enrich-test-api starts.

The whole integration is [`EmulatorResource`](src/test/java/com/example/uploads/EmulatorResource.java),
a `QuarkusTestResourceLifecycleManager`. Quarkus calls its `start()` before the application starts and
applies the map it returns as configuration. That map is the library's provider-neutral keys
(`ConnectionProperties`, ADR 0011) copied into the application's own `app.storage.*` keys. Nothing in
the test code names an AWS SDK type, imports from `test-cloud-aws`, or uses a Testcontainers type.

The resource writes one file through the library's `BlobStorage` before the application starts, and
the test reads it back through the application. This proves the application is talking to the
library's emulator. The test does not reach into the library from inside the `@QuarkusTest`, because
Quarkus runs tests in its own class loader.

```sh
# from the repository root, with Docker running
mvn -B install -DskipTests -pl test-core,test-cloud-aws -am
mvn -B -f examples/quarkus/pom.xml -Denrich-test-api.version=<the root pom's version> verify
```

Quarkus manages Testcontainers 2.x, the same line `test-cloud-aws` is built on. Dev Services are switched
off, because the emulator comes from the library.
