package com.thiru.wealthlens.auth.repository;

import com.thiru.wealthlens.auth.entity.UserDetail;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserDetailsRepository extends MongoRepository<UserDetail, String> {

	Optional<UserDetail> findByEmail(String email);
}
