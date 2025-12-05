package com.payflow.payment.bo;

import com.payflow.payment.client.PgClient;
import com.payflow.payment.client.PgResponse;
import com.payflow.payment.domain.Payment;
import com.payflow.payment.repository.PaymentRepository;
import com.payflow.user.domain.User;
import com.payflow.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class PaymentBO {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final PgClient pgClient;  // PG사 클라이언트

    @Autowired
    public PaymentBO(
            PaymentRepository paymentRepository,
            UserRepository userRepository,
            @Value("${pg.type:portOneClient}") String pgType,
            Map<String, PgClient> pgClients
    ) {
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.pgClient = pgClients.getOrDefault(pgType, pgClients.get("portOneClient"));
        log.info("사용할 PG 클라이언트: {} ({})", pgType, pgClient.getPgName());
    }

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

    /**
     * PG사 결제 페이지 URL 생성
     */
    public String getPaymentUrl(Payment payment) {
        return pgClient.generatePaymentUrl(payment);
    }

    /**
     * PG사 결제 요청 파라미터 생성
     */
    public Map<String, String> getPaymentParams(Payment payment) {
        return pgClient.generatePaymentParams(payment);
    }

    /**
     * PG사 승인 결과 처리 (Return URL 콜백)
     */
    @Transactional
    public Payment processPaymentCallback(Map<String, String> responseData) {
        String orderId = responseData.get("orderId");
        
        if (orderId == null) {
            throw new RuntimeException("orderId가 없습니다.");
        }

        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Payment not found for orderId: " + orderId));

        // 이미 처리된 결제인지 확인
        if (!"ready".equals(payment.getStatus())) {
            log.warn("이미 처리된 결제입니다 - orderId: {}, status: {}", orderId, payment.getStatus());
            return payment;
        }

        try {
            // PG사 응답 검증
            PgResponse pgResponse = pgClient.verifyPayment(responseData);

            // 결제 정보 업데이트
            payment.setPgTid(pgResponse.getPgTid());
            payment.setPgResponse(pgResponse.getRawResponse());
            payment.updateStatus(pgResponse.getStatus());

            log.info("PG사 결제 처리 완료 - orderId: {}, status: {}, pgTid: {}", 
                    orderId, pgResponse.getStatus(), pgResponse.getPgTid());

            return paymentRepository.save(payment);

        } catch (Exception e) {
            // 실패 시 상태 업데이트
            payment.updateStatus("failed");
            payment.setPgResponse("Error: " + e.getMessage());
            paymentRepository.save(payment);
            
            log.error("PG사 결제 처리 실패 - orderId: {}, error: {}", orderId, e.getMessage());
            throw new RuntimeException("결제 처리 실패: " + e.getMessage(), e);
        }
    }

    public Optional<Payment> getPayment(Long paymentId) {
        return paymentRepository.findById(paymentId);
    }

    public Optional<Payment> getPaymentByOrderId(String orderId) {
        return paymentRepository.findByOrderId(orderId);
    }

    public List<Payment> getPaymentList() {
        return paymentRepository.findAll();
    }
}
