package com.thiru.wealthlens.shared.entity.model;

import jakarta.persistence.PostPersist;

/**
 * Clears a {@link CreatedWithId}'s creation flag once it is saved, so saving the same instance again updates it.
 *
 * <p>Named (W-337f) on an entity with
 * {@code @EntityListeners(CreatedWithIdSaved.class)} (alongside {@code AuditingEntityListener.class}). Hibernate
 * instantiates an entity listener by reflection and calls its {@code @PostPersist} method with the entity itself, so
 * this takes {@code Object} rather than {@code CreatedWithId} — one listener class is shared across every entity
 * that implements the interface, not just one.
 */
public class CreatedWithIdSaved {

    @PostPersist
    public void onPostPersist(Object entity) {
        if (entity instanceof CreatedWithId createdWithId) {
            createdWithId.setNewlyCreated(false);
        }
    }
}
