package com.thiru.wealthlens.auth.service;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import com.thiru.wealthlens.auth.entity.UserDetail;
import com.thiru.wealthlens.auth.repository.UserDetailsRepository;
import com.thiru.wealthlens.shared.privacy.LastSignIn;
import com.thiru.wealthlens.shared.privacy.RegisteredUsers;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

/**
 * Every registered account's email, for a breach notice addressed to everyone (W-237c), and when each was last signed in to,
 * for erasure once its purpose lapses (W-249).
 */
@Service
@RequiredArgsConstructor
public class RegisteredUserDirectory implements RegisteredUsers {

    private static final String LAST_SIGNED_IN_AT = "last_signed_in_at";

    private final UserDetailsRepository repository;
    private final MongoTemplate mongoTemplate;

    @Override
    public List<String> emails() {
        return repository.findAll().stream().map(UserDetail::getEmail).toList();
    }

    /** A targeted update, so a sign-in never overwrites a password changed at the same moment. */
    public void recordSignIn(String email, LocalDateTime atUtc) {
        mongoTemplate.updateFirst(query(where("_id").is(email)), new Update().set(LAST_SIGNED_IN_AT, atUtc), UserDetail.class);
    }

    @Override
    public void startCounting(LocalDateTime nowUtc) {
        mongoTemplate.updateMulti(query(where(LAST_SIGNED_IN_AT).exists(false)), new Update().set(LAST_SIGNED_IN_AT, nowUtc),
                UserDetail.class);
    }

    @Override
    public List<LastSignIn> signedInLastBy(LocalDateTime cutoffUtc) {
        return mongoTemplate.find(query(where(LAST_SIGNED_IN_AT).lte(cutoffUtc)), UserDetail.class).stream()
                .map(user -> new LastSignIn(user.getEmail(), user.getLastSignedInAt())).toList();
    }
}
