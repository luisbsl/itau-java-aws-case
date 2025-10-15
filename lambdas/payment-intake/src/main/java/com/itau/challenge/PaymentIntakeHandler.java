package com.itau.challenge;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("unchecked")
public class PaymentIntakeHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final DynamoDbClient ddb;
  private final SnsClient sns;

  public PaymentIntakeHandler() {
    String region = System.getenv().getOrDefault("AWS_REGION", "us-east-1");
    Region r = Region.of(region);

    boolean local = "true".equalsIgnoreCase(System.getenv().getOrDefault("LOCALSTACK", "true"));
    String host = System.getenv().getOrDefault("LOCALSTACK_HOST", "localhost");
    URI endpoint = URI.create("http://" + host + ":4566");

    if (local) {
      this.ddb = DynamoDbClient.builder().region(r).endpointOverride(endpoint).build();
      this.sns = SnsClient.builder().region(r).endpointOverride(endpoint).build();
    } else {
      this.ddb = DynamoDbClient.builder().region(r).build();
      this.sns = SnsClient.builder().region(r).build();
    }
  }

  @Override
  public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
    try {
      String id = UUID.randomUUID().toString();
      String table = System.getenv("TABLE_NAME");
      String topicArn = System.getenv("TOPIC_ARN");

      String bodyStr = (String) event.getOrDefault("body", "{}");
      Map<String, Object> body = MAPPER.readValue(bodyStr, Map.class);

      String amount = String.valueOf(body.getOrDefault("amount", "0.00"));
      String customer = String.valueOf(body.getOrDefault("customerId", "unknown"));

      Map<String, AttributeValue> item = new HashMap<>();
      item.put("paymentId", AttributeValue.builder().s(id).build());
      item.put("amount", AttributeValue.builder().s(amount).build());
      item.put("customerId", AttributeValue.builder().s(customer).build());
      ddb.putItem(PutItemRequest.builder().tableName(table).item(item).build());

      Map<String, Object> msg = Map.of("paymentId", id, "amount", amount, "customerId", customer);
      sns.publish(PublishRequest.builder().topicArn(topicArn).message(MAPPER.writeValueAsString(msg)).build());

      return Map.of("statusCode", 202, "headers", Map.of("Content-Type", "application/json"),
          "body", MAPPER.writeValueAsString(Map.of("paymentId", id)));
    } catch (Exception e) {
      return Map.of("statusCode", 500, "headers", Map.of("Content-Type", "application/json"),
          "body", "{\"error\":\"" + e.getMessage() + "\"}");
    }
  }
}
