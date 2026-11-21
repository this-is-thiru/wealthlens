package com.thiru.wealthlens.portfolio.service;

import com.thiru.wealthlens.shared.dto.user.UserMail;
import com.thiru.wealthlens.shared.entity.query.QueryBuilder;
import com.thiru.wealthlens.shared.entity.query.QueryFilter;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/**
 * Read-only MongoDB query service. Uses {@link MongoTemplate} directly; no
 * class-level @{@link Transactional} because every operation here is a
 * read and wrapping reads in a transaction is unnecessary overhead.
 */
@Log4j2
@AllArgsConstructor
@Service
public class MongoTemplateService {

    private final MongoTemplate mongoTemplate;

    public <T> List<T> getDocuments(UserMail userMail, List<QueryFilter> queryFilters, Class<T> clazz) {
        return mongoTemplate.find(query(userMail, queryFilters), clazz);
    }

    /** One page of the matches, in the page's order, with the count of all of them (W-358c3b). */
    public <T> Page<T> getDocuments(UserMail userMail, List<QueryFilter> queryFilters, Class<T> clazz, Pageable pageable) {
        Query query = query(userMail, queryFilters);
        long total = mongoTemplate.count(query, clazz);
        return new PageImpl<>(mongoTemplate.find(query.with(pageable), clazz), pageable, total);
    }

    private static Query query(UserMail userMail, List<QueryFilter> queryFilters) {
        QueryFilter.validateAndSanitizationOfQueryFilters(queryFilters, userMail);
        QueryBuilder queryBuilder = new QueryBuilder();
        queryFilters.forEach(queryBuilder::addAsCriteria);
        return queryBuilder.build();
    }
}
