package com.itau.challenge.worker.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;
import java.time.Duration;

@Configuration
public class SqsConfig {

  private static final Logger log = LoggerFactory.getLogger(SqsConfig.class);

  @Value("${aws.region}")
  private String region;
  @Value("${aws.endpoint}")
  private String endpoint;
  @Value("${aws.localstack:true}")
  private boolean local;

  @Bean
  public SqsClient sqsClient() {
    log.info("Configuring SQS client - Region: {}, Local: {}, Endpoint: {}", region, local, endpoint);

    var builder = SqsClient.builder()
        .region(Region.of(region))
        .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create("test", "test")))
        .overrideConfiguration(config -> config
            .apiCallTimeout(Duration.ofSeconds(30))
            .apiCallAttemptTimeout(Duration.ofSeconds(10)));

    if (local) {
      builder.endpointOverride(URI.create(endpoint));
      log.info("Using LocalStack endpoint: {}", endpoint);
    }

    SqsClient client = builder.build();
    log.info("SQS client configured successfully");
    return client;
  }
}
