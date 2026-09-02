package io.ztoken.portal.newapi;

import io.ztoken.portal.session.NewApiIdentity;
import io.ztoken.portal.session.PortalPrincipal;
import io.ztoken.portal.console.DashboardSummary;
import io.ztoken.portal.console.TokenKey;
import io.ztoken.portal.console.TokenList;
import io.ztoken.portal.console.TokenSummary;
import io.ztoken.portal.console.TokenWriteRequest;
import io.ztoken.portal.console.LogPage;
import io.ztoken.portal.console.LogStats;
import io.ztoken.portal.console.Profile;
import io.ztoken.portal.console.ProfileUpdateRequest;
import io.ztoken.portal.console.LogQuery;
import io.ztoken.portal.catalog.ModelCatalog;

public interface NewApiClient {

    NewApiLogin login(String username, String password);

    void register(String username, String email, String password);

    NewApiIdentity getSelf(PortalPrincipal principal);

    DashboardSummary getDashboard(PortalPrincipal principal);

    TokenList getTokens(PortalPrincipal principal, int page, int pageSize);

    void createToken(PortalPrincipal principal, TokenWriteRequest request);

    TokenSummary updateToken(PortalPrincipal principal, long id, TokenWriteRequest request);

    TokenSummary updateTokenStatus(PortalPrincipal principal, long id, boolean enabled);

    void deleteToken(PortalPrincipal principal, long id);

    TokenKey getTokenKey(PortalPrincipal principal, long id);

    LogPage getLogs(PortalPrincipal principal, LogQuery query);

    LogStats getLogStats(PortalPrincipal principal, LogQuery query);

    Profile getProfile(PortalPrincipal principal);

    Profile updateProfile(PortalPrincipal principal, ProfileUpdateRequest request);

    ModelCatalog getModelCatalog();
}
