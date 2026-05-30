package com.customcontentengine.domain.definition;

import java.util.List;

public record DropTable(List<Entry> entries) {
    public DropTable {
        entries = List.copyOf(entries == null ? List.of() : entries);
    }

    public record Entry(String item, int amount) {
        public Entry {
            if (item == null || item.isBlank()) {
                throw new IllegalArgumentException("drop item must not be blank");
            }
            if (amount <= 0) {
                throw new IllegalArgumentException("drop amount must be positive");
            }
        }
    }
}
