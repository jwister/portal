package io.ztoken.portal.newapi;

import io.ztoken.portal.session.PortalPrincipal;

/** 将已保存的 NewAPI refresh token 换成新的短期访问令牌。 */
public interface NewApiSessionRefresher {

    NewApiCredentials refresh(PortalPrincipal principal);
}
