package com.payflow.payment;

import com.payflow.payment.bo.PaymentBO;
import com.payflow.payment.domain.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@Controller
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentBO paymentBO;

    @GetMapping("/pay/new")
    public String payForm() {
        return "create-payment";
    }

    /**
     * PG사 결제 페이지로 리다이렉트하는 중간 페이지
     * form submit을 통해 PG사로 전송
     */
    @GetMapping("/pay/redirect/{id}")
    public String redirectToPg(@PathVariable Long id, Model model) {
        Payment payment = paymentBO.getPayment(id)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        // PG사 결제 파라미터 가져오기
        Map<String, String> pgParams = paymentBO.getPaymentParams(payment);
        String pgUrl = paymentBO.getPaymentUrl(payment);

        model.addAttribute("pgUrl", pgUrl);
        model.addAttribute("pgParams", pgParams);

        return "redirect-pg";  // templates/redirect-pg.html
    }

    @GetMapping("/pay/result")
    public String payResult(
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String orderId,
            Model model
    ) {
        if (id != null) {
            var payment = paymentBO.getPayment(id)
                    .orElseThrow(() -> new RuntimeException("Payment not found"));
            model.addAttribute("payment", payment);
        } else if (orderId != null) {
            // orderId로 결제 정보 조회
            var payment = paymentBO.getPaymentByOrderId(orderId)
                    .orElseThrow(() -> new RuntimeException("Payment not found"));
            model.addAttribute("payment", payment);
        }
        return "payment-result";
    }

    @GetMapping("/pay/success")
    public String paymentSuccess(
            @RequestParam(required = false) String imp_uid,  // PortOne paymentId
            @RequestParam(required = false) String merchant_uid,  // orderId
            @RequestParam(required = false) String orderId  // fallback
    ) {
        // PortOne에서 successUrl로 리다이렉트될 때 imp_uid와 merchant_uid를 쿼리 파라미터로 전달
        // merchant_uid가 orderId입니다
        String actualOrderId = merchant_uid != null ? merchant_uid : orderId;
        
        if (actualOrderId == null) {
            return "redirect:/pay/fail?error=주문번호가 없습니다.";
        }

        // 결제 승인 처리 (서버에서 PortOne API 호출)
        try {
            // PaymentBO를 통해 결제 승인 처리
            // imp_uid가 있으면 결제 승인 API 호출
            if (imp_uid != null) {
                // Payment 정보 조회
                var paymentOpt = paymentBO.getPaymentByOrderId(actualOrderId);
                if (paymentOpt.isPresent()) {
                    var payment = paymentOpt.get();
                    
                    // 결제 승인 처리
                    java.util.Map<String, String> responseData = new java.util.HashMap<>();
                    responseData.put("paymentId", imp_uid);
                    responseData.put("orderId", actualOrderId);
                    responseData.put("amount", String.valueOf(payment.getAmount()));
                    
                    paymentBO.processPaymentCallback(responseData);
                }
            }
            
            return "redirect:/pay/result?orderId=" + actualOrderId;
        } catch (Exception e) {
            return "redirect:/pay/fail?error=" + e.getMessage();
        }
    }

    @GetMapping("/pay/fail")
    public String paymentFail(@RequestParam(required = false) String error, Model model) {
        if (error != null) {
            model.addAttribute("error", error);
        }
        return "payment-fail";
    }

    @GetMapping("/pay/detail/{id}")
    public String paymentDetail(@PathVariable Long id, Model model) {
        var payment = paymentBO.getPayment(id)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        model.addAttribute("payment", payment);
        return "detail-payment";
    }

    @GetMapping("/pay/list")
    public String paymentList(Model model) {
        model.addAttribute("payments", paymentBO.getPaymentList());
        return "list-payment";
    }
}
