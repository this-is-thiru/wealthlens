package com.thiru.wealthlens.taxplanning.workspace;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FilingDraftRepository extends MongoRepository<FilingDraft, String> {

    List<FilingDraft> findByWorkspaceId(String workspaceId);
}
