package io.ztoken.portal.payment.trc20;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ztoken.portal.payment.config.PaymentProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

/** 只读取 TronGrid 交易事件；支付结论始终由 TransferVerificationService 作二次核验。 */
@Component
public class HttpTronGridTransferClient implements TronGridTransferClient {
    private static final Logger log = LoggerFactory.getLogger(HttpTronGridTransferClient.class);
    private static final int TRONGRID_MAX_RESPONSE_BYTES = 8 * 1024 * 1024;
    private final WebClient client;
    private final ObjectMapper json = new ObjectMapper();
    private final String apiKey;

    public HttpTronGridTransferClient(PaymentProperties properties) {
        this.client = WebClient.builder().baseUrl(properties.getTrc20().getTrongrid().getBaseUrl())
                // getnowblock 返回完整区块，默认 256KB 缓冲可能不足。
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(TRONGRID_MAX_RESPONSE_BYTES))
                .build();
        this.apiKey = properties.getTrc20().getTrongrid().getApiKey();
    }

    @Override
    public TransferQueryResult findByTxid(String txid) {
        try {
            String events = awaitResponse("查询交易事件", client.get().uri("/v1/transactions/{txid}/events", txid)
                    .headers(this::addApiKey).retrieve().bodyToMono(String.class));
            long currentBlock = currentBlockNumber();
            List<ObservedTransfer> transfers = mapEvents(events, txid, currentBlock);
            log.info("TronGrid 交易查询完成：交易哈希={}，匹配到转账数={}", txid, transfers.size());
            return TransferQueryResult.success(transfers);
        } catch (MissingCurrentBlockHeightException exception) {
            log.warn("TronGrid 交易查询失败：交易哈希={}，失败类别=最新区块高度缺失，响应顶层字段={}",
                    txid, exception.responseFieldNames());
            return TransferQueryResult.retryableFailure("LATEST_BLOCK_HEIGHT_MISSING");
        } catch (TronGridCallException exception) {
            Throwable cause = rootCause(exception);
            if (cause instanceof WebClientResponseException responseException) {
                int status = responseException.getStatusCode().value();
                String category = httpFailureCategory(status);
                log.warn("TronGrid 交易查询失败：交易哈希={}，调用阶段={}，HTTP状态={}，失败类别={}",
                        txid, exception.stage(), status, category);
                return TransferQueryResult.retryableFailure("HTTP_" + status + "_" + category);
            }
            String category = cause instanceof TimeoutException ? "调用超时" : "调用异常";
            log.warn("TronGrid 交易查询失败：交易哈希={}，调用阶段={}，失败类别={}，根因类型={}",
                    txid, exception.stage(), category, cause.getClass().getSimpleName());
            return TransferQueryResult.retryableFailure(category + "_" + cause.getClass().getSimpleName());
        } catch (WebClientResponseException exception) {
            int status = exception.getStatusCode().value();
            String category = httpFailureCategory(status);
            log.warn("TronGrid 交易查询失败：交易哈希={}，HTTP状态={}，失败类别={}", txid, status, category);
            return TransferQueryResult.retryableFailure("HTTP_" + status + "_" + category);
        } catch (RuntimeException exception) {
            Throwable cause = rootCause(exception);
            String category = cause instanceof TimeoutException ? "调用超时" : "调用异常";
            log.warn("TronGrid 交易查询失败：交易哈希={}，调用阶段={}，失败类别={}，根因类型={}",
                    txid, "解析交易响应", category, cause.getClass().getSimpleName());
            return TransferQueryResult.retryableFailure(category + "_" + cause.getClass().getSimpleName());
        }
    }

    private List<ObservedTransfer> mapEvents(String body, String requestedTxid, long currentBlock) {
        List<ObservedTransfer> result = new ArrayList<>();
        for (JsonNode record : read(body).path("data")) {
            if (!"Transfer".equals(record.path("event_name").asText())) continue;
            JsonNode fields = record.path("result");
            String value = fields.path("value").asText(record.path("value").asText());
            if (value.isBlank()) continue;
            long block = record.path("block_number").asLong();
            result.add(new ObservedTransfer(record.path("transaction_id").asText(requestedTxid),
                    record.path("event_index").asLong(), record.path("contract_address").asText(),
                    TronAddressCodec.normalizeAccountAddress(fields.path("from").asText(record.path("from").asText())),
                    TronAddressCodec.normalizeAccountAddress(fields.path("to").asText(record.path("to").asText())),
                    Long.parseLong(value), block, Instant.ofEpochMilli(record.path("block_timestamp").asLong()),
                    !record.path("revert").asBoolean(false), Math.toIntExact(Math.max(0, currentBlock - block + 1))));
        }
        return result;
    }

    private long currentBlockNumber() {
        String body = awaitResponse("查询最新区块", client.post().uri("/wallet/getnowblock").headers(this::addApiKey)
                .retrieve().bodyToMono(String.class));
        JsonNode response = read(body);
        long block = response.path("block_header").path("raw_data").path("number").asLong(-1);
        if (block < 0) throw new MissingCurrentBlockHeightException(responseFieldNames(response));
        return block;
    }

    private JsonNode read(String body) { try { return json.readTree(body); } catch (Exception e) { throw new IllegalArgumentException("TronGrid 响应无法解析", e); } }
    private void addApiKey(HttpHeaders headers) { if (apiKey != null && !apiKey.isBlank()) headers.set("TRON-PRO-API-KEY", apiKey); }

    private String httpFailureCategory(int status) {
        return switch (status) {
            case 401, 403 -> "API Key 无效或无权限";
            case 404 -> "交易事件暂不可用或交易不存在";
            case 429 -> "请求频率受限";
            default -> status >= 500 && status < 600 ? "TronGrid 服务异常" : "上游 HTTP 异常";
        };
    }

    private String responseFieldNames(JsonNode response) {
        List<String> names = new ArrayList<>();
        response.fieldNames().forEachRemaining(names::add);
        return names.isEmpty() ? "<无>" : String.join(",", names);
    }

    private String awaitResponse(String stage, reactor.core.publisher.Mono<String> response) {
        try {
            return response.block(Duration.ofSeconds(10));
        } catch (RuntimeException exception) {
            throw new TronGridCallException(stage, exception);
        }
    }

    private Throwable rootCause(Throwable exception) {
        Throwable current = exception;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current;
    }

    private static final class MissingCurrentBlockHeightException extends RuntimeException {
        private final String responseFieldNames;

        private MissingCurrentBlockHeightException(String responseFieldNames) {
            this.responseFieldNames = responseFieldNames;
        }

        private String responseFieldNames() {
            return responseFieldNames;
        }
    }

    private static final class TronGridCallException extends RuntimeException {
        private final String stage;

        private TronGridCallException(String stage, RuntimeException cause) {
            super(cause);
            this.stage = stage;
        }

        private String stage() {
            return stage;
        }
    }
}
