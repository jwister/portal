package io.ztoken.portal.payment.api;

import io.ztoken.portal.payment.paypal.PayPalWebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PayPalWebhookControllerTest {

    private static final String EVENT = """
            {"id":"WH-1","event_type":"PAYMENT.CAPTURE.COMPLETED","resource":{"id":"CAPTURE-1"}}
            """;

    @Autowired
    private MockMvc mvc;

    @MockBean
    private PayPalWebhookService webhooks;

    @BeforeEach
    void resetWebhookService() {
        Mockito.reset(webhooks);
    }

    @Test
    void passesTheExactRawBodyAndLowerCaseHeadersWithoutRequiringAPortalSession() throws Exception {
        mvc.perform(post("/api/webhooks/paypal")
                        .header("PAYPAL-TRANSMISSION-ID", "transmission-1")
                        .header("PayPal-Auth-Algo", "SHA256withRSA")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EVENT))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        ArgumentCaptor<Map<String, String>> headers = ArgumentCaptor.forClass(Map.class);
        verify(webhooks).handle(headers.capture(), eq(EVENT));
        assertThat(headers.getValue()).containsEntry("paypal-transmission-id", "transmission-1")
                .containsEntry("paypal-auth-algo", "SHA256withRSA");
    }

    @Test
    void returnsOnlyBadRequestForAnInvalidWebhookWithoutProviderDetails() throws Exception {
        doThrow(new IllegalArgumentException("PayPal signature leaked-detail"))
                .when(webhooks).handle(org.mockito.ArgumentMatchers.anyMap(), eq(EVENT));

        mvc.perform(post("/api/webhooks/paypal").contentType(MediaType.APPLICATION_JSON).content(EVENT))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
    }

    @Test
    void returnsOnlyServiceUnavailableWhenVerificationInfrastructureFails() throws Exception {
        doThrow(new IllegalStateException("PayPal OAuth server-secret"))
                .when(webhooks).handle(org.mockito.ArgumentMatchers.anyMap(), eq(EVENT));

        mvc.perform(post("/api/webhooks/paypal").contentType(MediaType.APPLICATION_JSON).content(EVENT))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(""));
    }
}
