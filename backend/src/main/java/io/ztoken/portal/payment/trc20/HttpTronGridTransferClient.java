package io.ztoken.portal.payment.trc20;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ztoken.portal.payment.config.PaymentProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** 只读取 TronGrid 交易事件；支付结论始终由 TransferVerificationService 作二次核验。 */
@Component
public class HttpTronGridTransferClient implements TronGridTransferClient {
    private static final Logger log = LoggerFactory.getLogger(HttpTronGridTransferClient.class);
    private final WebClient client;
    private final ObjectMapper json = new ObjectMapper();
    private final String apiKey;

    public HttpTronGridTransferClient(PaymentProperties properties) {
        this.client = WebClient.builder().baseUrl(properties.getTrc20().getTrongrid().getBaseUrl()).build();
        this.apiKey = properties.getTrc20().getTrongrid().getApiKey();
    }

    @Override
    public TransferQueryResult findByTxid(String txid) {
        try {
            String events = client.get().uri("/v1/transactions/{txid}/events", txid).headers(this::addApiKey)
                    .retrieve().bodyToMono(String.class).block(Duration.ofSeconds(10));
            long currentBlock = currentBlockNumber();
            List<ObservedTransfer> transfers = mapEvents(events, txid, currentBlock);
            log.info("TronGrid 交易查询完成：交易哈希={}，匹配到转账数={}", txid, transfers.size());
            return TransferQueryResult.success(transfers);
        } catch (RuntimeException exception) {
            log.warn("TronGrid 交易查询失败：交易哈希={}，异常类型={}", txid, exception.getClass().getSimpleName());
            return TransferQueryResult.retryableFailure(exception.getClass().getSimpleName());
        }
    }

    @Override
    public TransferQueryResult findByReceiveAddress(String receiveAddress, String fingerprint) {
        try {
            String candidates = client.get().uri(uri -> {
                        var builder = uri.path("/v1/accounts/{address}/transactions/trc20").queryParam("only_confirmed", "true").queryParam("limit", 200);
                        if (fingerprint != null && !fingerprint.isBlank()) builder.queryParam("fingerprint", fingerprint);
                        return builder.build(receiveAddress);
                    })
                    .headers(this::addApiKey).retrieve().bodyToMono(String.class).block(Duration.ofSeconds(10));
            List<CandidateEvents> candidateEvents = new ArrayList<>();
            for (JsonNode candidate : read(candidates).path("data")) {
                if (!"Transfer".equals(candidate.path("type").asText())) continue;
                String txid = candidate.path("transaction_id").asText();
                if (txid.isBlank()) continue;
                String events = client.get().uri("/v1/transactions/{txid}/events", txid).headers(this::addApiKey)
                        .retrieve().bodyToMono(String.class).block(Duration.ofSeconds(10));
                candidateEvents.add(new CandidateEvents(txid, events));
            }
            // 同一轮地址扫描共用一次最新区块高度，避免历史交易较多时反复调用区块接口而触发上游限流。
            long currentBlock = currentBlockNumber();
            List<ObservedTransfer> transfers = new ArrayList<>();
            for (CandidateEvents events : candidateEvents) transfers.addAll(mapEvents(events.body(), events.txid(), currentBlock));
            String nextFingerprint = read(candidates).path("meta").path("fingerprint").asText(null);
            log.atLevel(transfers.isEmpty() ? org.slf4j.event.Level.DEBUG : org.slf4j.event.Level.INFO)
                    .log("TronGrid 收款地址扫描完成：收款地址={}，匹配到转账数={}", receiveAddress, transfers.size());
            return TransferQueryResult.success(transfers, nextFingerprint);
        } catch (RuntimeException exception) {
            String reason = failureReason(exception);
            log.warn("TronGrid 收款地址扫描失败：收款地址={}，失败原因={}", receiveAddress, reason);
            return TransferQueryResult.retryableFailure(reason);
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
        String body = client.post().uri("/wallet/getnowblock").headers(this::addApiKey)
                .retrieve().bodyToMono(String.class).block(Duration.ofSeconds(10));
        long block = read(body).path("block_header").path("raw_data").path("number").asLong(-1);
        if (block < 0) throw new IllegalStateException("TronGrid 未返回最新区块高度");
        return block;
    }

    /** 将上游异常转换为可排查且不含密钥、响应正文等敏感信息的失败原因。 */
    private String failureReason(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return exception.getClass().getSimpleName();
        return exception.getClass().getSimpleName() + "：" + message;
    }

    /** 保存已读取但尚未计算确认数的交易事件，待本轮统一取得最新区块高度后处理。 */
    private record CandidateEvents(String txid, String body) { }

    private JsonNode read(String body) { try { return json.readTree(body); } catch (Exception e) { throw new IllegalArgumentException("TronGrid 响应无法解析", e); } }
    private void addApiKey(HttpHeaders headers) { if (apiKey != null && !apiKey.isBlank()) headers.set("TRON-PRO-API-KEY", apiKey); }
}
