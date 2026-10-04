package com.example.uploads;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

/** Stores and returns files in the configured S3 bucket. */
@Path("/files/{name}")
public class FilesResource {

  private final S3Client s3;
  private final StorageConfig config;

  FilesResource(S3Client s3, StorageConfig config) {
    this.s3 = s3;
    this.config = config;
  }

  @PUT
  @Consumes(MediaType.APPLICATION_OCTET_STREAM)
  public Response upload(@PathParam("name") String name, byte[] content) {
    s3.putObject(b -> b.bucket(config.bucket()).key(name), RequestBody.fromBytes(content));
    return Response.created(URI.create("/files/" + name)).build();
  }

  @GET
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  public byte[] download(@PathParam("name") String name) {
    return s3.getObjectAsBytes(b -> b.bucket(config.bucket()).key(name)).asByteArray();
  }
}
