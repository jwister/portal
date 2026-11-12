package io.ztoken.portal.config;

import io.netty.util.NetUtil;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.reactive.function.client.WebClient;

/** 仅向 NewAPI 继续传递容器已验证的客户端地址，不自行解析或照搬浏览器的代理头。 */
public final class NewApiClientIp {
    private NewApiClientIp() { }

    /** 在构建每个请求时捕获当前 Servlet 来源，避免异步发送时丢失上下文或跨用户污染。 */
    public static void forward(WebClient.RequestHeadersSpec<?> request) {
        request.headers(headers -> {
            headers.remove("Forwarded");
            headers.remove("X-Forwarded-For");
            headers.remove("X-Real-IP");
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
                String ip = attributes.getRequest().getRemoteAddr();
                if (ip != null && (NetUtil.isValidIpV4Address(ip) || NetUtil.isValidIpV6Address(ip))) {
                    headers.set("X-Forwarded-For", ip);
                    headers.set("X-Real-IP", ip);
                }
            }
            // 定时任务、支付补偿等没有当前 HTTP 请求时，不伪造客户端来源。
        });
    }
}
