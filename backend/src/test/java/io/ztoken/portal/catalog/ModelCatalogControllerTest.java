package io.ztoken.portal.catalog;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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
    }

    @Test
    void catalogMapsOnlyCustomerSafePricingFields() throws Exception {
        // quota_type=0: ratio mode (model_ratio), quota_type=1: price mode (model_price)
        NEW_API.enqueue(new MockResponse().setHeader(HttpHeaders.CONTENT_TYPE, "application/json").setBody("""
                {"success":true,"data":[{"id":3,"model_name":"gpt-5-mini","vendor_name":"OpenAI","model_ratio":1.2,"model_price":0,"completion_ratio":2,"cache_ratio":0.25,"enable_groups":["default","premium"],"quota_type":0},{"id":4,"model_name":"glm-5","vendor_name":"Zhipu","model_ratio":0,"model_price":1.0,"completion_ratio":2,"cache_ratio":0.25,"enable_groups":[],"quota_type":1},{"id":5,"model_name":"claude-3","vendor_name":"Anthropic","model_ratio":3.0,"model_price":0,"completion_ratio":3,"enable_groups":["premium"],"quota_type":0}],"vendors":[]}
                """));

        ResponseEntity<ModelCatalog> response = http.getForEntity("/api/catalog/models", ModelCatalog.class);

        RecordedRequest upstream = NEW_API.takeRequest();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().items()).containsExactly(
                new ModelCatalogItem("gpt-5-mini", "OpenAI", List.of("default", "premium"), 1.2, 2.4, 0.3, true),
                new ModelCatalogItem("glm-5", "Zhipu", List.of("default"), 1.0, 2.0, 0.25, true),
                new ModelCatalogItem("claude-3", "Anthropic", List.of("premium"), 3.0, 9.0, null, true));
        assertThat(upstream.getPath()).isEqualTo("/api/pricing");
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
