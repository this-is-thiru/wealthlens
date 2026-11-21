package com.thiru.wealthlens.shared.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Routes for a {@code SUPER_USER} only; a route acting on one user names them ({@code /users/{email}/…}, D-855 C3a). Spring adds `/api/v1/admin/{module}` to every route of the annotated controller (`ApiZones`, D-853 C4a, D-855), so the
 * access rule follows the class, not a list of paths.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface AdminApi {
}
