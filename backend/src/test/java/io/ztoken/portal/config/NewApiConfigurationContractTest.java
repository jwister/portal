package io.ztoken.portal.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class NewApiConfigurationContractTest {

    @Test
    void developmentConfigUsesTheNewApiServiceAddressInsteadOfThePortalSite() throws IOException {
        String configuration = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(configuration)
                .contains("base-url: https://api.ztoken.cc")
                .contains("access-token: ${PORTAL_NEWAPI_ACCESS_TOKEN:${PAYMENT_NEWAPI_CREDIT_ACCESS_TOKEN:Lgh3Nj3cprP15x9AsfwLF75qaT/yPA==}}")
                .doesNotContain("pricing-token:")
                .doesNotContain("admin-token:")
                .doesNotContain("NEWAPI_BASE_URL")
                .doesNotContain("NEWAPI_PRICING_TOKEN");
    }
}
