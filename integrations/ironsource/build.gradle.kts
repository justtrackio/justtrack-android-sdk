import io.justtrack.configurePom
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.maven.publish)
}

val versionProps =
    Properties().apply {
        load(rootProject.file("sdk/deployment/version.properties").inputStream())
    }

val versionName = versionProps.getProperty("ANDROID_IRONSOURCE_ADAPTER_VERSION")

android {
    namespace = "io.justtrack.integrations.ironsource"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs =
            listOf(
                "-Xstring-concat=inline",
            )
    }
}

ktlint {
    version = "1.2.1"
    android = true // to use the Android Studio KtLint plugin style
    ignoreFailures = false
}

publishing {
    repositories {
        maven {
            credentials(AwsCredentials::class.java) {
                @Suppress("UNCHECKED_CAST")
                val loader = rootProject.extra["loadRequiredLocalProperties"] as groovy.lang.Closure<Properties>
                val properties = loader.call(rootProject.rootDir)

                accessKey = properties.getProperty("mavenPublicAwsAccessKeyId")
                secretKey = properties.getProperty("mavenPublicAwsSecretAccessKey")
                sessionToken = properties.getProperty("mavenPublicAwsSessionToken")
            }

            val isTest = project.findProperty("isTest")?.toString()?.toBoolean() ?: false

            if (versionName.contains("test") || versionName.contains("-dev") || isTest) {
                val repoUrl =
                    project.findProperty("mavenPublicTestUrl")?.toString()
                        ?: throw GradleException("mavenPublicTestUrl is not defined in gradle.properties")

                url = uri(repoUrl)
            } else if (versionName.contains("-rc")) {
                val repoUrl =
                    project.findProperty("mavenPublicUrl")?.toString()
                        ?: throw GradleException("mavenPublicTestUrl is not defined in gradle.properties")

                url = uri(repoUrl)
            } else if (versionName.contains("-")) {
                throw RuntimeException("Wrong version name format")
            } else {
                val repoUrl =
                    project.findProperty("mavenPublicUrl")?.toString()
                        ?: throw GradleException("mavenPublicTestUrl is not defined in gradle.properties")

                url = uri(repoUrl)
            }
        }
    }

    publications {
        register<MavenPublication>("release") {
            groupId = "io.justtrack"
            artifactId = "adapter-ironsource"
            version = versionName

            afterEvaluate {
                from(components["release"])
            }
            project.configurePom(
                pom = pom,
                artifactId = "adapter-ironsource",
                versionName = versionName,
                displayName = "IronSource",
            )

            artifact("${project.rootProject.rootDir}/LICENSE") {
                extension = "txt"
                classifier = "license"
            }
        }
    }
}

dependencies {
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    compileOnly(project(":sdk"))

    implementation(libs.integrations.ironsource)
}
