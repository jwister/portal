package io.ztoken.portal.newapi;

import io.ztoken.portal.session.NewApiIdentity;

public record NewApiLogin(NewApiIdentity identity, String accessToken) {
}
