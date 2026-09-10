package io.ztoken.portal.newapi;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;

/**
 * NewAPI 模型广场响应的原始 HTTP 语义，Portal 不得转换其中的响应体。
 */
public record NewApiRawResponse(HttpStatusCode status, MediaType contentType, byte[] body) {
}
