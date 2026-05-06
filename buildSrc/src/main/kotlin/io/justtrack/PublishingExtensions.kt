package io.justtrack

import org.gradle.api.Project
import org.gradle.api.publish.maven.MavenPom

private const val POM_DESCRIPTION = "justtrack is AppLike Group's next level attribution & UA automation platform - built by app publishers for app publishers."
private const val POM_URL = "https://justtrack.io/"
private const val LICENSE_NAME = "MIT License"

fun Project.configurePom(
    pom: MavenPom,
    artifactId: String,
    versionName: String,
    displayName: String,
) {
    pom.name.set("justtrack $displayName Adapter SDK")
    pom.description.set(POM_DESCRIPTION)
    pom.url.set(POM_URL)

    pom.withXml {
        val root = asNode()
        val licensesNode = root.appendNode("licenses")
        val licenseNode = licensesNode.appendNode("license")
        licenseNode.appendNode("name", LICENSE_NAME)
        licenseNode.appendNode(
            "url",
            "https://sdk.justtrack.io/maven/io/justtrack/$artifactId/$versionName/$artifactId-$versionName-license.txt",
        )
        licenseNode.appendNode("distribution", "repo")
    }
}
