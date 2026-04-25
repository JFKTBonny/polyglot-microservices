package com.microservices.payment.repository;

import com.microservices.payment.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    // Spring Data JPA generates the SQL automatically from the method name
    // findByOrderId → SELECT * FROM payments WHERE order_id = ?
    Optional<Payment> findByOrderId(UUID orderId);

    // findByUserId → SELECT * FROM payments WHERE user_id = ?
    List<Payment> findByUserId(UUID userId);

    // findByStatus → SELECT * FROM payments WHERE status = ?
    List<Payment> findByStatus(String status);

    // existsByOrderId → SELECT COUNT(*) > 0 FROM payments WHERE order_id = ?
    boolean existsByOrderId(UUID orderId);
}