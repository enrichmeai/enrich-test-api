package org.deveasy.test.feature.cloud;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;

import io.cucumber.junit.platform.engine.Cucumber;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/**
 * JUnit Platform suite to run provider-neutral Queue feature(s). Looks under classpath resource
 * path "features" and uses this package for glue.
 */
@Suite
@Cucumber
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "org.deveasy.test.feature.cloud")
public class CucumberQueueSuite {}
