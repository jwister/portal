package io.ztoken.portal.payment.trc20;

import io.ztoken.portal.payment.config.PaymentProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HttpTronGridTransferClientTest {
    @Test
    void resolvesAccountTransferCandidatesThroughTheirTransactionEvents() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(json("""
                    {"data":[{"transaction_id":"tx-1","type":"Transfer","token_info":{"address":"TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"},"from":"TFROM","to":"TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE","value":"1000001"}]}
                    """));
            server.enqueue(json("""
                    {"data":[{"transaction_id":"tx-1","event_index":0,"event_name":"Transfer","contract_address":"TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t","result":{"from":"TFROM","to":"TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE","value":"1000001"},"block_number":100,"block_timestamp":1760000000000}]}
                    """));
            server.enqueue(json("{\"block_header\":{\"raw_data\":{\"number\":120}}}"));
            server.start();
            PaymentProperties properties = new PaymentProperties();
            properties.getTrc20().getTrongrid().setBaseUrl(server.url("/").toString());

            TransferQueryResult result = new HttpTronGridTransferClient(properties)
                    .findByReceiveAddress("TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE", null);

            assertThat(result.succeeded()).isTrue();
            assertThat(result.transfers()).singleElement().satisfies(transfer -> {
                assertThat(transfer.txid()).isEqualTo("tx-1");
                assertThat(transfer.confirmations()).isEqualTo(21);
            });
        }
    }

    @Test
    void readsTheLatestBlockOnceForAllCandidatesInOneAddressScan() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(json("""
                    {"data":[{"transaction_id":"tx-1","type":"Transfer"},{"transaction_id":"tx-2","type":"Transfer"}]}
                    """));
            server.enqueue(json("""
                    {"data":[{"transaction_id":"tx-1","event_index":0,"event_name":"Transfer","contract_address":"TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t","result":{"from":"TFROM","to":"TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE","value":"1000001"},"block_number":100,"block_timestamp":1760000000000}]}
                    """));
            server.enqueue(json("""
                    {"data":[{"transaction_id":"tx-2","event_index":0,"event_name":"Transfer","contract_address":"TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t","result":{"from":"TFROM","to":"TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE","value":"1000002"},"block_number":101,"block_timestamp":1760000001000}]}
                    """));
            server.enqueue(json("{\"block_header\":{\"raw_data\":{\"number\":120}}}"));
            server.start();
            PaymentProperties properties = new PaymentProperties();
            properties.getTrc20().getTrongrid().setBaseUrl(server.url("/").toString());

            TransferQueryResult result = new HttpTronGridTransferClient(properties)
                    .findByReceiveAddress("TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE", null);

            assertThat(result.succeeded()).isTrue();
            assertThat(result.transfers()).extracting(ObservedTransfer::txid).containsExactly("tx-1", "tx-2");
            List<String> paths = List.of(
                    server.takeRequest().getPath(), server.takeRequest().getPath(),
                    server.takeRequest().getPath(), server.takeRequest().getPath());
            assertThat(paths).containsExactly(
                    "/v1/accounts/TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE/transactions/trc20?only_confirmed=true&limit=200",
                    "/v1/transactions/tx-1/events", "/v1/transactions/tx-2/events", "/wallet/getnowblock");
        }
    }

    private static MockResponse json(String body) { return new MockResponse().setHeader("Content-Type", "application/json").setBody(body); }
}
