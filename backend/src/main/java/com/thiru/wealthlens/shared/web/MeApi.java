package com.thiru.wealthlens.shared.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Routes for a signed-in caller: their own data, taken from the token ({@link CallerEmail}), or reference data any user reads (D-855 C2a). Spring adds `/api/v1/me/{module}` to every route of the annotated controller (`ApiZones`, D-853 C4a, D-855), so the
 * access rule follows the class, not a list of paths.
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MeApi {
}
