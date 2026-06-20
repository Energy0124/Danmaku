plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":shared:domain"))
    implementation(project(":shared:library-host-core"))
    implementation(project(":shared:library-server-core"))

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
}

application {
    mainClass.set("app.danmaku.server.windows.MainKt")
}
