package io.justtrack

import io.justtrack.util.ExcludeFromJacocoGeneratedReport

@ExcludeFromJacocoGeneratedReport
internal object IntegrationManager {
    /**
     * Called from Unity when we enable debug logs for the UnityJavaProxy. This will cause a log message
     * to be written for each Java -> C# method call, allowing you to see the last call on the crashing
     * thread.
     */
    @Suppress("unused")
    @JvmStatic
    @JvmName("enableUnityJavaProxyDebugging")
    fun enableUnityJavaProxyDebugging() {
        try {
            val clazz = Class.forName("com.unity3d.player.ReflectionHelper")
            val field = clazz.getDeclaredField("LOG")
            field.isAccessible = true
            field[null] = true
        } catch (exception: ReflectiveOperationException) {
            throw ReflectiveOperationException("Could not set ReflectionHelper.LOG to true", exception)
        }
    }
}
