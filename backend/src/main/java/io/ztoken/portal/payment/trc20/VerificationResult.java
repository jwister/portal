package io.ztoken.portal.payment.trc20;

/** 统一表达自动扫描和用户提交 TxID 后的链上核验结论。 */
public enum VerificationResult {
    CONFIRMED,
    PENDING_CONFIRMATION,
    AMOUNT_MISMATCH,
    UNMATCHED,
    DUPLICATE
}
