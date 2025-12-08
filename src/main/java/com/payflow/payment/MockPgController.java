package com.payflow.payment;

import com.payflow.payment.bo.PaymentBO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mock PG 서버 컨트롤러 (테스트용)
 * 실제 PG사 대신 결제 처리를 시뮬레이션
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class MockPgController {

    private final PaymentBO paymentBO;

    @RequestMapping(value = "/pg/mock", method = {RequestMethod.GET, RequestMethod.POST})
    public String mockPgPage(
            @RequestParam(required = false) String orderId,
            @RequestParam(required = false) String amount,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String returnUrl,
            Model model
    ) {
        log.info("Mock PG 페이지 접근 - orderId: {}, amount: {}, method: {}", orderId, amount, method);
        
        // orderId가 중복되어 전달될 수 있으므로 첫 번째 값만 사용
        if (orderId != null && orderId.contains(",")) {
            orderId = orderId.split(",")[0];
        }
        
        model.addAttribute("orderId", orderId);
        model.addAttribute("amount", amount);
        model.addAttribute("method", method);
        model.addAttribute("returnUrl", returnUrl != null ? returnUrl : "http://localhost:80/api/pay/callback");
        
        return "payment/mock-pg";
    }

    @PostMapping("/pg/mock/process")
    public String processPayment(
            @RequestParam String orderId,
            @RequestParam String amount,
            @RequestParam String method,
            @RequestParam String returnUrl,
            @RequestParam String action  // success 또는 fail
    ) {
        // orderId가 중복되어 전달될 수 있으므로 첫 번째 값만 사용
        if (orderId != null && orderId.contains(",")) {
            orderId = orderId.split(",")[0];
        }
        
        log.info("Mock PG 결제 처리 - orderId: {}, action: {}", orderId, action);
        
        try {
            // 결제 결과 데이터 생성
            Map<String, String> responseData = new HashMap<>();
            responseData.put("orderId", orderId);
            responseData.put("amount", amount);
            responseData.put("result", action);
            responseData.put("pgTid", UUID.randomUUID().toString());
            responseData.put("message", action.equals("success") ? "결제성공" : "결제실패");
            
            // PaymentBO를 통해 결제 처리
            var payment = paymentBO.processPaymentCallback(responseData);
            
            // 성공/실패에 따라 결과 페이지로 리다이렉트
            if ("success".equals(action)) {
                return "redirect:/pay/result?id=" + payment.getId();
            } else {
                return "redirect:/pay/fail?error=" + responseData.get("message");
            }
            
        } catch (Exception e) {
            log.error("Mock PG 결제 처리 중 오류 발생", e);
            return "redirect:/pay/fail?error=" + e.getMessage();
        }
    }
}

