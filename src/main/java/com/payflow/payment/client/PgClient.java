package com.payflow.payment.client;

import com.payflow.payment.domain.Payment;

import java.util.Map;

/**
 * PG사 연동 인터페이스
 * 각 PG사별로 구현체를 만들어 사용
 */
public interface PgClient {
    
    /**
     * PG사 결제 페이지 URL 생성
     * @param payment 결제 정보
     * @return PG사 결제 페이지 URL
     */
    String generatePaymentUrl(Payment payment);
    
    /**
     * PG사 결제 요청 파라미터 생성 (form submit용)
     * @param payment 결제 정보
     * @return PG사에 전달할 파라미터 맵
     */
    Map<String, String> generatePaymentParams(Payment payment);
    
    /**
     * PG사 승인 결과 검증
     * @param responseData PG사로부터 받은 응답 데이터
     * @return 검증 결과 및 결제 정보
     */
    PgResponse verifyPayment(Map<String, String> responseData);
    
    /**
     * PG사 이름 반환
     */
    String getPgName();
}







