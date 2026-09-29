package com.evento.lab.cluster.gateway;

import com.evento.lab.cluster.support.LabDatabases;
import com.evento.lab.cluster.support.store.CatalogStore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Direct, read-only access to each {@code DATA_TARGETS} database, for {@code ?db=<name>}. */
public class PriceReaders {

    private final Map<String, CatalogStore> readers = new LinkedHashMap<>();

    public PriceReaders(LabDatabases databases, String instanceId) {
        databases.targets().forEach((name, ds) -> readers.put(name, new CatalogStore(name, ds, instanceId)));
    }

    public CatalogStore get(String name) {
        return readers.get(name);
    }

    public List<String> names() {
        return List.copyOf(readers.keySet());
    }
}
