package com.evento.lab.versioning;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** The read model of the versioning test. */
public final class ItemStore {
    public static final Map<String, ItemView> items = new ConcurrentHashMap<>();
    public static final List<String> renames = new CopyOnWriteArrayList<>();

    private ItemStore() {}

    public static void reset() {
        items.clear();
        renames.clear();
    }
}
