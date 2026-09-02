package io.ztoken.portal.console;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProfileUpdateRequest(
        String displayName,
        String language
) {}
