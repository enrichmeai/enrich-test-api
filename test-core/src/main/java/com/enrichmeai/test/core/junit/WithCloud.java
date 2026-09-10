/*
 * Copyright 2025
 * Apache License, Version 2.0
 */
package com.enrichmeai.test.core.junit;

import com.enrichmeai.test.core.cloud.CloudMode;
import com.enrichmeai.test.core.cloud.CloudProvider;
import com.enrichmeai.test.core.cloud.CloudServiceType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/** JUnit 5 annotation to provision a CloudAdapter for tests and inject capabilities. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(CloudExtension.class)
public @interface WithCloud {
  CloudProvider provider();

  CloudMode mode() default CloudMode.EMULATOR;

  CloudServiceType[] services() default {};

  String region() default "us-east-1";
}
