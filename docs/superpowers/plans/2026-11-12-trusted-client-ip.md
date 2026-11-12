# 客户端 IP 透传与限流响应实施计划

**目标：** 修复 Portal 转发时丢失真实来源 IP，并向前端保留上游 429 和等待时间。

**方案：** 按用户“别搞复杂”的要求，复用 Spring Boot/Tomcat 的 native 代理链解析，不新增自定义 IP 解析器或管理页面。统一的 NewApiClientIp 函数在每次 WebClient 请求构建时读取 Servlet remoteAddr；只向 NewAPI 添加 X-Forwarded-For/X-Real-IP，不解析原始客户端头，没有请求上下文时不附加来源。NewAPI 现有 TRUSTED_PROXIES 配置支持此功能，不改其业务代码。

- [x] 补充并发来源隔离、容器代理链解析、后台不透传及服务令牌不变测试。
- [x] 接入 NewApiHttpClient、HttpNewApiCreditClient；配置 native 解析与可选的公网代理地址正则。
- [x] JSON 解析前识别 429，释放响应体，校验 Retry-After，返回 429/no-store；不自动重放 OAuth 或支付写请求。
- [x] 登录、注册和 OAuth 回调共用倒计时提示；等待期间禁止重复提交。
- [x] 补充部署说明；Portal 出口已知为 95.41.67.11，CDN/入口代理配置需核对真实部署。
- [x] 运行相关回归与前端构建、代码审查；提交推送前端及 Portal 子模块引用。

运行时前提：入口代理正确确认 CDN 的客户端地址；NewAPI 只信任受控转发来源。默认不会信任任意公网来源；本地代码测试不能替代线上 CDN/Nginx 配置检查。

验证结果：前端相关 65 项测试通过，生产构建、预渲染与产物校验通过；Java 上游客户端、认证、入账与会话相关测试通过；代码审查未发现必修问题。线上 CDN 回源配置仍需部署时核对。
