package io.ztoken.portal.auth;

import io.ztoken.portal.console.TokenList;
import io.ztoken.portal.console.TokenWriteRequest;
import io.ztoken.portal.newapi.NewApiClient;
import io.ztoken.portal.newapi.NewApiException;
import io.ztoken.portal.session.NewApiIdentity;
import io.ztoken.portal.newapi.NewApiLogin;
import io.ztoken.portal.session.PortalPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultTokenServiceTest {

    @Mock
    private NewApiClient newApiClient;

    private DefaultTokenService service;

    @BeforeEach
    void setUp() {
        service = new DefaultTokenService(newApiClient);
    }

    @Test
    void initializeDefaultTokenOnRegisterCreatesTokenAndLogsOut() {
        NewApiIdentity identity = new NewApiIdentity(10L, "alice");
        NewApiLogin login = new NewApiLogin(identity, "test-access-token", "test-refresh-token", "test-sid",
                Instant.now().plusSeconds(3600));

        when(newApiClient.login("alice", "secret123")).thenReturn(login);
        when(newApiClient.getTokens(any(PortalPrincipal.class), eq(1), eq(1)))
                .thenReturn(new TokenList(1, 1, 0L, List.of()));

        service.initializeDefaultTokenOnRegister("alice", "secret123");

        ArgumentCaptor<TokenWriteRequest> captor = ArgumentCaptor.forClass(TokenWriteRequest.class);
        verify(newApiClient).createToken(any(PortalPrincipal.class), captor.capture());
        TokenWriteRequest request = captor.getValue();
        assertThat(request.name()).isEqualTo("default");
        assertThat(request.unlimited()).isTrue();
        assertThat(request.expiredTime()).isEqualTo(-1L);
        assertThat(request.group()).isEqualTo("default");

        verify(newApiClient).logout(any(PortalPrincipal.class));
    }

    @Test
    void ensureDefaultTokenExistsSkipsWhenTokensAlreadyExist() {
        PortalPrincipal principal = new PortalPrincipal(10L, "alice", "test-token");
        when(newApiClient.getTokens(principal, 1, 1))
                .thenReturn(new TokenList(1, 1, 1L, List.of()));

        service.ensureDefaultTokenExists(principal);

        verify(newApiClient, never()).createToken(any(), any());
    }

    @Test
    void initializeDefaultTokenOnRegisterSuppressesExceptionsWithoutFailing() {
        when(newApiClient.login("alice", "secret123"))
                .thenThrow(new NewApiException("NewAPI 连接超时"));

        assertThatCode(() -> service.initializeDefaultTokenOnRegister("alice", "secret123"))
                .doesNotThrowAnyException();

        verify(newApiClient, never()).createToken(any(), any());
    }
}
