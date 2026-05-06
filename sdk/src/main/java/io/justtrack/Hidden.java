package io.justtrack;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Use this annotation to mark things as non-public which are still public for some reason (like static
 * fields in an interface). The ProGuardGenerator will take this into account and not generate keep
 * rules for fields annotated with this interface.
 */
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.SOURCE)
@interface Hidden {
}
