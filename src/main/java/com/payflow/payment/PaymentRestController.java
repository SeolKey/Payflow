package com.payflow.payment;

import com.payflow.payment.bo.PaymentBO;
import com.payflow.payment.domain.Payment;
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
    private final com.payflow.payment.client.PortOneClient portOneClient;

    @PostMapping("/create")
    public Map<String, Object> createPayment(
            @RequestParam Long userId,
            @RequestParam int amount,
            @RequestParam String method
    ) {

        Payment payment = paymentBO.createPayment(userId, amount, method);

        // PG사 결제 파라미터 생성
        Map<String, String> pgParams = paymentBO.getPaymentParams(payment);

        return Map.of(
                "success", true,
                "paymentId", payment.getId(),
                "orderId", payment.getOrderId(),
                "pgParams", pgParams,
                "redirectUrl", "/pay/redirect/" + payment.getId()
        );
    }

    /**
     * PG사 Return URL 콜백 처리
     * POST /api/pay/callback
     */
    @PostMapping("/callback")
    public ResponseEntity<Map<String, Object>> handlePaymentCallback(
            @RequestParam Map<String, String> params
    ) {
        try {
            log.info("PG사 콜백 수신: {}", params);

            Payment payment = paymentBO.processPaymentCallback(params);

            // 성공/실패 페이지로 리다이렉트
            String redirectUrl = "/pay/result?id=" + payment.getId();
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "paymentId", payment.getId(),
                    "status", payment.getStatus(),
                    "redirectUrl", redirectUrl
            ));

        } catch (Exception e) {
            log.error("PG사 콜백 처리 중 오류 발생", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * GET 방식 콜백도 지원 (일부 PG사는 GET으로 전달)
     */
    @GetMapping("/callback")
    public String handlePaymentCallbackGet(
            @RequestParam Map<String, String> params
    ) {
        try {
            log.info("PG사 콜백 수신 (GET): {}", params);

            Payment payment = paymentBO.processPaymentCallback(params);

            // 성공/실패 페이지로 리다이렉트
            return "redirect:/pay/result?id=" + payment.getId();

        } catch (Exception e) {
            log.error("PG사 콜백 처리 중 오류 발생", e);
            return "redirect:/pay/fail?error=" + e.getMessage();
        }
    }

    /**
     * PortOne V2 결제 준비 API
     * POST /api/pay/prepare
     */
    @PostMapping("/prepare")
    public ResponseEntity<Map<String, Object>> preparePayment(
            @RequestBody Map<String, Object> request
    ) {
        try {
            String paymentId = (String) request.get("paymentId");  // 클라이언트에서 생성한 paymentId
            String orderId = (String) request.get("orderId");
            String orderName = (String) request.get("orderName");  // 필수: 주문명
            Integer amount = (Integer) request.get("amount");
            @SuppressWarnings("unchecked")
            Map<String, Object> deviceInfo = (Map<String, Object>) request.get("deviceInfo");  // 클라이언트에서 전달한 deviceInfo

            if (paymentId == null || orderId == null || orderName == null || amount == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "필수 파라미터가 누락되었습니다. (paymentId, orderId, orderName, amount)"
                ));
            }

            log.info("PortOne 결제 준비 요청 - paymentId: {}, orderId: {}, orderName: {}, amount: {}, deviceInfo: {}", 
                    paymentId, orderId, orderName, amount, deviceInfo);

            // PortOneClient를 통해 결제 준비 (paymentId, orderName, deviceInfo 포함)
            portOneClient.preparePayment(paymentId, orderId, orderName, amount, deviceInfo);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "paymentId", paymentId,
                    "orderId", orderId
            ));

        } catch (Exception e) {
            log.error("결제 준비 처리 중 오류 발생", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * PortOne 결제 승인 API
     * POST /api/pay/confirm
     */
    @PostMapping("/confirm")
    public ResponseEntity<Map<String, Object>> confirmPayment(
            @RequestBody Map<String, Object> request
    ) {
        try {
            String paymentId = (String) request.get("paymentId");
            String orderId = (String) request.get("orderId");
            Integer amount = (Integer) request.get("amount");

            if (paymentId == null || orderId == null || amount == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "필수 파라미터가 누락되었습니다."
                ));
            }

            log.info("PortOne 결제 승인 요청 - paymentId: {}, orderId: {}, amount: {}", 
                    paymentId, orderId, amount);

            // 결제 승인 처리
            Map<String, String> responseData = new java.util.HashMap<>();
            responseData.put("paymentId", paymentId);
            responseData.put("orderId", orderId);
            responseData.put("amount", String.valueOf(amount));

            Payment payment = paymentBO.processPaymentCallback(responseData);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "paymentId", payment.getId(),
                    "status", payment.getStatus(),
                    "redirectUrl", "/pay/result?id=" + payment.getId()
            ));

        } catch (Exception e) {
            log.error("결제 승인 처리 중 오류 발생", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }
}
