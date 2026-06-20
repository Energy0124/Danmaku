plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":shared:domain"))
    implementation(project(":shared:library-host-core"))
    implementation(project(":shared:library-server-core"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    testImplementation(kotlin("test"))
}

application {
    mainClass.set("app.danmaku.server.windows.MainKt")
}
