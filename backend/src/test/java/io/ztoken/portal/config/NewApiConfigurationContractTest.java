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
                .contains("pricing-token: Lgh3Nj3cprP15x9AsfwLF75qaT/yPA==")
                .doesNotContain("NEWAPI_BASE_URL")
                .doesNotContain("NEWAPI_PRICING_TOKEN");
    }
}
