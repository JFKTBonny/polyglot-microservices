package com.microservices.payment.service;

import com.microservices.payment.dto.CreatePaymentRequest;
import com.microservices.payment.model.Payment;
import com.microservices.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Random;

@Slf4j                    // generates log field automatically — log.info(), log.error()
@Service                  // marks this as a Spring service bean — injectable everywhere
@RequiredArgsConstructor  // Lombok generates constructor injection automatically
public class PaymentService {

    // Constructor injection — Spring injects this automatically
    // No @Autowired needed with @RequiredArgsConstructor
    private final PaymentRepository paymentRepository;

    @Transactional
    public Payment processPayment(String orderId, String userId, double amount) {

        UUID orderUUID = UUID.fromString(orderId);
        UUID userUUID  = UUID.fromString(userId);

        // Idempotency check — don't process the same order twice
        if (paymentRepository.existsByOrderId(orderUUID)) {
            log.warn("[payment] order {} already processed — returning existing", orderId);
            return paymentRepository.findByOrderId(orderUUID).get();
        }

        // Simulate payment processing
        // In production this would call Stripe, PayPal, etc.
        boolean paymentSucceeded = simulatePaymentGateway(amount);

        Payment payment = Payment.builder()
                .orderId(orderUUID)
                .userId(userUUID)
                .amount(new java.math.BigDecimal(amount))
                .status(paymentSucceeded ? "completed" : "failed")
                .paymentMethod("card")
                .failureReason(paymentSucceeded ? null : "insufficient funds")
                .build();

        Payment saved = paymentRepository.save(payment);

        log.info("[payment] order {} — status: {} amount: ${}",
                orderId, saved.getStatus(), amount);

        return saved;
    }

    public Optional<Payment> getPaymentByOrderId(String orderId) {
        return paymentRepository.findByOrderId(UUID.fromString(orderId));
    }

    public List<Payment> getPaymentsByUserId(String userId) {
        return paymentRepository.findByUserId(UUID.fromString(userId));
    }

    public List<Payment> getAllPayments() {
        return paymentRepository.findAll();
    }

    // Simulates a payment gateway — 90% success rate
    // Replace with real Stripe/PayPal integration in production
    private boolean simulatePaymentGateway(double amount) {
        Random random = new Random();
        boolean success = random.nextInt(10) != 0; // 90% success
        log.debug("[payment-gateway] amount=${} result={}",
                amount, success ? "approved" : "declined");
        return success;
    }
}