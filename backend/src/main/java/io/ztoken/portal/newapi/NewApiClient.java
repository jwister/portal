package io.ztoken.portal.newapi;

import io.ztoken.portal.session.NewApiIdentity;
import io.ztoken.portal.session.PortalPrincipal;

public interface NewApiClient {

    NewApiLogin login(String username, String password);

    NewApiIdentity getSelf(PortalPrincipal principal);
}
