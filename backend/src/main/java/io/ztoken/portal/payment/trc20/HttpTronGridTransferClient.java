package io.ztoken.portal.payment.trc20;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ztoken.portal.payment.config.PaymentProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** 只读取 TronGrid 交易事件；支付结论始终由 TransferVerificationService 作二次核验。 */
@Component
public class HttpTronGridTransferClient implements TronGridTransferClient {
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
            return TransferQueryResult.success(mapEvents(events, txid, currentBlock));
        } catch (RuntimeException exception) {
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
            List<ObservedTransfer> transfers = new ArrayList<>();
            for (JsonNode candidate : read(candidates).path("data")) {
                if (!"Transfer".equals(candidate.path("type").asText())) continue;
                String txid = candidate.path("transaction_id").asText();
                if (txid.isBlank()) continue;
                String events = client.get().uri("/v1/transactions/{txid}/events", txid).headers(this::addApiKey)
                        .retrieve().bodyToMono(String.class).block(Duration.ofSeconds(10));
                transfers.addAll(mapEvents(events, txid, currentBlockNumber()));
            }
            return TransferQueryResult.success(transfers, read(candidates).path("meta").path("fingerprint").asText(null));
        } catch (RuntimeException exception) {
            return TransferQueryResult.retryableFailure(exception.getClass().getSimpleName());
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

    private JsonNode read(String body) { try { return json.readTree(body); } catch (Exception e) { throw new IllegalArgumentException("TronGrid 响应无法解析", e); } }
    private void addApiKey(HttpHeaders headers) { if (apiKey != null && !apiKey.isBlank()) headers.set("TRON-PRO-API-KEY", apiKey); }
}
