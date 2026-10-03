package com.example.uploads;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.common.WithTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/** The application, configured by {@link EmulatorResource}, against the library's emulator. */
@QuarkusTest
@WithTestResource(EmulatorResource.class)
class FilesResourceTest {

  @Test
  void theApplicationReadsWhatTheLibraryWroteToTheEmulator() {
    given()
        .when()
        .get("/files/" + EmulatorResource.SEED_KEY)
        .then()
        .statusCode(200)
        .body(equalTo(EmulatorResource.SEED));
  }

  @Test
  void anUploadThroughTheApplicationRoundTrips() {
    given()
        .contentType("application/octet-stream")
        .body("hello from the app".getBytes())
        .when()
        .put("/files/hello.txt")
        .then()
        .statusCode(201);

    given()
        .when()
        .get("/files/hello.txt")
        .then()
        .statusCode(200)
        .body(equalTo("hello from the app"));
  }
}
