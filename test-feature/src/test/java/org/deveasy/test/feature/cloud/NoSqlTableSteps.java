package org.deveasy.test.feature.cloud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.deveasy.test.core.cloud.capability.NoSqlTable;

/** Provider-neutral NoSqlTable (e.g., DynamoDB) steps backed by the resolved CloudAdapter. */
public class NoSqlTableSteps {

  private NoSqlTable table;
  private String tableName;

  @Given("a table named {string} with partition key {string}")
  public void aTable(String tableName, String partitionKey) {
    this.table = CloudSelectionSteps.adapter().noSqlTable();
    this.tableName = tableName;
    table.ensureTable(tableName, partitionKey);
    ScenarioState.put("tableName", tableName);
  }

  @When("I put item with {string}={string} and data {string}")
  public void iPutItem(String keyName, String keyValue, String jsonData) throws Exception {
    ensureTable();
    Map<String, Object> item = new HashMap<>();
    item.put(keyName, keyValue);
    ObjectMapper mapper = new ObjectMapper();
    @SuppressWarnings("unchecked")
    Map<String, Object> data = mapper.readValue(jsonData, Map.class);
    if (data != null) {
      item.putAll(data);
    }
    table.putItem(tableName, item);
  }

  @Then("I can get item with {string}={string}")
  public void iCanGetItem(String keyName, String keyValue) {
    ensureTable();
    Map<String, Object> item = table.getItem(tableName, keyValue);
    assertNotNull(item, "Expected item to be returned for key value: " + keyValue);
    assertEquals(keyValue, String.valueOf(item.get(keyName)));
  }

  @Then("scanning the table returns {int} items")
  public void scanningReturnsItems(int count) {
    ensureTable();
    List<Map<String, Object>> items = table.scan(tableName);
    assertEquals(count, items.size());
  }

  private void ensureTable() {
    if (table == null) {
      table = CloudSelectionSteps.adapter().noSqlTable();
      tableName = ScenarioState.get("tableName");
    }
  }
}
