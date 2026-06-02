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
include(":shared:library-server-core")
include(":shared:library-client-android")
include(":shared:player-android-media3")
include(":apps:desktop-windows")
include(":apps:android-mobile")
include(":apps:android-tv")
