package io.ztoken.portal.payment.trc20;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.ztoken.portal.payment.config.PaymentProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class HttpTronGridTransferClientTest {
    @Test
    void resolvesSubmittedTransactionThroughItsEvents() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(json("""
                    {"data":[{"transaction_id":"tx-1","event_index":0,"event_name":"Transfer","contract_address":"TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t","result":{"from":"TFROM","to":"TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE","value":"1000001"},"block_number":100,"block_timestamp":1760000000000}]}
                    """));
            server.enqueue(json("{\"block_header\":{\"raw_data\":{\"number\":120}}}"));
            server.start();
            PaymentProperties properties = new PaymentProperties();
            properties.getTrc20().getTrongrid().setBaseUrl(server.url("/").toString());

            TransferQueryResult result = new HttpTronGridTransferClient(properties).findByTxid("tx-1");

            assertThat(result.succeeded()).isTrue();
            assertThat(result.transfers()).singleElement().satisfies(transfer -> {
                assertThat(transfer.txid()).isEqualTo("tx-1");
                assertThat(transfer.confirmations()).isEqualTo(21);
            });
            assertThat(server.takeRequest().getPath()).isEqualTo("/v1/transactions/tx-1/events");
            assertThat(server.takeRequest().getPath()).isEqualTo("/wallet/getnowblock");
        }
    }

    @Test
    void logsHttpStatusAndChineseCategoryWithoutLeakingApiKeyOrResponseBody() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(429).setBody("upstream-sensitive-detail"));
            server.start();
            PaymentProperties properties = new PaymentProperties();
            properties.getTrc20().getTrongrid().setBaseUrl(server.url("/").toString());
            properties.getTrc20().getTrongrid().setApiKey("test-trongrid-api-key");
            ListAppender<ILoggingEvent> appender = startAppender();
            try {
                TransferQueryResult result = new HttpTronGridTransferClient(properties).findByTxid("tx-rate-limited");

                assertThat(result.succeeded()).isFalse();
                assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                        .anySatisfy(message -> assertThat(message).contains(
                                "交易哈希=tx-rate-limited", "HTTP状态=429", "失败类别=请求频率受限")
                                .doesNotContain("test-trongrid-api-key", "upstream-sensitive-detail"));
            } finally {
                stopAppender(appender);
            }
        }
    }

    private static MockResponse json(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }

    private static ListAppender<ILoggingEvent> startAppender() {
        Logger logger = (Logger) LoggerFactory.getLogger(HttpTronGridTransferClient.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static void stopAppender(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(HttpTronGridTransferClient.class);
        logger.detachAppender(appender);
        appender.stop();
    }
}
