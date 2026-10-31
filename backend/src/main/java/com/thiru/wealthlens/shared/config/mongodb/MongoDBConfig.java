package com.thiru.wealthlens.shared.config.mongodb;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

@Configuration
@EnableMongoAuditing(auditorAwareRef = "securityAuditorAware")
public class MongoDBConfig {
}
