package com.microservices.payment.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component               // marks this as a Spring bean — injectable everywhere
@RequiredArgsConstructor
public class KafkaProducer {

    // KafkaTemplate is auto-configured by Spring Boot
    // based on application.properties settings — no manual setup needed
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;  // Jackson JSON serializer — auto-configured

    public void publish(String topic, String key, Object payload) {
        try {
            // Serialize payload to JSON string
            String json = objectMapper.writeValueAsString(payload);

            // Send message — returns a Future but we don't wait for it here
            // Spring Kafka handles retries and error callbacks internally
            kafkaTemplate.send(topic, key, json)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("[kafka] failed to publish to '{}': {}", topic, ex.getMessage());
                        } else {
                            log.info("[kafka] published to '{}' partition={} offset={}",
                                    topic,
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });

        } catch (Exception e) {
            log.error("[kafka] serialization error: {}", e.getMessage());
        }
    }

    // Convenience methods for specific topics
    public void publishPaymentProcessed(String orderId, String userId, String status) {
        Map<String, String> payload = Map.of(
                "order_id", orderId,
                "user_id",  userId,
                "status",   status
        );
        publish("payment.processed", orderId, payload);
    }

    public void publishPaymentFailed(String orderId, String userId, String reason) {
        Map<String, String> payload = Map.of(
                "order_id", orderId,
                "user_id",  userId,
                "status",   "failed",
                "reason",   reason
        );
        publish("payment.failed", orderId, payload);
    }
}