package com.payflow.payment.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.payment.domain.Payment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mock PG 클라이언트 (테스트용)
 * 실제 PG사 연동 전 개발/테스트용으로 사용
 */
@Component("mockPgClient")
@Slf4j
public class MockPgClient implements PgClient {

    @Value("${pg.mock.url:http://localhost:80/pg/mock}")
    private String mockPgUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String generatePaymentUrl(Payment payment) {
        // URL에는 쿼리 파라미터를 포함하지 않고 base URL만 반환
        // 모든 파라미터는 form의 hidden input으로 전달됨
        return mockPgUrl;
    }

    @Override
    public Map<String, String> generatePaymentParams(Payment payment) {
        Map<String, String> params = new HashMap<>();
        params.put("orderId", payment.getOrderId());
        params.put("amount", String.valueOf(payment.getAmount()));
        params.put("method", payment.getMethod());
        params.put("returnUrl", "http://localhost:80/api/pay/callback");
        return params;
    }

    @Override
    public PgResponse verifyPayment(Map<String, String> responseData) {
        try {
            String result = responseData.get("result");
            String orderId = responseData.get("orderId");
            String pgTid = responseData.getOrDefault("pgTid", UUID.randomUUID().toString());
            
            String rawResponse = objectMapper.writeValueAsString(responseData);
            
            if ("success".equals(result)) {
                log.info("Mock PG 결제 성공 - orderId: {}, pgTid: {}", orderId, pgTid);
                return PgResponse.success(pgTid, rawResponse);
            } else {
                String message = responseData.getOrDefault("message", "결제 실패");
                log.warn("Mock PG 결제 실패 - orderId: {}, message: {}", orderId, message);
                return PgResponse.failure(message, rawResponse);
            }
        } catch (Exception e) {
            log.error("Mock PG 응답 검증 실패", e);
            return PgResponse.failure("응답 검증 실패: " + e.getMessage(), "");
        }
    }

    @Override
    public String getPgName() {
        return "MOCK";
    }
}
