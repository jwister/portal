package io.ztoken.portal.newapi;

import io.ztoken.portal.session.PortalPrincipal;
import io.ztoken.portal.session.NewApiIdentity;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class NewApiHttpClientTest {

    private static final MockWebServer NEW_API = startServer();

    @Autowired
    private NewApiClient client;

    @AfterAll
    static void stopNewApi() throws Exception {
        NEW_API.shutdown();
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

    @DynamicPropertySource
    static void newApiProperties(DynamicPropertyRegistry registry) {
        registry.add("portal.new-api.base-url", () -> NEW_API.url("/").toString());
    }

    @Test
    void currentUserRequestUsesBearerTokenAndMatchingUserHeader() throws Exception {
        NEW_API.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .setBody("{" + "\"success\":true,\"data\":{\"id\":7,\"username\":\"alice\"}}"));

        NewApiIdentity identity = client.getSelf(new PortalPrincipal(7L, "alice", "access-token"));

        RecordedRequest request = NEW_API.takeRequest();
        assertThat(identity).isEqualTo(new NewApiIdentity(7L, "alice"));
        assertThat(request.getPath()).isEqualTo("/api/user/self");
        assertThat(request.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer access-token");
        assertThat(request.getHeader("New-Api-User")).isEqualTo("7");
    }
}
