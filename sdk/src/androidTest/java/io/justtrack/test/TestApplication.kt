package io.justtrack.test

import android.app.Application

class TestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Set the nonLocalizedLabel here
        applicationInfo.nonLocalizedLabel = "Test App Label"
    }
}
