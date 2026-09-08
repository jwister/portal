package io.ztoken.portal.payment.api;

import io.ztoken.portal.payment.trc20.VerificationResult;

/** TxID 即时查询的安全结果，不把第三方响应或密钥暴露给浏览器。 */
public record TxidVerificationResponse(VerificationResult result) {
}
