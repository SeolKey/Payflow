package com.payflow.payment;

import com.payflow.payment.bo.PaymentBO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentBO paymentBO;

    @GetMapping("/pay/new")
    public String payForm() {
        return "create-payment"; // templates/create-payment.html
    }

    @GetMapping("/pay/result")
    public String payResult() {
        return "payment-result";
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
