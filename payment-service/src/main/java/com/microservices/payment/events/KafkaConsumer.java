package com.microservices.payment.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservices.payment.model.Payment;
import com.microservices.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaConsumer {

    private final PaymentService paymentService;
    private final KafkaProducer  kafkaProducer;
    private final ObjectMapper   objectMapper;

    // @KafkaListener replaces the entire consumer setup you wrote manually
    // in Python and Go — no reader setup, no for loop, no manual start()
    // Spring Boot wires everything from application.properties automatically
    @KafkaListener(
            topics   = "stock.reserved",
            groupId  = "payment-service"
    )
    public void handleStockReserved(
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment       // manual ack — from application.properties
    ) {
        log.info("[kafka] received message topic={} partition={} offset={}",
                record.topic(), record.partition(), record.offset());

        try {
            // Deserialize JSON string to Map
            Map<String, Object> event = objectMapper.readValue(
                    record.value(),
                    objectMapper.getTypeFactory()
                            .constructMapType(Map.class, String.class, Object.class)
            );

            String orderId = (String) event.get("order_id");
            String userId  = (String) event.get("user_id");
            String status  = (String) event.get("status");

            log.info("[kafka] stock event — order={} status={}", orderId, status);

            // Only process payment if stock was successfully reserved
            if ("reserved".equals(status)) {
                processPayment(orderId, userId, event);
            } else {
                // Stock insufficient — no payment needed
                log.warn("[kafka] stock insufficient for order {} — skipping payment", orderId);
                kafkaProducer.publishPaymentFailed(
                        orderId, userId, "stock insufficient"
                );
            }

            // Manually acknowledge — commit offset only after success
            acknowledgment.acknowledge();
            log.info("[kafka] offset committed partition={} offset={}",
                    record.partition(), record.offset());

        } catch (Exception e) {
            log.error("[kafka] failed to process message: {}", e.getMessage());
            // Don't acknowledge — message will be redelivered
            // In production add retry logic and DLQ here
        }
    }

    private void processPayment(String orderId, String userId, Map<String, Object> event) {
        try {
            // Extract total from event — handle both Integer and Double
            double amount = 0.0;
            Object totalObj = event.get("total");
            if (totalObj instanceof Number) {
                amount = ((Number) totalObj).doubleValue();
            }

            log.info("[payment] processing payment for order={} amount={}", orderId, amount);

            // Process payment via service layer
            Payment payment = paymentService.processPayment(orderId, userId, amount);

            // Publish result based on payment outcome
            if ("completed".equals(payment.getStatus())) {
                log.info("[payment] ✓ payment completed for order={}", orderId);
                kafkaProducer.publishPaymentProcessed(orderId, userId, "completed");
            } else {
                log.warn("[payment] ✗ payment failed for order={} reason={}",
                        orderId, payment.getFailureReason());
                kafkaProducer.publishPaymentFailed(
                        orderId, userId, payment.getFailureReason()
                );
            }

        } catch (Exception e) {
            log.error("[payment] error processing payment for order={}: {}",
                    orderId, e.getMessage());
            kafkaProducer.publishPaymentFailed(orderId, userId, e.getMessage());
        }
    }

    // Also listen to stock.insufficient directly
    // in case inventory service publishes there instead
    @KafkaListener(
            topics  = "stock.insufficient",
            groupId = "payment-service"
    )
    public void handleStockInsufficient(
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment
    ) {
        log.warn("[kafka] stock insufficient event received partition={} offset={}",
                record.partition(), record.offset());

        try {
            Map<String, Object> event = objectMapper.readValue(
                    record.value(),
                    objectMapper.getTypeFactory()
                            .constructMapType(Map.class, String.class, Object.class)
            );

            String orderId = (String) event.get("order_id");
            String userId  = (String) event.get("user_id");

            kafkaProducer.publishPaymentFailed(
                    orderId, userId, "stock insufficient"
            );

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("[kafka] failed to handle stock.insufficient: {}", e.getMessage());
        }
    }
}