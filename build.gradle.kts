plugins {
    id("java-library")
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.dokka)
    `maven-publish`
}

group = "com.wip"

val isCI = System.getenv("GITHUB_ACTIONS") == "true"
val runNumber = System.getenv("GITHUB_RUN_NUMBER") ?: "0"
val baseVersion = libs.versions.kpsd.get()

version = if (isCI && baseVersion.contains("-")) {
    "$baseVersion.$runNumber"
} else {
    baseVersion
}


java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21

    withSourcesJar()
    withJavadocJar()
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
    }
}

dependencies {
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.core)
}

tasks.test {
    useJUnit()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = "com.wip"
            artifactId = "kpsd"
            version = project.version.toString()
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Wip-Sama/kpsd")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: (project.findProperty("gpr.user") as? String) ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: (project.findProperty("gpr.key") as? String) ?: ""
            }
        }
    }
}
