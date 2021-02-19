group = "ru.senin.kotlin.net"

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.10.2")

    implementation("org.jetbrains.exposed:exposed-core:0.29.1")

    val ktor_version: String by project
    testImplementation("io.ktor:ktor-server-tests:$ktor_version")
}