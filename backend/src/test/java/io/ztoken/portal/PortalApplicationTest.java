package io.ztoken.portal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PortalApplicationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate http;

    @Test
    void unknownClientRouteReturnsSpaEntry() {
        ResponseEntity<String> response = http.getForEntity("http://localhost:" + port + "/models", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("<div id=\"root\">");
    }
}
