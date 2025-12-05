package com.payflow.payment;

import com.payflow.payment.bo.PaymentBO;
import com.payflow.payment.domain.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/pay")
@RequiredArgsConstructor
public class PaymentRestController {

    private final PaymentBO paymentBO;

    @PostMapping("/create")
    public Map<String, Object> createPayment(
            @RequestParam Long userId,
            @RequestParam int amount,
            @RequestParam String method
    ) {

        Payment payment = paymentBO.createPayment(userId, amount, method);

        return Map.of(
                "success", true,
                "paymentId", payment.getId(),
                "redirectUrl", "/pay/result?id=" + payment.getId()
        );
    }
}
