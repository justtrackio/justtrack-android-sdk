package io.justtrack

import androidx.annotation.VisibleForTesting

internal interface EntityRepository<T> : AutoCloseable {
    /**
     * store entity in database
     * @param data the entity to be store. Note, the entity class must be annotate with @Entity.
     * @return The auto-generated ID of the inserted entity
     */
    suspend fun storeEntity(data: T): Long?

    /**
     * store entities in database, if needed it can also return List<Long> as well.
     * @param dataList the list of entity to be store. Note, the entity class must be annotate with @Entity.
     */
    suspend fun storeEntities(dataList: List<T>)

    /**
     * removing entity by comparing the entity's creation timestamp(timestampInMS) and the provided cutOffMS.
     * If the created timestamp is older than cutOffMS, then the entity will be remove.
     * @param cutoffMS the millisecond that determine the cutoff date
     */
    suspend fun removeEntitiesByDate(cutoffMS: Long)

    /**
     * Retrieving a list of up to 100 entities from the database ordering by their creation date.
     * Also retrieving only "unmark" entity as well. Meaning they are not under ongoing process
     * which is indicated by having their "processingTimeInMS" as -1. The following list of entity
     * then will be mark as undergoing processing, by altering the "processingTimeInMS" to be the current time.
     * @param batchSize the maximum size of the list to be retrieve. By default it is 100.
     * @return A list of entity that is being mark and ready to be send to server.
     */
    suspend fun fetchNextBatchAndMark(batchSize: Int = 100): List<T>

    /**
     * Receiving the list of entity by their id to "mark" them. Meaning to update the following
     * list of entity "processingTimeInMS" to the current time.
     * @param idList a list of id of the entity, to be marked.
     */
    suspend fun markEntitiesById(idList: List<Long>)

    /**
     * Receiving the list of entity by their id to "unmark" them. Meaning to update the following
     * list of entity "processingTimeInMS" back to -1. Which could mean that the server process the list
     * and rejected them for some reason. Unmarking these entities mean that it can be selected for processing again.
     * @param idList a list of id of the entity, to be unmark.
     */
    suspend fun unMarkEntitiesById(idList: List<Long>)

    /**
     * Removing a list of entity by their id.
     * @param dataList a list of id of the entity, to be removing from database
     */
    suspend fun deleteEntities(dataList: List<T>)

    @VisibleForTesting
    suspend fun getAll(): List<T>

    @VisibleForTesting
    suspend fun getAllUnMark(): List<T>
}
