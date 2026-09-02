package io.ztoken.portal.payment.paypal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ztoken.portal.payment.config.PaymentProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpPayPalClientTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void createsCaptureOrderWithServerCentsAndStableIdempotencyKey() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(oauthResponse("server-token", 300));
            server.enqueue(jsonResponse("""
                    {"id":"PP-ORDER","status":"CREATED","purchase_units":[{
                      "amount":{"currency_code":"USD","value":"25.50"}
                    }]}
                    """));
            server.start();

            HttpPayPalClient client = clientFor(server);

            assertThat(client.createOrder("PO-1", 2_550L, "PO-1-paypal"))
                    .isEqualTo(new PayPalOrderDetails("PP-ORDER", "CREATED", "USD", 2_550L));

            RecordedRequest oauth = server.takeRequest();
            assertThat(oauth.getPath()).isEqualTo("/v1/oauth2/token");
            assertThat(oauth.getHeader("Authorization")).isEqualTo("Basic Y2xpZW50LWlkOmNsaWVudC1zZWNyZXQ=");
            assertThat(oauth.getHeader("Content-Type")).startsWith("application/x-www-form-urlencoded");
            assertThat(oauth.getBody().readUtf8()).isEqualTo("grant_type=client_credentials");

            RecordedRequest create = server.takeRequest();
            assertThat(create.getPath()).isEqualTo("/v2/checkout/orders");
            assertThat(create.getHeader("Authorization")).isEqualTo("Bearer server-token");
            assertThat(create.getHeader("PayPal-Request-Id")).isEqualTo("PO-1-paypal");
            assertThat(create.getHeader("Prefer")).isEqualTo("return=representation");
            JsonNode body = JSON.readTree(create.getBody().readUtf8());
            assertThat(body.path("intent").asText()).isEqualTo("CAPTURE");
            assertThat(body.path("purchase_units").get(0).path("reference_id").asText()).isEqualTo("PO-1");
            assertThat(body.path("purchase_units").get(0).path("amount").path("currency_code").asText())
                    .isEqualTo("USD");
            assertThat(body.path("purchase_units").get(0).path("amount").path("value").asText())
                    .isEqualTo("25.50");
        }
    }

    @Test
    void reusesValidOAuthTokenForCaptureAndSendsCaptureIdempotencyKey() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(oauthResponse("server-token", 300));
            server.enqueue(jsonResponse("""
                    {"id":"PP-ORDER","status":"CREATED","purchase_units":[{
                      "amount":{"currency_code":"USD","value":"25.50"}
                    }]}
                    """));
            server.enqueue(jsonResponse("""
                    {"id":"PP-ORDER","purchase_units":[{"payments":{"captures":[{
                      "id":"CAPTURE-1","status":"COMPLETED",
                      "amount":{"currency_code":"USD","value":"25.50"}
                    }]}}]}
                    """));
            server.start();

            HttpPayPalClient client = clientFor(server);
            client.createOrder("PO-1", 2_550L, "PO-1-paypal");

            assertThat(client.captureOrder("PP-ORDER", "PO-1-capture"))
                    .isEqualTo(new PayPalCaptureDetails("PP-ORDER", "CAPTURE-1", "COMPLETED", "USD", 2_550L));

            assertThat(server.takeRequest().getPath()).isEqualTo("/v1/oauth2/token");
            assertThat(server.takeRequest().getPath()).isEqualTo("/v2/checkout/orders");
            RecordedRequest capture = server.takeRequest();
            assertThat(capture.getPath()).isEqualTo("/v2/checkout/orders/PP-ORDER/capture");
            assertThat(capture.getHeader("Authorization")).isEqualTo("Bearer server-token");
            assertThat(capture.getHeader("PayPal-Request-Id")).isEqualTo("PO-1-capture");
            assertThat(capture.getHeader("Prefer")).isEqualTo("return=representation");
            assertThat(server.getRequestCount()).isEqualTo(3);
        }
    }

    @Test
    void verifiesWebhookUsingPayPalOfficialVerificationEnvelope() throws Exception {
        String rawBody = """
                {"id":"WH-1","event_type":"PAYMENT.CAPTURE.COMPLETED","resource":{"id":"CAPTURE-1"}}
                """;
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(oauthResponse("server-token", 300));
            server.enqueue(jsonResponse("{\"verification_status\":\"SUCCESS\"}"));
            server.start();

            HttpPayPalClient client = clientFor(server);

            assertThat(client.verifyWebhook(Map.of(
                    "PayPal-Auth-Algo", "SHA256withRSA",
                    "PayPal-Cert-Url", "https://api.paypal.test/cert.pem",
                    "PayPal-Transmission-Id", "transmission-1",
                    "PayPal-Transmission-Sig", "signature",
                    "PayPal-Transmission-Time", "2026-09-02T10:00:00Z"
            ), rawBody)).isTrue();

            assertThat(server.takeRequest().getPath()).isEqualTo("/v1/oauth2/token");
            RecordedRequest verify = server.takeRequest();
            assertThat(verify.getPath()).isEqualTo("/v1/notifications/verify-webhook-signature");
            assertThat(verify.getHeader("Authorization")).isEqualTo("Bearer server-token");
            JsonNode body = JSON.readTree(verify.getBody().readUtf8());
            assertThat(body.path("auth_algo").asText()).isEqualTo("SHA256withRSA");
            assertThat(body.path("cert_url").asText()).isEqualTo("https://api.paypal.test/cert.pem");
            assertThat(body.path("transmission_id").asText()).isEqualTo("transmission-1");
            assertThat(body.path("transmission_sig").asText()).isEqualTo("signature");
            assertThat(body.path("transmission_time").asText()).isEqualTo("2026-09-02T10:00:00Z");
            assertThat(body.path("webhook_id").asText()).isEqualTo("webhook-id");
            assertThat(body.path("webhook_event").path("id").asText()).isEqualTo("WH-1");
        }
    }

    @Test
    void rejectsProviderAmountsThatCannotBeRepresentedAsExactCents() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(oauthResponse("server-token", 300));
            server.enqueue(jsonResponse("""
                    {"id":"PP-ORDER","status":"CREATED","purchase_units":[{
                      "amount":{"currency_code":"USD","value":"25.501"}
                    }]}
                    """));
            server.start();

            assertThatThrownBy(() -> clientFor(server).createOrder("PO-1", 2_550L, "PO-1-paypal"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("invalid USD amount");
        }
    }

    @Test
    void selectsSandboxByDefaultAndLiveOnlyForExplicitLiveMode() {
        PaymentProperties.Paypal sandbox = configuredPaypal();
        PaymentProperties.Paypal live = configuredPaypal();
        live.setMode("live");

        assertThat(HttpPayPalClient.apiBaseUrl(sandbox)).isEqualTo("https://api-m.sandbox.paypal.com");
        assertThat(HttpPayPalClient.apiBaseUrl(live)).isEqualTo("https://api-m.paypal.com");
    }

    private static HttpPayPalClient clientFor(MockWebServer server) {
        return new HttpPayPalClient(configuredPaypal(), WebClient.builder().baseUrl(server.url("/").toString()).build(), JSON);
    }

    private static PaymentProperties.Paypal configuredPaypal() {
        PaymentProperties.Paypal paypal = new PaymentProperties.Paypal();
        paypal.setClientId("client-id");
        paypal.setClientSecret("client-secret");
        paypal.setWebhookId("webhook-id");
        return paypal;
    }

    private static MockResponse oauthResponse(String token, long expiresIn) {
        return jsonResponse("{\"access_token\":\"" + token + "\",\"expires_in\":" + expiresIn + "}");
    }

    private static MockResponse jsonResponse(String body) {
        return new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body);
    }
}
