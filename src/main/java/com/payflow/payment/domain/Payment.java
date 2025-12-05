package com.payflow.payment.domain;

import com.payflow.user.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "order_id")
    private String orderId;

    @Column(name = "pg_tid")
    private String pgTid;  // PG사 거래번호 (Transaction ID)

    @Column(name = "pg_response")
    private String pgResponse;  // PG사 응답 데이터 (JSON)

    private int amount;

    private String status;

    private String method;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 상태 업데이트 메서드
    public void updateStatus(String status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public void setPgTid(String pgTid) {
        this.pgTid = pgTid;
    }

    public void setPgResponse(String pgResponse) {
        this.pgResponse = pgResponse;
    }
}
