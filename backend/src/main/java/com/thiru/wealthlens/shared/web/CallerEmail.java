package com.thiru.wealthlens.shared.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The signed-in caller's email, from the token (D-855 C4a): a {@link MeApi} route reads its user here, never from its path, so no
 * caller can name another user's data.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CallerEmail {
}
