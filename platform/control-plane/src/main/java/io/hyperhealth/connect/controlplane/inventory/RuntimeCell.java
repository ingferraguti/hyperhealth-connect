package io.hyperhealth.connect.controlplane.inventory;

import java.util.Objects;
import java.util.Set;

import io.hyperhealth.connect.controlplane.inventory.InventoryId.RuntimeCellId;

/**
 * Technical failure and scaling domain with explicit inventory-scope assignments.
 *
 * <p>A cell is not a child of an endpoint or facility. An assignment to an ancestor authorizes all
 * descendants; an assignment to a leaf authorizes only that leaf. Multiple assignments, including
 * assignments in different tenants, are represented explicitly and can therefore be subjected to
 * deployment policy in later work packages.</p>
 */
public record RuntimeCell(RuntimeCellId id, Set<InventoryPath> authorizedScopes) {
    public RuntimeCell {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(authorizedScopes, "authorizedScopes");
        if (authorizedScopes.isEmpty()) {
            throw new IllegalArgumentException("A runtime cell requires at least one authorized scope");
        }
        if (authorizedScopes.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("A runtime cell scope cannot be null");
        }
        authorizedScopes = Set.copyOf(authorizedScopes);
    }

    public static RuntimeCell create(Set<InventoryPath> authorizedScopes) {
        return new RuntimeCell(RuntimeCellId.newId(), authorizedScopes);
    }

    public boolean isAuthorizedFor(ScopedInventoryResource resource) {
        Objects.requireNonNull(resource, "resource");
        return isAuthorizedFor(resource.path());
    }

    public boolean isAuthorizedFor(InventoryPath requestedScope) {
        Objects.requireNonNull(requestedScope, "requestedScope");
        return authorizedScopes.stream().anyMatch(scope -> scope.contains(requestedScope));
    }
}
