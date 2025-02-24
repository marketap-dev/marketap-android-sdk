pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS) // FAIL_ON_PROJECT_REPOS 대신 사용
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") // JitPack에서 가져올 수 있도록 추가
    }
}

rootProject.name = "android"
include(":app")
include(":sdk")
