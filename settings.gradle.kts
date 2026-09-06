pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "danmaku"

include(":shared:domain")
include(":shared:library-client")
include(":shared:library-client-android")
include(":shared:app-update-android")
include(":shared:player-android-media3")
include(":apps:android-mobile")
include(":apps:android-tv")
include(":apps:android-tv-benchmark")
