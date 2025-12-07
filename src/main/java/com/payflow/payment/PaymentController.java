package com.payflow.payment;

import com.payflow.payment.bo.PaymentBO;
import com.payflow.payment.client.PortOneClient;
import com.payflow.payment.domain.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    private final PaymentBO paymentBO;
    private final PortOneClient portOneClient;

    @GetMapping("/pay/new")
    public String payForm() {
        return "create-payment";
    }

    /**
     * ⭐ PortOne Checkout V2 결제창 띄우는 엔드포인트
     */
    @GetMapping("/pay/request/{orderId}")
    public String requestPayment(
            @PathVariable String orderId,
            Model model
    ) {
        Payment payment = paymentBO.getPaymentByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        // method → PortOne ENUM 매핑
        String rawMethod = payment.getMethod();
        String payMethod;

        if (rawMethod == null) payMethod = "CARD";
        else {
            switch (rawMethod.toLowerCase()) {
                case "card": payMethod = "CARD";   break;
                case "vbank": payMethod = "VBANK"; break;
                default: payMethod = "CARD";
            }
        }

        // 🟡 Checkout V2는 channelKey 만으로 PG 자동 선택됨
        Map<String, Object> pgParams = new HashMap<>();
        pgParams.put("storeId", "store-fd43ffed-f666-488a-9e41-82609944ff14");
        pgParams.put("channelKey", "channel-key-cab2ba46-5d95-4455-9533-30a1337e3de2");

        // 주문 정보
        pgParams.put("orderId", payment.getOrderId());
        pgParams.put("amount", payment.getAmount());
        pgParams.put("orderName", "PayFlow 결제");
        pgParams.put("payMethod", payMethod);

        // 🟡 INICIS 일반결제 필수 buyer 정보
        pgParams.put("buyerEmail", "test@example.com");
        pgParams.put("buyerName", "테스트사용자");
        pgParams.put("buyerPhone", "01012345678");

        // callback URL
        pgParams.put("successUrl", "http://localhost/pay/success?orderId=" + payment.getOrderId());
        pgParams.put("failUrl", "http://localhost/pay/fail?orderId=" + payment.getOrderId());

        log.info("📦 PG 파라미터 전달됨: {}", pgParams);

        model.addAttribute("pgParams", pgParams);
        return "payment-request";
    }

    @GetMapping("/pay/result")
    public String payResult(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String orderId,
            Model model
    ) {
        if (id != null) model.addAttribute("payment", paymentBO.getPayment(id).orElseThrow());
        else if (orderId != null) model.addAttribute("payment", paymentBO.getPaymentByOrderId(orderId).orElseThrow());
        return "payment-result";
    }

    @GetMapping("/pay/success")
    public String paymentSuccess(
            @RequestParam String paymentId,
            @RequestParam String orderId
    ) {
        log.info("💰 결제 성공 콜백 수신 - paymentId={}, orderId={}", paymentId, orderId);

        try {
            paymentBO.updatePaymentSuccess(orderId, paymentId, null);
            return "redirect:/pay/result?orderId=" + orderId;

        } catch (Exception e) {
            log.error("❌ 결제 승인 처리 실패: {}", e.getMessage());
            return "redirect:/pay/fail?error=" + e.getMessage();
        }
    }

    @GetMapping("/pay/fail")
    public String paymentFail(
            @RequestParam(required = false) String error,
            Model model
    ) {
        if (error != null) model.addAttribute("error", error);
        return "payment-fail";
    }

    @GetMapping("/pay/list")
    public String payList(Model model) {
        model.addAttribute("payments", paymentBO.getPaymentList());
        return "list-payment"; // list-payment.html
    }

    @GetMapping("/pay/detail/{orderId}")
    public String payDetail(@PathVariable String orderId,
                            Model model) {

        Payment payment = paymentBO.getPaymentByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Payment not found: " + orderId));

        model.addAttribute("payment", payment);  // 🔥 핵심 수정
        return "detail-payment";
    }
}
