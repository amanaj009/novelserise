/*
 * Copyright (c) 2026 Aman AJ. All rights reserved.
 *
 */

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://oss.sonatype.org/content/repositories/releases") }
        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots") }
        maven { url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots") }
    }
}

rootProject.name = "NovelSeries"

include(":app")

// A plain clone does not initialize Git submodules. Download the shared library
// automatically on the first Gradle sync so the project opens without requiring
// a separate MyApplication checkout.
if (!file("storymania/build.gradle.kts").isFile) {
    check(file(".gitmodules").isFile && file(".git").exists()) {
        "Missing storymania library. Clone with --recurse-submodules."
    }

    val storymaniaFetch = providers.exec {
        workingDir(rootDir)
        commandLine("git", "submodule", "update", "--init", "--recursive", "--", "storymania")
        isIgnoreExitValue = true
    }

    check(
        storymaniaFetch.result.get().exitValue == 0 &&
            file("storymania/build.gradle.kts").isFile
    ) {
        "Could not download storymania: ${storymaniaFetch.standardError.asText.get().trim()}"
    }
}

include(":storymania")
project(":storymania").projectDir = file("storymania")
