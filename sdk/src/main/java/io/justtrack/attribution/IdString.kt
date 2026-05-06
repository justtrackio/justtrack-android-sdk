package io.justtrack.attribution

/**
 * A tuple of id and name.
 */
interface IdString {
    /**
     * Get the id.
     *
     * @return The id.
     */
    val id: Int

    /**
     * Get the name.
     *
     * @return The name.
     */
    val name: String
}
