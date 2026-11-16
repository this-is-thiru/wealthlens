package com.thiru.wealthlens.shared.entity.model;

import org.springframework.data.domain.Persistable;

/**
 * An entity given its id before its first save (W-329b, D-725). Spring Data takes an entity with an id to be stored
 * already, so auditing would fill only its update fields; this says whether it is new instead. Its creation path sets
 * {@code newlyCreated} beside the id, and {@link CreatedWithIdSaved} clears it once saved, so a later save of the same
 * instance is an update. One saved with no id is new, as Spring Data would judge it; loaded from the database it is never
 * new.
 */
public interface CreatedWithId extends Persistable<String> {

    /** Marks the entity as not yet stored, or, once saved, as stored. */
    void setNewlyCreated(boolean newlyCreated);
}
