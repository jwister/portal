# Portal 模型广场接口透传设计

## 目标

让 Portal 的模型页面直接使用 NewAPI 模型广场的原始接口数据，避免 Portal 将模型价格和元数据转换为自身的简化结构后产生字段丢失或口径偏差。

## 范围

Portal 仅代理 NewAPI 模型广场当前实际调用的三个只读接口：

- `GET /api/pricing`
- `GET /api/perf-metrics/summary?hours=...`
- `GET /api/perf-metrics?model=...&group=...&hours=...`

对应的 Portal 路由固定为：

- `GET /api/catalog/pricing`
- `GET /api/catalog/perf-metrics/summary`
- `GET /api/catalog/perf-metrics`

模型配置、管理接口、任意路径代理和 NewAPI 源码修改均不在范围内。

## 架构与数据流

浏览器只访问 Portal 的三个固定路由。Portal 后端使用现有 `portal.new-api.base-url` 与 `portal.new-api.pricing-token` 请求相应的 NewAPI 路由，并将浏览器给出的查询参数逐项转发。上游的 HTTP 状态码、`Content-Type` 和 JSON 响应体原样返回；Portal 不解析、补全、过滤、排序或换算任何模型广场字段。

`/api/pricing` 会保留 `data`、`vendors`、`group_ratio`、`usable_group`、`supported_endpoint`、`auto_groups` 和 `pricing_version` 等完整上游响应。性能摘要与单模型性能接口同样返回完整上游 JSON，因此 Portal 与 NewAPI 在同一时刻获取到的数据结构和值一致。

前端模型页从新的原样价格接口读取数据。页面仍可仅展示当前已设计的字段，但不再依赖 `ModelCatalogItem` 或后端价格换算；后续模型广场字段新增时，前端可直接读取响应中的新字段。

## 安全与错误处理

代理仅允许上述三个 GET 路由，不能由客户端指定上游地址或路径。访问令牌只由 Portal 服务端附加为 Bearer 授权头，不会发送给浏览器。上游响应的非成功状态和 JSON 错误体将原样转发，以保持调用语义；网络连接或超时等无法取得上游响应的错误继续使用 Portal 的统一安全错误响应，不输出令牌或上游配置。

## 验证

后端集成测试使用 MockWebServer，分别验证每个 Portal 路由：

1. 请求路径和全部查询参数正确转发；
2. 授权头仅由服务端加入；
3. 成功响应的状态码、内容类型和 JSON 字节内容未被转换；
4. 上游非成功响应同样保留状态码和错误 JSON。

前端测试将模拟完整的 NewAPI `pricing` 响应，确认模型页从原始 `data`、`vendors` 与分组信息渲染和筛选，而不是读取已废弃的简化 DTO。
