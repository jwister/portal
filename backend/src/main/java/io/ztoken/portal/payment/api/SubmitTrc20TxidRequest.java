package io.ztoken.portal.payment.api;

import jakarta.validation.constraints.NotBlank;

/** 客户提交的公开链上交易哈希；服务端自行查询和核验其内容。 */
public record SubmitTrc20TxidRequest(@NotBlank String txid) {
}
