package com.itau.challenge.worker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.math.BigDecimal;

@Component
public class PaymentConsumer {

  private static final Logger log = LoggerFactory.getLogger(PaymentConsumer.class);
  private final SqsClient sqs;
  private final JdbcTemplate jdbc;
  private final String queueName;
  private final ObjectMapper mapper = new ObjectMapper();
  private String queueUrlCache;

  public PaymentConsumer(SqsClient sqs, JdbcTemplate jdbc, @Value("${queues.payments}") String queueName) {
    this.sqs = sqs;
    this.jdbc = jdbc;
    this.queueName = queueName;
  }

  private String queueUrl() {
    if (queueUrlCache == null) {
      try {
        queueUrlCache = sqs.getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build()).queueUrl();
        log.info("Queue URL resolved: {}", queueUrlCache);
      } catch (Exception e) {
        log.error("Failed to get queue URL for: {}", queueName, e);
        throw new RuntimeException("Cannot resolve queue URL", e);
      }
    }
    return queueUrlCache;
  }

  @Scheduled(fixedDelay = 1000)
  void poll() {
    try {
      ReceiveMessageResponse resp = sqs.receiveMessage(ReceiveMessageRequest.builder()
          .queueUrl(queueUrl())
          .maxNumberOfMessages(10)
          .waitTimeSeconds(1)
          .visibilityTimeout(30)
          .build());

      if (resp.messages().isEmpty()) {
        log.debug("No messages received from queue");
        return;
      }

      log.info("Received {} messages from queue", resp.messages().size());

      for (Message m : resp.messages()) {
        processMessage(m);
      }
    } catch (Exception e) {
      log.error("Error polling SQS queue: {}", e.getMessage(), e);
    }
  }

  private void processMessage(Message m) {
    try {
      log.debug("Processing message: {}", m.messageId());

      // Parse outer SNS envelope
      JsonNode root = mapper.readTree(m.body());
      String innerMessage = root.path("Message").asText();

      if (innerMessage.isEmpty()) {
        log.warn("Empty message body in SNS envelope, skipping message: {}", m.messageId());
        deleteMessage(m.receiptHandle());
        return;
      }

      // Parse actual payment payload
      JsonNode payload = mapper.readTree(innerMessage);

      // Validate required fields
      String paymentId = payload.path("paymentId").asText();
      String amountStr = payload.path("amount").asText();
      String customerId = payload.path("customerId").asText();

      if (paymentId.isEmpty() || amountStr.isEmpty() || customerId.isEmpty()) {
        log.error("Missing required fields in payload: paymentId={}, amount={}, customerId={}",
            paymentId, amountStr, customerId);
        deleteMessage(m.receiptHandle()); // Remove invalid message
        return;
      }

      // Parse and validate amount
      BigDecimal amount;
      try {
        amount = new BigDecimal(amountStr);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
          log.error("Invalid amount value: {}", amountStr);
          deleteMessage(m.receiptHandle());
          return;
        }
      } catch (NumberFormatException e) {
        log.error("Invalid amount format: {}", amountStr, e);
        deleteMessage(m.receiptHandle());
        return;
      }

      // Insert into database
      int rowsAffected = jdbc.update(
          "insert into payment_events(payment_id, amount, customer_id) values (?,?,?)",
          paymentId, amount, customerId);

      if (rowsAffected > 0) {
        log.info("Successfully processed payment {} for customer {} with amount {}",
            paymentId, customerId, amount);
        deleteMessage(m.receiptHandle());
      } else {
        log.error("Failed to insert payment event for paymentId: {}", paymentId);
      }

    } catch (Exception e) {
      log.error("Error processing message {}: {}", m.messageId(), e.getMessage(), e);
      // Don't delete message - let it go back to queue for retry
    }
  }

  private void deleteMessage(String receiptHandle) {
    try {
      sqs.deleteMessage(DeleteMessageRequest.builder()
          .queueUrl(queueUrl())
          .receiptHandle(receiptHandle)
          .build());
      log.debug("Message deleted from queue");
    } catch (Exception e) {
      log.error("Failed to delete message: {}", e.getMessage(), e);
    }
  }
}
