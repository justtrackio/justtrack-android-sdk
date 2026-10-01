package io.justtrack.api

import io.justtrack.JSONEncodable
import io.justtrack.log.Logger

internal open class DefaultLogApi : LogApi {
    override suspend fun sendLogs(logger: Logger, body: JSONEncodable, advertiserId: String?, uuid: String?, installId: String?): Result<Unit> {
        return Result.success(Unit)
    }
}
