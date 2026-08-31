package io.ztoken.portal.console;

import io.ztoken.portal.session.NewApiIdentity;
import io.ztoken.portal.session.PortalSessionService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DashboardControllerTest {

    private static final MockWebServer NEW_API = startServer();

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private PortalSessionService sessions;

    @AfterAll
    static void stopNewApi() throws Exception {
        NEW_API.shutdown();
    }

    @DynamicPropertySource
    static void newApiProperties(DynamicPropertyRegistry registry) {
        registry.add("portal.new-api.base-url", () -> NEW_API.url("/").toString());
    }

    @Test
    void dashboardReturnsOnlyTheCurrentUsersUsageSummary() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("""
                        {"success":true,"data":{"id":7,"username":"alice","quota":900,"used_quota":100,"request_count":12}}
                        """));
        String sessionId = sessions.create(new NewApiIdentity(7L, "alice"), "access-token").getId();
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, "PORTAL_SESSION=" + sessionId);

        ResponseEntity<DashboardSummary> response = http.exchange("/api/console/dashboard", HttpMethod.GET,
                new HttpEntity<>(headers), DashboardSummary.class);

        RecordedRequest upstream = NEW_API.takeRequest();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(new DashboardSummary(900L, 100L, 12L));
        assertThat(upstream.getPath()).isEqualTo("/api/user/self");
        assertThat(upstream.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer access-token");
        assertThat(upstream.getHeader("New-Api-User")).isEqualTo("7");
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
