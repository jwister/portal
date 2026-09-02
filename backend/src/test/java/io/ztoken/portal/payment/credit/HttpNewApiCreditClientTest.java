package io.ztoken.portal.payment.credit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.ztoken.portal.config.PortalProperties;
import io.ztoken.portal.payment.config.PaymentProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class HttpNewApiCreditClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockWebServer newApi;
    private PortalProperties portalProperties;
    private PaymentProperties paymentProperties;
    private HttpNewApiCreditClient client;

    @BeforeEach
    void setUp() throws Exception {
        newApi = new MockWebServer();
        newApi.start();

        portalProperties = new PortalProperties();
        portalProperties.getNewApi().setBaseUrl(newApi.url("/").toString());
        paymentProperties = new PaymentProperties();
        paymentProperties.getNewApiCredit().setAccessToken("service-credit-token");
        client = new HttpNewApiCreditClient(portalProperties, paymentProperties, objectMapper, Duration.ofSeconds(2));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (newApi != null) {
            newApi.shutdown();
        }
    }

    @Test
    void sendsOnlyTheServerCredentialAndRequiredQuotaCommand() throws Exception {
        newApi.enqueue(jsonResponse(200, "{\"success\":true}"));

        CreditResult result = client.addQuota(7L, 2_500_000L);

        RecordedRequest request = newApi.takeRequest();
        String bodyText = request.getBody().readUtf8();
        JsonNode body = objectMapper.readTree(bodyText);
        assertThat(result).isEqualTo(CreditResult.SUCCESS);
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/api/user/manage");
        assertThat(request.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer service-credit-token");
        assertThat(request.getHeader("New-Api-User")).isNull();
        assertThat(request.getHeader(HttpHeaders.CONTENT_TYPE)).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(body.path("id").asLong()).isEqualTo(7L);
        assertThat(body.path("action").asText()).isEqualTo("add_quota");
        assertThat(body.path("mode").asText()).isEqualTo("add");
        assertThat(body.path("value").asLong()).isEqualTo(2_500_000L);
        assertThat(bodyText).doesNotContain("service-credit-token").doesNotContain("browser-access-token");
    }

    @Test
    void classifiesAnExplicitBusinessRejectionAsFailed() throws Exception {
        newApi.enqueue(jsonResponse(200, "{\"success\":false}"));

        assertThat(client.addQuota(7L, 500_000L)).isEqualTo(CreditResult.FAILED);
        newApi.takeRequest();
    }

    @Test
    void classifiesAnyFourHundredResponseAsFailed() throws Exception {
        newApi.enqueue(jsonResponse(403, "not a response schema"));

        assertThat(client.addQuota(7L, 500_000L)).isEqualTo(CreditResult.FAILED);
        newApi.takeRequest();
    }

    @Test
    void classifiesServerErrorsAsUnknown() throws Exception {
        newApi.enqueue(jsonResponse(500, "{\"success\":false}"));

        assertThat(client.addQuota(7L, 500_000L)).isEqualTo(CreditResult.UNKNOWN);
        newApi.takeRequest();
    }

    @Test
    void classifiesMalformedSuccessfulResponsesAsUnknown() throws Exception {
        newApi.enqueue(jsonResponse(200, "this is not json"));

        assertThat(client.addQuota(7L, 500_000L)).isEqualTo(CreditResult.UNKNOWN);
        newApi.takeRequest();
    }

    @Test
    void classifiesTimedOutRequestsAsUnknown() throws Exception {
        newApi.enqueue(jsonResponse(200, "{\"success\":true}").setHeadersDelay(2, TimeUnit.SECONDS));

        HttpNewApiCreditClient shortTimeoutClient = new HttpNewApiCreditClient(
                portalProperties, paymentProperties, objectMapper, Duration.ofMillis(100));

        assertThat(shortTimeoutClient.addQuota(7L, 500_000L)).isEqualTo(CreditResult.UNKNOWN);
        newApi.takeRequest();
    }

    @Test
    void classifiesConnectionFailuresAsUnknown() throws Exception {
        newApi.shutdown();
        newApi = null;

        assertThat(client.addQuota(7L, 500_000L)).isEqualTo(CreditResult.UNKNOWN);
    }

    private MockResponse jsonResponse(int status, String body) {
        return new MockResponse()
                .setResponseCode(status)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(body);
    }
}
