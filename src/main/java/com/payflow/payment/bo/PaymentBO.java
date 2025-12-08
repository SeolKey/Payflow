package com.payflow.payment.bo;

import com.payflow.payment.domain.Payment;
import com.payflow.payment.repository.PaymentRepository;
import com.payflow.user.domain.User;
import com.payflow.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    public PaymentBO(
            PaymentRepository paymentRepository,
            UserRepository userRepository
    ) {
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
    }

    /**
     * 결제 생성 (기본용)
     */
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
     * ✅ Checkout V2 준비 단계용 (지금은 로그/검증용만 사용)
     */
    public void preparePayment(
            String paymentId,
            String orderId,
            String orderName,
            int amount,
            Map<String, Object> deviceInfo
    ) {
        log.info("Checkout V2 결제 준비 - paymentId={}, orderId={}, orderName={}, amount={}, deviceInfo={}",
                paymentId, orderId, orderName, amount, deviceInfo);

        // 필요하면 여기서 orderId 기준으로 Payment 엔티티를 생성/업데이트하는 로직 추가 가능
        // (user 정보, method 등을 받지 못하니 지금은 DB에 손대지 않고 로그만 찍도록 둠)
    }

    /**
     * ✅ Checkout V2 결제 성공 처리 (공통 로직)
     */
    @Transactional
    public void updatePaymentSuccess(String orderId, String paymentId, String transactionId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Payment not found for orderId: " + orderId));

        // 이미 처리된 경우 다시 건들지 않음
        if (!"ready".equals(payment.getStatus())) {
            log.warn("이미 처리된 결제입니다 - orderId: {}, status: {}", orderId, payment.getStatus());
            return;
        }

        String tid = (transactionId != null && !transactionId.isBlank())
                ? transactionId
                : paymentId;

        payment.setPgTid(tid);
        payment.setPgResponse("SUCCESS");
        payment.updateStatus("paid");

        paymentRepository.save(payment);

        log.info("Checkout V2 결제 성공 처리 완료 - orderId={}, paymentId={}, tid={}",
                orderId, paymentId, tid);
    }

    /**
     * ✅ Checkout V2 결제 실패 처리 (공통 로직)
     */
    @Transactional
    public void updatePaymentFail(String orderId, String message) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Payment not found for orderId: " + orderId));

        if (!"ready".equals(payment.getStatus())) {
            log.warn("이미 처리된 결제입니다(실패 처리 시도) - orderId: {}, status: {}", orderId, payment.getStatus());
            return;
        }

        payment.setPgResponse("FAIL: " + message);
        payment.updateStatus("failed");

        paymentRepository.save(payment);

        log.info("Checkout V2 결제 실패 처리 완료 - orderId={}, reason={}", orderId, message);
    }

    /**
     * (남겨둔 기존 콜백 처리 – 필요하면 다른 PG 흐름에서 사용)
     */
    @Transactional
    public Payment processPaymentCallback(Map<String, String> responseData) {
        String orderId = responseData.get("orderId");
        String paymentId = responseData.get("paymentId");

        if (orderId == null) {
            throw new RuntimeException("orderId가 없습니다.");
        }

        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Payment not found for orderId: " + orderId));

        // 이미 성공 처리된 경우 방지
        if (!"ready".equals(payment.getStatus())) {
            log.warn("이미 처리된 결제입니다 - orderId: {}, status: {}", orderId, payment.getStatus());
            return payment;
        }

        // Checkout V2에서는 success면 바로 완료 처리
        payment.updateStatus("paid");
        payment.setPgTid(paymentId != null ? paymentId : "CHECKOUT-V2");
        payment.setPgResponse("SUCCESS");

        paymentRepository.save(payment);

        log.info("Checkout V2 결제 완료 처리 - orderId: {}, paymentId: {}", orderId, paymentId);
        return payment;
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

    /**
     * 정렬된 결제 목록 조회
     * @param sortBy 정렬 기준: latest(최신순), oldest(오래된순), orderId(주문번호순), highAmount(고액순), lowAmount(저액순)
     */
    public List<Payment> getPaymentListSorted(String sortBy) {
        List<Payment> payments = paymentRepository.findAll();
        
        if (sortBy == null || sortBy.isEmpty()) {
            sortBy = "latest";
        }
        
        switch (sortBy) {
            case "oldest":
                payments.sort((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()));
                break;
            case "orderId":
                payments.sort((a, b) -> a.getOrderId().compareTo(b.getOrderId()));
                break;
            case "highAmount":
                payments.sort((a, b) -> Integer.compare(b.getAmount(), a.getAmount()));
                break;
            case "lowAmount":
                payments.sort((a, b) -> Integer.compare(a.getAmount(), b.getAmount()));
                break;
            case "latest":
            default:
                payments.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
                break;
        }
        
        return payments;
    }
}
