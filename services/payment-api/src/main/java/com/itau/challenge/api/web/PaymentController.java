package com.itau.challenge.api.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping
public class PaymentController {

  private static final Logger log = LoggerFactory.getLogger(PaymentController.class);
  private final DynamoDbClient ddb;

  public PaymentController(DynamoDbClient ddb) {
    this.ddb = ddb;
  }

  @GetMapping("/health")
  public ResponseEntity<?> health() {
    return ResponseEntity.ok(Map.of("status", "UP"));
  }

  @GetMapping("/payments/{id}")
  public ResponseEntity<?> get(@PathVariable String id) {
    // Validate input
    if (id == null || id.trim().isEmpty()) {
      log.warn("Invalid payment ID requested: {}", id);
      return ResponseEntity.badRequest()
          .body(Map.of("error", "Payment ID cannot be empty"));
    }

    log.info("Fetching payment with ID: {}", id);

    try {
      GetItemResponse resp = ddb.getItem(GetItemRequest.builder()
          .tableName("payments")
          .key(Map.of("paymentId", AttributeValue.builder().s(id).build()))
          .consistentRead(true)
          .build());

      if (!resp.hasItem()) {
        log.warn("Payment not found: {}", id);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("error", "Payment not found"));
      }

      Map<String, AttributeValue> item = resp.item();

      // Safely extract values with null checks
      Map<String, String> response = new HashMap<>();
      response.put("paymentId", extractStringValue(item, "paymentId", id));
      response.put("amount", extractStringValue(item, "amount", "0.00"));
      response.put("customerId", extractStringValue(item, "customerId", "unknown"));

      log.info("Successfully retrieved payment: {}", id);
      return ResponseEntity.ok(response);

    } catch (ResourceNotFoundException e) {
      log.error("DynamoDB table not found: {}", e.getMessage());
      return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
          .body(Map.of("error", "Service temporarily unavailable"));

    } catch (DynamoDbException e) {
      log.error("DynamoDB error for payment {}: {}", id, e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", "Error retrieving payment"));

    } catch (Exception e) {
      log.error("Unexpected error retrieving payment {}: {}", id, e.getMessage(), e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
          .body(Map.of("error", "Internal server error"));
    }
  }

  private String extractStringValue(Map<String, AttributeValue> item, String key, String defaultValue) {
    AttributeValue value = item.get(key);
    if (value != null && value.s() != null) {
      return value.s();
    }
    log.warn("Missing or null attribute '{}', using default: {}", key, defaultValue);
    return defaultValue;
  }
}
