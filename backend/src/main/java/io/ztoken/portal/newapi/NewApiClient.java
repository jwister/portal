package io.ztoken.portal.newapi;

import io.ztoken.portal.session.NewApiIdentity;
import io.ztoken.portal.session.PortalPrincipal;
import io.ztoken.portal.console.DashboardSummary;
import io.ztoken.portal.console.TokenList;
import io.ztoken.portal.catalog.ModelCatalog;

public interface NewApiClient {

    NewApiLogin login(String username, String password);

    void register(String username, String email, String password);

    NewApiIdentity getSelf(PortalPrincipal principal);

    DashboardSummary getDashboard(PortalPrincipal principal);

    TokenList getTokens(PortalPrincipal principal);

    ModelCatalog getModelCatalog();
}
