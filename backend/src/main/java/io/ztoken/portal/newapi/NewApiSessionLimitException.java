package io.ztoken.portal.newapi;

/** NewAPI 拒绝创建会话，因为该账户的活跃登录会话已达到上限。 */
public class NewApiSessionLimitException extends NewApiException {
    public NewApiSessionLimitException() {
        super("NewAPI active login session limit reached");
    }
}
