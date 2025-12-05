package com.payflow.payment.bo;

import com.payflow.payment.domain.Payment;
import com.payflow.payment.repository.PaymentRepository;
import com.payflow.user.domain.User;
import com.payflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PaymentBO {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;

    public Payment createPayment(Long userId, int amount, String method) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User Not Found"));

        Payment payment = Payment.builder()
                .user(user)
                .orderId("ORD-" + System.currentTimeMillis())
                .amount(amount)
                .status("ready")
                .method(method)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return paymentRepository.save(payment);
    }

    public Optional<Payment> getPayment(Long paymentId) {
        return paymentRepository.findById(paymentId);
    }

    public List<Payment> getPaymentList() {
        return paymentRepository.findAll();
    }

}
