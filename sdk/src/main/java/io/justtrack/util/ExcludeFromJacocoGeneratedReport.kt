package io.justtrack.util

/**
 * Marker annotation excluding the annotated declaration from the generated JaCoCo coverage report.
 *
 * Apply to classes, functions, constructors, or property accessors whose behaviour is exercised
 * indirectly (e.g. via the Android framework) and therefore cannot meaningfully be unit-tested.
 */
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
)
internal annotation class ExcludeFromJacocoGeneratedReport
