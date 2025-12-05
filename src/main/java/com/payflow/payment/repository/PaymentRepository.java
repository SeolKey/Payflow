package com.payflow.payment.repository;

import com.payflow.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    
    Optional<Payment> findByOrderId(String orderId);
}
