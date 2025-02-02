/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.solr.metrics;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import org.apache.solr.SolrJettyTestBase;
import org.apache.solr.core.NodeConfig;
import org.apache.solr.core.SolrXmlConfig;
import org.junit.BeforeClass;
import org.junit.Test;

/** Test {@link OperatingSystemMetricSet} and proper JVM metrics registration. */
public class JvmMetricsTest extends SolrJettyTestBase {
  static final String[] STRING_OS_METRICS = {"arch", "name", "version"};
  static final String[] NUMERIC_OS_METRICS = {"availableProcessors", "systemLoadAverage"};

  static final String[] BUFFER_METRICS = {
    "direct.Count",
    "direct.MemoryUsed",
    "direct.TotalCapacity",
    "mapped.Count",
    "mapped.MemoryUsed",
    "mapped.TotalCapacity"
  };

  @BeforeClass
  public static void beforeTest() throws Exception {
    createAndStartJetty(legacyExampleCollection1SolrHome());
  }

  @Test
  public void testOperatingSystemMetricSet() {
    OperatingSystemMetricSet set = new OperatingSystemMetricSet();
    Map<String, Metric> metrics = set.getMetrics();
    assertTrue(metrics.size() > 0);
    for (String metric : NUMERIC_OS_METRICS) {
      Gauge<?> gauge = (Gauge<?>) metrics.get(metric);
      assertNotNull(metric, gauge);
      double value = ((Number) gauge.getValue()).doubleValue();
      // SystemLoadAverage on Windows may be -1.0
      assertTrue("unexpected value of " + metric + ": " + value, value >= 0 || value == -1.0);
    }
    for (String metric : STRING_OS_METRICS) {
      Gauge<?> gauge = (Gauge<?>) metrics.get(metric);
      assertNotNull(metric, gauge);
      String value = (String) gauge.getValue();
      assertNotNull(value);
      assertFalse(value.isEmpty());
    }
  }

  @Test
  public void testAltBufferPoolMetricSet() {
    AltBufferPoolMetricSet set = new AltBufferPoolMetricSet();
    Map<String, Metric> metrics = set.getMetrics();
    assertTrue(metrics.size() > 0);
    for (String name : BUFFER_METRICS) {
      assertNotNull(name, metrics.get(name));
      Object g = metrics.get(name);
      assertTrue(g instanceof Gauge);
      Object v = ((Gauge<?>) g).getValue();
      assertTrue(v instanceof Long);
    }
  }

  @Test
  public void testSystemProperties() {
    if (System.getProperty("basicauth") == null) {
      // make sure it's set
      System.setProperty("basicauth", "foo:bar");
    }
    SolrMetricManager metricManager = jetty.getCoreContainer().getMetricManager();
    Map<String, Metric> metrics = metricManager.registry("solr.jvm").getMetrics();
    Metric metric = metrics.get("system.properties");
    assertNotNull(metrics.toString(), metric);
    MetricsMap map = (MetricsMap) ((SolrMetricManager.GaugeWrapper<?>) metric).getGauge();
    assertNotNull(metrics.toString(), map);
    Map<String, Object> values = map.getValue();
    System.getProperties()
        .forEach(
            (k, v) -> {
              if (NodeConfig.NodeConfigBuilder.DEFAULT_HIDDEN_SYS_PROPS.contains(k)) {
                assertNull("hidden property " + k + " present!", values.get(k));
              } else {
                assertEquals(v, values.get(String.valueOf(k)));
              }
            });
  }

  @Test
  public void testHiddenSysProps() throws Exception {
    Path home = TEST_PATH();

    // default config
    String solrXml = Files.readString(home.resolve("solr.xml"), StandardCharsets.UTF_8);
    NodeConfig config = SolrXmlConfig.fromString(home, solrXml);
    NodeConfig.NodeConfigBuilder.DEFAULT_HIDDEN_SYS_PROPS.forEach(
        s -> assertTrue(s, config.isSysPropHidden(s)));

    // custom config
    solrXml = Files.readString(home.resolve("solr-hiddensysprops.xml"), StandardCharsets.UTF_8);
    NodeConfig config2 = SolrXmlConfig.fromString(home, solrXml);
    Arrays.asList("foo", "bar", "baz").forEach(s -> assertTrue(s, config2.isSysPropHidden(s)));
  }

  @Test
  public void testSetupJvmMetrics() {
    SolrMetricManager metricManager = jetty.getCoreContainer().getMetricManager();
    Map<String, Metric> metrics = metricManager.registry("solr.jvm").getMetrics();
    assertTrue(metrics.size() > 0);
    assertTrue(
        metrics.toString(),
        metrics.entrySet().stream().anyMatch(e -> e.getKey().startsWith("buffers.")));
    assertTrue(
        metrics.toString(),
        metrics.entrySet().stream().anyMatch(e -> e.getKey().startsWith("classes.")));
    assertTrue(
        metrics.toString(),
        metrics.entrySet().stream().anyMatch(e -> e.getKey().startsWith("os.")));
    assertTrue(
        metrics.toString(),
        metrics.entrySet().stream().anyMatch(e -> e.getKey().startsWith("gc.")));
    assertTrue(
        metrics.toString(),
        metrics.entrySet().stream().anyMatch(e -> e.getKey().startsWith("memory.")));
    assertTrue(
        metrics.toString(),
        metrics.entrySet().stream().anyMatch(e -> e.getKey().startsWith("threads.")));
    assertTrue(
        metrics.toString(),
        metrics.entrySet().stream().anyMatch(e -> e.getKey().startsWith("system.")));
  }
}
