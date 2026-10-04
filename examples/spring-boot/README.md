# Spring Boot example: point your application at the emulator

A small Spring Boot 3.5 service (`PUT /files/{name}` stores the body in an S3 bucket) and one
`@SpringBootTest` that starts it against the LocalStack emulator enrich-test-api starts.

The whole integration is the `@DynamicPropertySource` method in
[`UploadControllerTest`](src/test/java/com/example/uploads/UploadControllerTest.java). It copies the
library's provider-neutral keys (`ConnectionProperties`, ADR 0011) into the application's own
`app.storage.*` keys. The test names no AWS SDK type, imports nothing from `test-cloud-aws`, and
uses no Testcontainers type. `test-cloud-aws` is on the test classpath only so that `ServiceLoader`
finds it.

```sh
# from the repository root, with Docker running
mvn -B install -DskipTests -pl test-core,test-cloud-aws -am
mvn -B -f examples/spring-boot/pom.xml -Denrich-test-api.version=<the root pom's version> verify
```

Outside this repository, drop `-Denrich-test-api.version` to use the released version in the POM.

Spring Boot 3.5 rather than 4.x: Boot 4 manages Testcontainers 2.x, and `test-cloud-aws` is built on
Testcontainers 1.20. This project is not a module of the library's build, and the `examples` workflow
builds it on every PR.

For Quarkus, see [`../quarkus`](../quarkus).
