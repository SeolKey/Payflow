package com.payflow.payment.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.payment.domain.Payment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * PortOne (KG이니시스) PG 클라이언트
 * 실제 결제 처리를 위한 구현체
 */
@Component("portOneClient")
@Slf4j
public class PortOneClient implements PgClient {

    @Value("${pg.portone.secret-key:}")
    private String secretKey;

    @Value("${pg.portone.store-id}")
    private String storeId;

    @Value("${pg.portone.channel-key:}")
    private String channelKey;

    @Value("${pg.portone.api-url:https://api.portone.io}")
    private String apiUrl;

    @Value("${pg.portone.success-url:http://localhost:80/pay/success}")
    private String successUrl;

    @Value("${pg.portone.fail-url:http://localhost:80/pay/fail}")
    private String failUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public PortOneClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String generatePaymentUrl(Payment payment) {
        // PortOne은 JavaScript SDK를 사용하므로 URL이 필요 없음
        return null;
    }

    @Override
    public Map<String, String> generatePaymentParams(Payment payment) {
        Map<String, String> params = new HashMap<>();
        params.put("storeId", storeId);  // Store ID (V2)
        params.put("orderId", payment.getOrderId());
        params.put("amount", String.valueOf(payment.getAmount()));
        params.put("orderName", "PayFlow 결제");
        params.put("successUrl", successUrl + "?orderId=" + payment.getOrderId());
        params.put("failUrl", failUrl + "?orderId=" + payment.getOrderId());
        return params;
    }

    /**
     * PortOne V2 결제 준비 API 호출
     * 클라이언트에서 생성한 paymentId를 포함하여 결제를 준비함
     */
    public void preparePayment(String paymentId, String orderId, String orderName, int amount, Map<String, Object> deviceInfo) {
        try {
            // PortOne V2 결제 준비 API 엔드포인트
            String url = "https://checkout-service.prod.iamport.co/api/prepare/v2";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // PortOne V2 인증: Bearer {API Secret}
            headers.set("Authorization", "Bearer " + secretKey);
            
            log.info("PortOne 결제 준비 API 호출 - paymentId: {}, orderId: {}, orderName: {}, amount: {}, deviceInfo: {}", 
                    paymentId, orderId, orderName, amount, deviceInfo);

            // 요청 바디 (PortOne V2는 data 래퍼 없이 최상위 레벨에 직접 필드 전달)
            Map<String, Object> body = new HashMap<>();
            body.put("paymentId", paymentId);  // 필수: 클라이언트에서 생성한 paymentId
            body.put("orderName", orderName);  // 필수: 주문명
            body.put("totalAmount", amount);  // 필수: 결제 금액 (totalAmount로 전달)
            
            // channelKey 또는 pgProvider 중 하나 필수
            if (channelKey != null && !channelKey.isEmpty()) {
                body.put("channelKey", channelKey);  // Channel Key 사용 (권장)
            } else {
                body.put("pgProvider", "html5_inicis");  // pgProvider 사용 (대체)
            }
            
            body.put("storeId", storeId);
            body.put("orderId", orderId);
            body.put("currency", "KRW");
            
            // deviceInfo 필수 (클라이언트에서 전달받거나 기본값 사용)
            // PortOne V2는 Map<String, String> 타입을 요구하므로 명시적으로 생성
            Map<String, String> deviceInfoToSend = new HashMap<>();
            
            log.info("받은 deviceInfo (원본): {}", deviceInfo);
            log.info("deviceInfo null 여부: {}, empty 여부: {}", deviceInfo == null, deviceInfo != null && deviceInfo.isEmpty());
            
            // platform 값 추출 (PortOne V2는 platformType 필드를 요구함)
            // PortOne V2는 "PC" 또는 "MOBILE" 값만 유효함 ("WEB"은 유효하지 않음!)
            String platformTypeValue = "PC";  // 기본값 (PC 웹 브라우저)
            if (deviceInfo != null && !deviceInfo.isEmpty()) {
                log.info("클라이언트에서 받은 deviceInfo 사용");
                
                // platform 또는 platformType 모두 확인
                if (deviceInfo.containsKey("platform")) {
                    Object platformObj = deviceInfo.get("platform");
                    String platformStr = platformObj != null ? platformObj.toString().toUpperCase() : "PC";
                    // "WEB"을 "PC"로 변환 (PortOne V2는 "PC" 사용)
                    platformTypeValue = "WEB".equalsIgnoreCase(platformStr) ? "PC" : platformStr;
                    // "PC"가 아니고 "MOBILE"도 아니면 "PC"로 설정
                    if (!"PC".equalsIgnoreCase(platformTypeValue) && !"MOBILE".equalsIgnoreCase(platformTypeValue)) {
                        platformTypeValue = "PC";
                    }
                } else if (deviceInfo.containsKey("platformType")) {
                    Object platformTypeObj = deviceInfo.get("platformType");
                    String platformTypeStr = platformTypeObj != null ? platformTypeObj.toString().toUpperCase() : "PC";
                    // "WEB"을 "PC"로 변환 (PortOne V2는 "PC" 사용)
                    platformTypeValue = "WEB".equalsIgnoreCase(platformTypeStr) ? "PC" : platformTypeStr;
                    // "PC"가 아니고 "MOBILE"도 아니면 "PC"로 설정
                    if (!"PC".equalsIgnoreCase(platformTypeValue) && !"MOBILE".equalsIgnoreCase(platformTypeValue)) {
                        platformTypeValue = "PC";
                    }
                }
                
                // ip 값 추출 (PortOne V2는 ip 필드를 요구함, ipAddress 아님)
                String ipValue = null;
                if (deviceInfo.containsKey("ipAddress")) {
                    Object ipAddressObj = deviceInfo.get("ipAddress");
                    if (ipAddressObj != null) {
                        String ipAddressStr = ipAddressObj.toString();
                        ipValue = ipAddressStr.isEmpty() ? null : ipAddressStr;
                    }
                } else if (deviceInfo.containsKey("ip")) {
                    Object ipObj = deviceInfo.get("ip");
                    if (ipObj != null) {
                        String ipStr = ipObj.toString();
                        ipValue = ipStr.isEmpty() ? null : ipStr;
                    }
                }
                
                // PortOne V2는 platformType 필드를 요구함 (platform이 아님!)
                deviceInfoToSend.put("platformType", platformTypeValue);
                if (ipValue != null) {
                    deviceInfoToSend.put("ip", ipValue);
                } else {
                    deviceInfoToSend.put("ip", "127.0.0.1");  // 기본값
                }
            } else {
                // 기본값 (PC 웹 브라우저용)
                log.info("기본 deviceInfo 사용");
                // PortOne V2는 platformType 필드를 요구하며, 유효값은 "PC" 또는 "MOBILE"
                deviceInfoToSend.put("platformType", "PC");
                deviceInfoToSend.put("ip", "127.0.0.1");
            }
            
            // deviceInfo가 비어있지 않은지 확인
            if (deviceInfoToSend.isEmpty()) {
                log.error("❌ deviceInfoToSend가 비어있습니다! 기본값으로 강제 생성합니다.");
                deviceInfoToSend.put("platformType", "PC");
                deviceInfoToSend.put("ip", "127.0.0.1");
            }
            
            // platformType이 반드시 있어야 함
            if (!deviceInfoToSend.containsKey("platformType") || deviceInfoToSend.get("platformType") == null) {
                log.error("❌ deviceInfo.platformType이 없습니다! 기본값으로 설정합니다.");
                deviceInfoToSend.put("platformType", "PC");
            }
            
            // platform 필드가 있으면 제거 (platformType만 사용)
            if (deviceInfoToSend.containsKey("platform")) {
                log.warn("⚠️ deviceInfo에서 platform 필드를 제거합니다. (platformType만 사용)");
                deviceInfoToSend.remove("platform");
            }
            
            // ip가 반드시 있어야 함
            if (!deviceInfoToSend.containsKey("ip") || deviceInfoToSend.get("ip") == null) {
                log.error("❌ deviceInfo.ip이 없습니다! 기본값으로 설정합니다.");
                deviceInfoToSend.put("ip", "127.0.0.1");
            }
            
            log.info("전송할 deviceInfo: {}", deviceInfoToSend);
            log.info("deviceInfoToSend 크기: {}", deviceInfoToSend.size());
            log.info("deviceInfoToSend.platformType: {}", deviceInfoToSend.get("platformType"));
            log.info("deviceInfoToSend.ip: {}", deviceInfoToSend.get("ip"));
            
            // deviceInfo를 body에 직접 추가 (data 래퍼 없이)
            body.put("deviceInfo", deviceInfoToSend);
            log.info("✅ body에 deviceInfo 추가 완료: {}", body.get("deviceInfo"));
            
            // body에 deviceInfo가 제대로 추가되었는지 확인
            log.info("body에 deviceInfo 추가 후 확인: {}", body.containsKey("deviceInfo"));
            log.info("body.deviceInfo 값: {}", body.get("deviceInfo"));
            
            // 최종 body 필드 확인
            log.info("최종 body 필드 키 목록: {}", body.keySet());
            log.info("최종 body에 deviceInfo 포함 여부: {}", body.containsKey("deviceInfo"));
            
            // 요청 바디 생성 직전에 deviceInfo가 body에 포함되어 있는지 최종 확인 및 강제 추가
            if (!body.containsKey("deviceInfo") || body.get("deviceInfo") == null) {
                log.error("❌ 요청 바디 생성 직전에 body에 deviceInfo가 없거나 null입니다! 강제로 추가합니다.");
                body.put("deviceInfo", deviceInfoToSend);
            }
            
            // deviceInfo 값이 올바른지 확인
            Object deviceInfoInBody = body.get("deviceInfo");
            if (deviceInfoInBody == null) {
                log.error("❌ body.deviceInfo가 null입니다! 다시 설정합니다.");
                body.put("deviceInfo", deviceInfoToSend);
            } else {
                log.info("✅ body.deviceInfo 확인됨: {}", deviceInfoInBody);
            }
            
            // PortOne V2 API는 data 래퍼를 요구함
            // data 래퍼에는 결제 정보만 포함하고, deviceInfo는 최상위 레벨에 별도로 추가
            Map<String, Object> dataWrapper = new HashMap<>();
            
            // 필수 필드들을 명시적으로 추가 (deviceInfo는 data 래퍼 외부에 위치)
            dataWrapper.put("paymentId", paymentId);
            dataWrapper.put("orderName", orderName);
            dataWrapper.put("totalAmount", amount);
            dataWrapper.put("orderId", orderId);
            dataWrapper.put("storeId", storeId);
            dataWrapper.put("currency", "KRW");
            
            // channelKey 또는 pgProvider 추가
            if (channelKey != null && !channelKey.isEmpty()) {
                dataWrapper.put("channelKey", channelKey);
            } else {
                dataWrapper.put("pgProvider", "html5_inicis");
            }
            
            log.info("dataWrapper 키 목록 (deviceInfo 제외): {}", dataWrapper.keySet());
            
            // 최종 요청 바디 생성
            Map<String, Object> finalBody = new HashMap<>();
            
            // data 래퍼 추가
            finalBody.put("data", dataWrapper);
            
            // deviceInfo는 최상위 레벨에 추가 (data 래퍼 외부!)
            // PortOne V2 API는 deviceInfo를 최상위 레벨에서 찾음
            log.info("✅ deviceInfo를 최상위 레벨에 추가: {}", deviceInfoToSend);
            finalBody.put("deviceInfo", deviceInfoToSend);
            
            log.info("최종 finalBody 키 목록: {}", finalBody.keySet());
            log.info("finalBody에 data 포함: {}", finalBody.containsKey("data"));
            log.info("finalBody에 deviceInfo 포함: {}", finalBody.containsKey("deviceInfo"));
            
            // body를 finalBody로 교체
            body = finalBody;
            
            log.info("✅ data 래퍼로 감싼 최종 body: {}", body);
            
            // 요청 바디 JSON 출력 (디버깅용)
            try {
                String requestBodyJson = objectMapper.writeValueAsString(body);
                log.info("PortOne 결제 준비 요청 바디 (전체): {}", requestBodyJson);
                
                // deviceInfo가 포함되어 있는지 문자열 검색으로도 확인
                    if (requestBodyJson.contains("deviceInfo")) {
                        log.info("✅ 요청 바디 JSON에 deviceInfo 포함됨");
                        // deviceInfo 내용도 확인
                        if (requestBodyJson.contains("\"platformType\"")) {
                            log.info("✅ 요청 바디 JSON에 platformType 필드 포함됨");
                        } else {
                            log.error("❌ 요청 바디 JSON에 platformType 필드가 없습니다!");
                        }
                        if (requestBodyJson.contains("\"ip\"")) {
                            log.info("✅ 요청 바디 JSON에 ip 필드 포함됨");
                        } else {
                            log.error("❌ 요청 바디 JSON에 ip 필드가 없습니다!");
                        }
                        // platform 필드가 있으면 경고 (platformType을 사용해야 함)
                        if (requestBodyJson.contains("\"platform\"") && !requestBodyJson.contains("\"platformType\"")) {
                            log.warn("⚠️ 요청 바디 JSON에 platform 필드가 포함되어 있습니다. platformType을 사용해야 합니다.");
                        }
                    } else {
                        log.error("❌ 요청 바디 JSON에 deviceInfo가 포함되지 않음! 다시 추가합니다.");
                        // deviceInfo는 최상위 레벨에 추가
                        body.put("deviceInfo", deviceInfoToSend);
                        requestBodyJson = objectMapper.writeValueAsString(body);
                        log.info("수정된 요청 바디: {}", requestBodyJson);
                    }
            } catch (Exception e) {
                log.error("요청 바디 로그 출력 실패", e);
            }

            // HttpEntity 생성 직전 최종 확인 및 상세 로그 출력
            log.info("========================================");
            log.info("=== PortOne API 최종 요청 바디 구조 ===");
            log.info("========================================");
            
            try {
                // 최종 요청 바디를 JSON으로 변환하여 출력
                String finalRequestBodyJson = objectMapper.writeValueAsString(body);
                log.info("최종 요청 바디 (JSON): {}", finalRequestBodyJson);
                
                // 포맷팅된 JSON으로도 출력 (가독성 향상)
                String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(body);
                log.info("최종 요청 바디 (포맷팅):\n{}", prettyJson);
                
                // 각 필드별 상세 확인
                log.info("--- 최종 요청 바디 필드 상세 ---");
                log.info("body 키 목록: {}", body.keySet());
                log.info("body 크기: {}", body.size());
                
                // data 래퍼 확인 (PortOne V2 API는 data 래퍼를 요구함)
                if (body.containsKey("data")) {
                    log.info("✅ body에 'data' 래퍼가 있습니다. (PortOne V2 요구사항)");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> dataWrapperFromBody = (Map<String, Object>) body.get("data");
                    if (dataWrapperFromBody != null) {
                        log.info("data 래퍼 내용: {}", dataWrapperFromBody);
                        log.info("data 래퍼 키 목록: {}", dataWrapperFromBody.keySet());
                        
                    }
                } else {
                    log.error("❌ body에 'data' 래퍼가 없습니다! PortOne V2 API는 data 래퍼를 요구합니다.");
                }
                
                // 최상위 레벨의 deviceInfo 확인 (PortOne V2는 deviceInfo를 최상위 레벨에서 찾음)
                if (body.containsKey("deviceInfo")) {
                    log.info("✅ 최상위 레벨에 deviceInfo 있음: {}", body.get("deviceInfo"));
                    
                    Object deviceInfoObj = body.get("deviceInfo");
                    if (deviceInfoObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> deviceInfoMap = (Map<String, Object>) deviceInfoObj;
                        log.info("deviceInfo 키 목록: {}", deviceInfoMap.keySet());
                        log.info("deviceInfo.platformType: {}", deviceInfoMap.get("platformType"));
                        log.info("deviceInfo.ip: {}", deviceInfoMap.get("ip"));
                        
                        if (deviceInfoMap.containsKey("ipAddress") && !deviceInfoMap.containsKey("ip")) {
                            log.error("❌ deviceInfo에 'ipAddress' 필드가 있지만 'ip' 필드가 없습니다! PortOne V2는 'ip' 필드를 요구합니다.");
                        }
                        if (!deviceInfoMap.containsKey("ip")) {
                            log.error("❌ deviceInfo에 'ip' 필드가 없습니다!");
                        }
                        if (!deviceInfoMap.containsKey("platformType")) {
                            log.error("❌ deviceInfo에 'platformType' 필드가 없습니다!");
                        }
                    }
                } else {
                    log.error("❌ 최상위 레벨에 deviceInfo가 없습니다! 강제로 추가합니다.");
                    body.put("deviceInfo", deviceInfoToSend);
                }
                
                // 필수 필드 확인 (data 래퍼 내부)
                log.info("--- 필수 필드 확인 (data 래퍼 내부) ---");
                @SuppressWarnings("unchecked")
                Map<String, Object> dataForCheck = (Map<String, Object>) body.get("data");
                if (dataForCheck != null) {
                    log.info("paymentId: {}", dataForCheck.get("paymentId"));
                    log.info("orderId: {}", dataForCheck.get("orderId"));
                    log.info("totalAmount: {}", dataForCheck.get("totalAmount"));
                    log.info("orderName: {}", dataForCheck.get("orderName"));
                    log.info("storeId: {}", dataForCheck.get("storeId"));
                    log.info("channelKey: {}", dataForCheck.get("channelKey"));
                    log.info("currency: {}", dataForCheck.get("currency"));
                }
                
            } catch (Exception e) {
                log.error("최종 요청 바디 로그 출력 실패", e);
            }
            
            log.info("========================================");
            
            // HttpEntity 생성 직전 최종 확인: 최상위 레벨에 deviceInfo가 반드시 있어야 함
            if (!body.containsKey("deviceInfo") || body.get("deviceInfo") == null) {
                log.error("❌ HttpEntity 생성 직전: 최상위 레벨에 deviceInfo가 없습니다! 강제로 추가합니다.");
                body.put("deviceInfo", deviceInfoToSend);
            } else {
                // deviceInfo가 있더라도 올바른 구조인지 확인
                Object deviceInfoFinal = body.get("deviceInfo");
                if (deviceInfoFinal instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> deviceInfoMapFinal = (Map<String, Object>) deviceInfoFinal;
                    log.info("✅ 최상위 레벨 deviceInfo 확인됨: {}", deviceInfoMapFinal);
                    
                    if (!deviceInfoMapFinal.containsKey("platformType") || !deviceInfoMapFinal.containsKey("ip")) {
                        log.error("❌ HttpEntity 생성 직전: deviceInfo에 필수 필드가 없습니다! 다시 설정합니다.");
                        body.put("deviceInfo", deviceInfoToSend);
                    }
                    // platform 필드가 있으면 platformType으로 변경
                    if (deviceInfoMapFinal.containsKey("platform") && !deviceInfoMapFinal.containsKey("platformType")) {
                        log.warn("⚠️ HttpEntity 생성 직전: deviceInfo에서 platform 필드를 platformType으로 변경합니다.");
                        Object platformValue = deviceInfoMapFinal.get("platform");
                        deviceInfoMapFinal.remove("platform");
                        deviceInfoMapFinal.put("platformType", platformValue);
                        body.put("deviceInfo", deviceInfoMapFinal);
                    }
                } else {
                    log.error("❌ HttpEntity 생성 직전: deviceInfo가 Map 타입이 아닙니다! 다시 설정합니다.");
                    body.put("deviceInfo", deviceInfoToSend);
                }
            }

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            
            // 실제 전송 직전 최종 확인
            try {
                String actualRequestBodyJson = objectMapper.writeValueAsString(body);
                String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(body);
                
                log.info("=== 실제 PortOne API로 전송되는 최종 바디 ===");
                log.info("JSON (한 줄): {}", actualRequestBodyJson);
                log.info("JSON (포맷팅):\n{}", prettyJson);
                
                // deviceInfo 포함 여부 확인
                if (actualRequestBodyJson.contains("deviceInfo")) {
                    log.info("✅ 최종 바디에 deviceInfo 포함됨");
                    
                    // 최상위 레벨에 deviceInfo가 있는지 확인 (PortOne V2는 최상위 레벨에 deviceInfo를 요구함)
                    // JSON 구조: {"data":{...},"deviceInfo":{...}}
                    if (actualRequestBodyJson.contains("\"deviceInfo\":{") || actualRequestBodyJson.contains("\"deviceInfo\": {")) {
                        log.info("✅ deviceInfo가 최상위 레벨에 있습니다. (정상)");
                    } else {
                        log.warn("⚠️ deviceInfo가 올바른 위치에 없을 수 있습니다.");
                    }
                    
                    // platformType과 ip 필드가 있는지 확인
                    if (actualRequestBodyJson.contains("\"platformType\"")) {
                        log.info("✅ deviceInfo.platformType 필드 확인됨");
                    } else {
                        log.error("❌ deviceInfo.platformType 필드가 없습니다!");
                    }
                    if (actualRequestBodyJson.contains("\"ip\"")) {
                        log.info("✅ deviceInfo.ip 필드 확인됨");
                    } else {
                        log.error("❌ deviceInfo.ip 필드가 없습니다!");
                    }
                } else {
                    log.error("❌ 최종 바디에 deviceInfo가 없습니다!");
                }
                
                log.info("==========================================");
            } catch (Exception e) {
                log.error("실제 전송 바디 로그 출력 실패", e);
            }

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("PortOne 결제 준비 완료 - paymentId: {}, orderId: {}", paymentId, orderId);
                // 성공 시 아무것도 반환하지 않음 (void)
            } else {
                String errorBody = response.getBody() != null ? response.getBody().toString() : "응답 본문 없음";
                log.error("PortOne 결제 준비 API 호출 실패 - 상태코드: {}, 응답: {}", response.getStatusCode(), errorBody);
                throw new RuntimeException("PortOne 결제 준비 API 호출 실패: " + response.getStatusCode() + " - " + errorBody);
            }
        } catch (Exception e) {
            log.error("PortOne 결제 준비 실패", e);
            throw new RuntimeException("결제 준비 실패: " + e.getMessage(), e);
        }
    }

    /**
     * PortOne 결제 승인 API 호출
     * paymentId와 orderId를 받아서 실제 결제를 승인함
     */
    public PgResponse confirmPayment(String paymentId, String orderId, int amount) {
        try {
            // PortOne V2 API 엔드포인트
            // V2에서는 /v2/payments/{paymentId}/confirm 또는 /v2/payments/confirm 사용
            String url = apiUrl + "/v2/payments/" + paymentId + "/confirm";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // PortOne V2 인증: Bearer {API Secret}
            headers.set("Authorization", "Bearer " + secretKey);
            
            log.info("PortOne 결제 승인 API 호출 - URL: {}, paymentId: {}, orderId: {}, amount: {}", 
                    url, paymentId, orderId, amount);

            // 요청 바디 (V2 API 형식)
            Map<String, Object> body = new HashMap<>();
            body.put("orderId", orderId);
            body.put("amount", amount);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                String status = (String) responseBody.get("status");
                String pgTid = paymentId;
                String rawResponse = objectMapper.writeValueAsString(responseBody);

                if ("PAID".equals(status) || "DONE".equals(status)) {
                    log.info("PortOne 결제 성공 - orderId: {}, paymentId: {}", orderId, paymentId);
                    return PgResponse.success(pgTid, rawResponse);
                } else {
                    String message = (String) responseBody.getOrDefault("message", "결제 실패");
                    log.warn("PortOne 결제 실패 - orderId: {}, message: {}", orderId, message);
                    return PgResponse.failure(message, rawResponse);
                }
            } else {
                throw new RuntimeException("PortOne API 호출 실패: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("PortOne 결제 승인 실패", e);
            return PgResponse.failure("결제 승인 실패: " + e.getMessage(), "");
        }
    }

    @Override
    public PgResponse verifyPayment(Map<String, String> responseData) {
        // PortOne은 결제 승인 API를 직접 호출해야 함
        String paymentId = responseData.get("paymentId");
        String orderId = responseData.get("orderId");
        String amountStr = responseData.get("amount");

        if (paymentId == null || orderId == null || amountStr == null) {
            return PgResponse.failure("필수 파라미터가 누락되었습니다.", "");
        }

        try {
            int amount = Integer.parseInt(amountStr);
            return confirmPayment(paymentId, orderId, amount);
        } catch (Exception e) {
            log.error("PortOne 결제 검증 실패", e);
            return PgResponse.failure("결제 검증 실패: " + e.getMessage(), "");
        }
    }

    @Override
    public String getPgName() {
        return "PORTONE";
    }
}

