package io.ztoken.portal.catalog;

import io.ztoken.portal.newapi.NewApiClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog")
public class ModelCatalogController {
    private final NewApiClient newApiClient;

    public ModelCatalogController(NewApiClient newApiClient) {
        this.newApiClient = newApiClient;
    }

    @GetMapping("/models")
    public ModelCatalog models() {
        return newApiClient.getModelCatalog();
    }
}
