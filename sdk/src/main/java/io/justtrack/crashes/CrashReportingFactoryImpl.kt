package io.justtrack.crashes

import io.justtrack.JtCrashReporter

internal class CrashReportingFactoryImpl(
    private val jtCrashReporter: JtCrashReporter,
) : CrashReportingFactory {
    override fun create(): CrashReporter = jtCrashReporter
}
