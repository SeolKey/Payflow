package com.payflow.payment.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * PG사 응답 결과 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PgResponse {
    private boolean success;
    private String status;  // completed, failed, canceled
    private String pgTid;  // PG사 거래번호
    private String message;  // 응답 메시지
    private String rawResponse;  // 원본 응답 데이터 (JSON)
    
    public static PgResponse success(String pgTid, String rawResponse) {
        return PgResponse.builder()
                .success(true)
                .status("completed")
                .pgTid(pgTid)
                .rawResponse(rawResponse)
                .build();
    }
    
    public static PgResponse failure(String message, String rawResponse) {
        return PgResponse.builder()
                .success(false)
                .status("failed")
                .message(message)
                .rawResponse(rawResponse)
                .build();
    }
}
