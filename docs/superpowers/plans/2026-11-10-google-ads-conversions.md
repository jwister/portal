# Google Ads 注册与购买转化实施计划

**目标：** 邮箱、GitHub、Google 新建账号后上报注册；服务端确认 PAID 后按订单金额、币种与唯一订单号上报购买。

**方案：** NewAPI 在实际 OAuth 创建事务成功后设置请求级新用户标识，成功登录响应附加 is_new_user，默认 false。Portal 只返回 isNewUser，不返回上游令牌。支付响应增加 conversionValue、conversionCurrency；当前美元订单统一按 amountUsdMinor / 100 与 USD 上报，包括 USDT 的美元购买价值，不包含识别尾差。前端集中初始化 Ads 并封装上报、去重与跳转超时。

**技术栈：** Go/Gin、Java/Spring Boot、React/TypeScript、Vitest、JUnit。

- [x] 扩展 NewAPI controller/auth_session_test.go，验证新建、已有账号、失败创建与登录响应；运行定向 Go 测试确认缺失行为。
- [x] 修改 NewAPI controller/oauth.go、controller/user.go，在创建事务提交后设置 oauth_new_user，响应读取该布尔值；保留原认证、MFA、state、cookie 流程；按用户要求在 dev 分支保留修改，main 不变。
- [x] 扩展 Portal NewApiHttpClientTest、AuthControllerTest、PaymentOrderControllerTest，验证新用户标识透传、缺省 false、凭据隔离及订单转化金额。
- [x] 修改 NewApiLogin、NewApiHttpClient、AuthController 与 PaymentOrderResponse，保持原构造方式兼容；OAuth 返回 JSON 与原会话 cookie。
- [x] 新增前端 support/google-ads.test.ts，验证队列、初始化幂等、购买金额/币种、重复订单、脚本失败与注册跳转超时。
- [x] 修改 support/load-tracking.ts、main.tsx 与新增 google-ads.ts，在首屏初始化命令队列、按需加载脚本，继续使用已有 dataLayer。
- [x] 扩展并运行注册、OAuth 回调与支付完成页测试，接入统一上报函数；邮箱成功和新 OAuth 用户上报，老用户与失败不上报，支付仅 PAID 上报。
- [x] 运行前端构建、相关前后端回归与 Go 测试；审查 diff，保留用户未跟踪文件。

验收限制：本地自动化验证事件调用与业务条件；线上 GTM 容器配置和 Ads 接收/归因需部署后通过 Tag Assistant 检查。Google event_callback 只表示事件处理结束，不代表 Ads 服务端确认接收。

认证参考：OWASP Authentication Cheat Sheet、Session Management Cheat Sheet；本次仅增加非敏感响应元数据，保持 state 单次消费、现有认证验证与会话凭据服务端存储。

实际验证：前端相关回归 12 个文件共 69 项通过；Portal 后端 AuthControllerTest、NewApiHttpClientTest、PaymentOrderControllerTest、PaymentOrderServiceTest 共 85 项通过；NewAPI OAuth 新用户、state/flow 与会话定向测试通过；前端 TypeScript、生产构建、预渲染与产物预算校验通过。代码审查发现的 OAuth URL 授权参数采集与脚本下载失败无法重试问题均已修复并复核。

补充：公共 /purchase 到账后进入统一完成页。NewAPI 测试夹具同时关闭审计库，解决 Windows 临时文件清理失败；注册回跳测试模拟统计处理完成，独立统计测试验证两秒超时。
