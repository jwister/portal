package io.ztoken.portal.auth;

import io.ztoken.portal.console.TokenList;
import io.ztoken.portal.console.TokenWriteRequest;
import io.ztoken.portal.newapi.NewApiClient;
import io.ztoken.portal.newapi.NewApiLogin;
import io.ztoken.portal.session.PortalPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 负责新用户初始化及会话接入时默认 API Key（令牌）的自动创建与幂等保障。
 */
@Service
public class DefaultTokenService {

    private static final Logger log = LoggerFactory.getLogger(DefaultTokenService.class);

    public static final String DEFAULT_TOKEN_NAME = "default";
    public static final String DEFAULT_TOKEN_GROUP = "default";

    private final NewApiClient newApiClient;

    public DefaultTokenService(NewApiClient newApiClient) {
        this.newApiClient = newApiClient;
    }

    /**
     * 在新用户通过账号密码或邮箱注册成功后，使用注册凭据临时登录并自动创建名为 default 的默认令牌。
     * 无论创建成功或失败，均在 finally 中安全注销临时会话，异常绝不抛出打断注册主流程。
     *
     * @param username 用户名
     * @param password 注册密码
     */
    public void initializeDefaultTokenOnRegister(String username, String password) {
        PortalPrincipal principal = null;
        try {
            log.info("开始为新注册用户 {} 初始化默认令牌", username);
            NewApiLogin login = newApiClient.login(username, password);
            principal = toPrincipal(login);
            ensureDefaultTokenExists(principal);
        } catch (Exception exception) {
            log.warn("为新注册用户 {} 自动初始化默认令牌失败: {}", username, exception.getMessage());
        } finally {
            if (principal != null) {
                try {
                    newApiClient.logout(principal);
                } catch (Exception ignored) {
                    // 登出仅为回收临时上游凭据，异常不予处理
                }
            }
        }
    }

    /**
     * 检查并确保该用户存在默认 API Key。如果令牌总数为 0，则自动为其创建名为 default 的无限额度令牌。
     *
     * @param principal 当前认证用户
     */
    public void ensureDefaultTokenExists(PortalPrincipal principal) {
        if (principal == null) {
            return;
        }
        try {
            TokenList tokens = newApiClient.getTokens(principal, 1, 1);
            if (tokens != null && tokens.total() > 0) {
                log.debug("用户 {} 已存在 {} 个令牌，跳过默认令牌创建", principal.username(), tokens.total());
                return;
            }
            TokenWriteRequest request = new TokenWriteRequest(
                    DEFAULT_TOKEN_NAME,
                    true,      // 无限额度
                    0L,        // 额度值（在无限额度模式下无效）
                    -1L,       // 永不过期
                    DEFAULT_TOKEN_GROUP
            );
            newApiClient.createToken(principal, request);
            log.info("已成功为用户 {} 自动创建默认 API Key: {}", principal.username(), DEFAULT_TOKEN_NAME);
        } catch (Exception exception) {
            log.warn("为用户 {} 检查或创建默认令牌失败: {}", principal.username(), exception.getMessage());
        }
    }

    /**
     * 将 NewApiLogin 登录信息转换为请求级别的 PortalPrincipal 凭证
     *
     * @param login 上游登录响应
     * @return 临时用户主体凭据
     */
    public PortalPrincipal toPrincipal(NewApiLogin login) {
        return new PortalPrincipal(
                "temp-session-" + login.identity().userId(),
                login.identity().userId(),
                login.identity().username(),
                login.accessToken(),
                login.refreshToken(),
                login.sessionId(),
                login.accessExpiresAt()
        );
    }
}
