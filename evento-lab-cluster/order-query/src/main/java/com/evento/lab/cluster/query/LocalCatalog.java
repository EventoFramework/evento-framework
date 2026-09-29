package com.evento.lab.cluster.query;

import com.evento.lab.cluster.support.store.CatalogStore;

/** The cell's own copy of {@code catalog_price}, read by {@code FindPriceQuery}. */
public record LocalCatalog(CatalogStore store) {
}
