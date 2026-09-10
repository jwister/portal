package io.ztoken.portal.catalog;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ModelCatalogControllerTest {

    private static final MockWebServer NEW_API = startServer();

    @Autowired
    private TestRestTemplate http;

    @AfterAll
    static void stopNewApi() throws Exception {
        NEW_API.shutdown();
    }

    @DynamicPropertySource
    static void newApiProperties(DynamicPropertyRegistry registry) {
        registry.add("portal.new-api.base-url", () -> NEW_API.url("/").toString());
        registry.add("portal.new-api.pricing-token", () -> "test-pricing-token");
    }

    @Test
    @Order(1)
    void pricingForwardsCompletePayloadAndPricingToken() throws Exception {
        String body = """
                {"success":true,"data":[{"model_name":"gpt-5-mini","billing_usage_schema":{"duration":"second"}}],"vendors":[{"id":7,"name":"OpenAI"}],"group_ratio":{"default":1},"pricing_version":"v42"}
                """.trim();
        NEW_API.enqueue(new MockResponse().setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).setBody(body));

        ResponseEntity<String> response = http.getForEntity("/api/catalog/pricing", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).isEqualTo(body);
        RecordedRequest upstream = NEW_API.takeRequest();
        assertThat(upstream.getPath()).isEqualTo("/api/pricing");
        assertThat(upstream.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer test-pricing-token");
    }

    @Test
    @Order(4)
    void statusForwardsCompletePublicPayloadWithoutPricingToken() throws Exception {
        String body = """
                {"success":true,"data":{"price":1.5,"usd_exchange_rate":7.2,"HeaderNavModules":"{\\"pricing\\":true}"}}
                """.trim();
        NEW_API.enqueue(new MockResponse().setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).setBody(body));

        ResponseEntity<String> response = http.getForEntity("/api/catalog/status", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(body);
        RecordedRequest upstream = NEW_API.takeRequest();
        assertThat(upstream.getPath()).isEqualTo("/api/status");
        assertThat(upstream.getHeader(HttpHeaders.AUTHORIZATION)).isNull();
    }

    @Test
    @Order(2)
    void performanceSummaryForwardsHoursAndPayload() throws Exception {
        String body = """
                {"success":true,"data":{"models":[{"model_name":"gpt-5-mini","success_rate":99.9}]}}
                """.trim();
        NEW_API.enqueue(new MockResponse().setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).setBody(body));

        ResponseEntity<String> response = http.getForEntity("/api/catalog/perf-metrics/summary?hours=48", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(body);
        assertThat(NEW_API.takeRequest().getPath()).isEqualTo("/api/perf-metrics/summary?hours=48");
    }

    @Test
    @Order(3)
    void performanceMetricsForwardsQueryAndUpstreamError() throws Exception {
        String body = """
                {"success":false,"message":"metrics unavailable"}
                """.trim();
        NEW_API.enqueue(new MockResponse().setResponseCode(HttpStatus.SERVICE_UNAVAILABLE.value())
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).setBody(body));

        ResponseEntity<String> response = http.getForEntity(
                "/api/catalog/perf-metrics?model=gpt-5-mini&group=premium&hours=12", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isEqualTo(body);
        RecordedRequest upstream = NEW_API.takeRequest();
        assertThat(upstream.getRequestUrl().queryParameter("model")).isEqualTo("gpt-5-mini");
        assertThat(upstream.getRequestUrl().queryParameter("group")).isEqualTo("premium");
        assertThat(upstream.getRequestUrl().queryParameter("hours")).isEqualTo("12");
    }

    private static MockWebServer startServer() {
        MockWebServer server = new MockWebServer();
        try {
            server.start();
            return server;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
