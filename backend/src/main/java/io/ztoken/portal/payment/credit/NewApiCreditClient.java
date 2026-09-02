package io.ztoken.portal.payment.credit;

public interface NewApiCreditClient {

    CreditResult addQuota(long userId, long quota);
}
