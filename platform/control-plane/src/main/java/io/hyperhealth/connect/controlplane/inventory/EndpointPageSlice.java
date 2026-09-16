package io.hyperhealth.connect.controlplane.inventory;

import java.util.List;

/** One deterministic keyset page; the repository never calculates a sensitive total count. */
public record EndpointPageSlice(List<ScopedEndpoint> items, boolean hasMore) {
    public EndpointPageSlice {
        items = List.copyOf(items);
        if (hasMore && items.isEmpty()) {
            throw new IllegalArgumentException("an empty page cannot have more items");
        }
    }
}
