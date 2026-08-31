package io.ztoken.portal.catalog;

public record ModelCatalogItem(String name, String vendor, String group, Double inputPrice, Double outputPrice, Double cachePrice, boolean priceAvailable) {
}
