package com.payflow.payment;

import com.payflow.payment.bo.PaymentBO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/pay")
@RequiredArgsConstructor
@Slf4j
public class PaymentRestController {

    private final PaymentBO paymentBO;

    /** ✅ 결제 생성 API (HTML에서 FormData로 호출함) */
    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createPayment(
            @RequestParam Long userId,
            @RequestParam int amount,
            @RequestParam String method
    ) {

        var payment = paymentBO.createPayment(userId, amount, method);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "paymentId", payment.getId(),
                "orderId", payment.getOrderId(),
                "redirectUrl", "/pay/request/" + payment.getOrderId()
        ));
    }

    /** ========== 아래는 네가 준 원본 그대로 유지 ========= */

    @PostMapping("/prepare")
    public ResponseEntity<Map<String, Object>> preparePayment(
            @RequestBody Map<String, Object> request
    ) {
        log.info("=== /api/pay/prepare 요청 === {}", request);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/success")
    public ResponseEntity<Map<String, Object>> successCallback(
            @RequestParam String paymentId,
            @RequestParam String orderId
    ) {
        log.info("🎉 결제 성공: {}, {}", paymentId, orderId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @GetMapping("/fail")
    public ResponseEntity<Map<String, Object>> failCallback(
            @RequestParam String orderId,
            @RequestParam String message
    ) {
        log.warn("🔥 결제 실패: {}", orderId);
        return ResponseEntity.ok(Map.of("success", false));
    }
}
