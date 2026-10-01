package io.justtrack.api

import io.justtrack.log.Logger
import io.justtrack.log.LoggerFields
import io.justtrack.Metric
import io.justtrack.okhttp.Headers

internal class TestApiLogger : Logger {
    override val fallback: Logger get() = this
    override fun debug(message: String, vararg fields: LoggerFields) = Unit
    override fun info(message: String, vararg fields: LoggerFields) = Unit
    override fun warn(message: String, vararg fields: LoggerFields) = Unit
    override fun warn(message: String, exception: Throwable, vararg fields: LoggerFields) = Unit
    override fun error(message: String, vararg fields: LoggerFields) = Unit
    override fun error(message: String, exception: Throwable, vararg fields: LoggerFields) = Unit
    override fun publishMetric(metric: Metric, value: Double, vararg dimensions: LoggerFields) = Unit
}

internal class TestHeaderProvider : HeaderProvider {
    override fun provideHeader(advertiserId: String?, uuid: String?, installId: String?, logger: Logger): Headers = Headers.Builder().build()

    override fun provideHeaderV2(advertiserId: String?, uuid: String?, installId: String?, logger: Logger): Headers = Headers.Builder().build()
}
