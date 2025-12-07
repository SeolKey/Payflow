package com.payflow.payment.client;

import com.payflow.payment.domain.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component("portOneClient")
@RequiredArgsConstructor
public class PortOneClient implements PgClient {

    @Override
    public String generatePaymentUrl(Payment payment) {
        // Checkout V2에서는 사용하지 않음
        return null;
    }

    @Override
    public Map<String, String> generatePaymentParams(Payment payment) {
        // Checkout V2에서는 사용하지 않음
        return Map.of();
    }

    @Override
    public PgResponse verifyPayment(Map<String, String> responseData) {
        // confirmPayment 기능을 쓰지 않는다면 verify도 의미 없음
        return PgResponse.success(
                responseData.get("paymentId"),
                "Checkout V2에서는 서버 검증을 사용하지 않습니다."
        );
    }

    @Override
    public String getPgName() {
        return "PORTONE";
    }
}
