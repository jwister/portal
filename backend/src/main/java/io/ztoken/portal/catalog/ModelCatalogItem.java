package io.ztoken.portal.catalog;

import java.util.List;

public record ModelCatalogItem(String name, String vendor, List<String> groups, Double inputPrice, Double outputPrice, Double cachePrice, boolean priceAvailable) {
}
