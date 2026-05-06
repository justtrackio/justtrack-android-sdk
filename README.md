# justtrack SDK

The [justtrack SDK](https://justtrack.io/) is a marketing SDK with full MMP (Mobile Measurement Partner) capabilities for your Android application. It provides attribution, event tracking, ad revenue forwarding, in-app purchase tracking, retargeting, remote config, and more.

You can find the complete documentation at <https://docs.justtrack.io/sdk/overview/>.

## Requirements

| Property    | Version          |
|-------------|------------------|
| Min SDK     | 21 (Android 5.0) |
| Compile SDK | 35               |
| Kotlin      | 1.9.20           |

## Installation

In your `settings.gradle`, add the justtrack Maven repository to `dependencyResolutionManagement`:

```kotlin
dependencyResolutionManagement {
  repositories {
    // ... your other repositories
    maven(url = "https://sdk.justtrack.io/maven")
  }
}
```

Then, in your module-level `build.gradle`, add the SDK dependency:

```kotlin
dependencies {
    implementation("io.justtrack:justtrack-android-sdk:7.1.0")
}
```

## Getting Started

### Instantiate the SDK

In your main activity class, or the class where you want to use the SDK, create an instance variable `sdk` for the `JustTrackSdk`.

```kotlin
class MainActivity : Activity() {
    private lateinit var sdk: JustTrackSdk
    override fun onCreate(savedInstanceState: Bundle?) {
        // ...
        sdk = JustTrackSdkBuilder(this, BuildConfig.JUSTTRACK_SDK_API_TOKEN).build()
    }

    override fun onDestroy() {
        sdk.shutdown()
        // ...
    }
}
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for details on the project structure, prerequisites, quality checks, and contribution workflow.

## Support

If you have any problems or issues with the SDK, feel free to reach out directly via [support@justtrack.io](mailto:support@justtrack.io).

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
