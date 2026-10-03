package com.example.uploads;

import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

@RestController
class UploadController {

  private final S3Client s3;
  private final StorageProperties props;

  UploadController(S3Client s3, StorageProperties props) {
    this.s3 = s3;
    this.props = props;
  }

  /** Stores the request body as {@code name} in the configured bucket. */
  @PutMapping("/files/{name}")
  ResponseEntity<Void> upload(
      @PathVariable String name,
      @org.springframework.web.bind.annotation.RequestBody byte[] content) {
    s3.putObject(b -> b.bucket(props.bucket()).key(name), RequestBody.fromBytes(content));
    return ResponseEntity.created(URI.create("/files/" + name)).build();
  }
}
