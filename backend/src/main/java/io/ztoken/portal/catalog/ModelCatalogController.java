package io.ztoken.portal.catalog;

import io.ztoken.portal.newapi.NewApiClient;
import io.ztoken.portal.newapi.NewApiRawResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog")
public class ModelCatalogController {
    private final NewApiClient newApiClient;

    public ModelCatalogController(NewApiClient newApiClient) {
        this.newApiClient = newApiClient;
    }

    /** 透传模型广场汇率和导航配置使用的公开状态响应。 */
    @GetMapping("/status")
    public ResponseEntity<byte[]> status() {
        return passthrough(newApiClient.getModelSquareStatus());
    }

    /** 透传 NewAPI 模型广场的完整定价响应，避免 Portal 自行换算字段。 */
    @GetMapping("/pricing")
    public ResponseEntity<byte[]> pricing() {
        return passthrough(newApiClient.getPricing());
    }

    /** 透传模型广场列表卡片使用的性能汇总数据。 */
    @GetMapping("/perf-metrics/summary")
    public ResponseEntity<byte[]> performanceSummary(@RequestParam MultiValueMap<String, String> query) {
        return passthrough(newApiClient.getPerformanceSummary(query));
    }

    /** 透传模型详情使用的性能数据，并保留全部查询参数。 */
    @GetMapping("/perf-metrics")
    public ResponseEntity<byte[]> performanceMetrics(@RequestParam MultiValueMap<String, String> query) {
        return passthrough(newApiClient.getPerformanceMetrics(query));
    }

    private ResponseEntity<byte[]> passthrough(NewApiRawResponse upstream) {
        return ResponseEntity.status(upstream.status()).contentType(upstream.contentType()).body(upstream.body());
    }
}
