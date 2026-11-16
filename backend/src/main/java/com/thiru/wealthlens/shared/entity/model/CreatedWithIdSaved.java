package com.thiru.wealthlens.shared.entity.model;

import org.bson.Document;
import org.springframework.data.mongodb.core.mapping.event.AfterSaveCallback;
import org.springframework.stereotype.Component;

/** Clears a {@link CreatedWithId}'s creation flag once it is saved, so saving the same instance again updates it. */
@Component
public class CreatedWithIdSaved implements AfterSaveCallback<CreatedWithId> {

    @Override
    public CreatedWithId onAfterSave(CreatedWithId entity, Document document, String collection) {
        entity.setNewlyCreated(false);
        return entity;
    }
}
