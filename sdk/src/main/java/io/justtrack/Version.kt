package io.justtrack

/**
 * Simple data type describing an app or SDK version.
 */
interface Version {
    /**
     * Get the major version number.
     *
     * @return Major version number.
     */
    val major: Int

    /**
     * Get the minor version number.
     *
     * @return Minor version number.
     */
    val minor: Int

    /**
     * Get the patch version number.
     *
     * @return Patch version number.
     */
    val patch: Int

    /**
     * Get a string representation of the version.
     *
     * @return A string representation of the version.
     */
    val name: String
}
