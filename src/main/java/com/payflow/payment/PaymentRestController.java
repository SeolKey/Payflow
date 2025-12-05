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
            log.info("========================================");
            log.info("=== /api/pay/prepare 요청 수신 ===");
            log.info("========================================");
            log.info("요청 전체: {}", request);
            log.info("요청 키 목록: {}", request.keySet());
            
            String paymentId = (String) request.get("paymentId");  // 클라이언트에서 생성한 paymentId
            String orderId = (String) request.get("orderId");
            String orderName = (String) request.get("orderName");  // 필수: 주문명
            Integer amount = (Integer) request.get("amount");
            @SuppressWarnings("unchecked")
            Map<String, Object> deviceInfo = (Map<String, Object>) request.get("deviceInfo");  // 클라이언트에서 전달한 deviceInfo

            if (paymentId == null || orderId == null || orderName == null || amount == null) {
                log.error("❌ 필수 파라미터 누락 - paymentId: {}, orderId: {}, orderName: {}, amount: {}", 
                        paymentId, orderId, orderName, amount);
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "필수 파라미터가 누락되었습니다. (paymentId, orderId, orderName, amount)"
                ));
            }

            log.info("PortOne 결제 준비 요청 - paymentId: {}, orderId: {}, orderName: {}, amount: {}, deviceInfo: {}", 
                    paymentId, orderId, orderName, amount, deviceInfo);
            
            // deviceInfo 상세 확인
            if (deviceInfo == null) {
                log.warn("⚠️ deviceInfo가 null입니다. PortOneClient에서 기본값을 생성합니다.");
                // deviceInfo가 null이면 빈 Map으로 전달 (PortOneClient에서 기본값 생성)
                deviceInfo = new java.util.HashMap<>();
            } else {
                log.info("deviceInfo 상세: {}", deviceInfo);
                log.info("deviceInfo 키 목록: {}", deviceInfo.keySet());
                log.info("deviceInfo.platform: {}", deviceInfo.get("platform"));
                log.info("deviceInfo.platformType: {}", deviceInfo.get("platformType"));
                log.info("deviceInfo.ip: {}", deviceInfo.get("ip"));
                log.info("deviceInfo.ipAddress: {}", deviceInfo.get("ipAddress"));
            }

            // deviceInfo가 비어있거나 필수 필드가 없으면 기본값 생성
            if (deviceInfo.isEmpty() || (!deviceInfo.containsKey("platform") && !deviceInfo.containsKey("platformType"))) {
                log.warn("⚠️ deviceInfo가 비어있거나 필수 필드가 없습니다. 기본값을 생성합니다.");
                deviceInfo.put("platform", "PC");
                deviceInfo.put("ipAddress", "127.0.0.1");
            }

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
