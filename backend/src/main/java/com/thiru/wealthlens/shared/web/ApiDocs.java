package com.thiru.wealthlens.shared.web;

import io.swagger.v3.oas.annotations.Parameter;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Pageable;

/**
 * What the generated API contract says about parameters springdoc cannot read from a signature (W-359b): the caller's email is taken
 * from the token, so it is no parameter at all, and a page is asked for by its number and size only — the order is the service's
 * (D-861 C3a), so the {@code sort} Spring's {@link Pageable} would advertise is not read.
 */
@Configuration
public class ApiDocs {

    static {
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CallerEmail.class).replaceParameterObjectWithClass(Pageable.class, PageQuery.class);
    }

    /** A page of a list that grows, as a caller asks for it. */
    record PageQuery(@Parameter(description = "Zero-based page number") Integer page,
            @Parameter(description = "Rows in the page; the server caps it") Integer size) {
    }
}
