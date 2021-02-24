import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    id("org.jetbrains.kotlin.jvm").apply(false)
}

subprojects {
    version = "0.0.1"

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "application")

    configure<KotlinJvmProjectExtension> {
        sourceSets {
            getByName("main") {
                kotlin.setSrcDirs(listOf("src"))
                resources.setSrcDirs(listOf("resources"))
            }
            getByName("test") {
                kotlin.setSrcDirs(listOf("test"))
                resources.setSrcDirs(listOf("testresources"))
            }
            all {
                languageSettings.useExperimentalAnnotation("io.ktor.util.KtorExperimentalAPI")
            }
        }
        target.compilations.all {
            kotlinOptions.jvmTarget = "1.8"
            javaSourceSet.java.setSrcDirs(defaultSourceSet.kotlin.sourceDirectories)
            javaSourceSet.resources.setSrcDirs(defaultSourceSet.resources.sourceDirectories)
        }
    }

    repositories {
        mavenCentral()
        jcenter()
    }

    dependencies {
        val junit_version: String by project
        "testImplementation"(kotlin("test-junit5"))
        "testImplementation"("org.junit.jupiter:junit-jupiter-api:$junit_version")
        "testRuntimeOnly"("org.junit.jupiter:junit-jupiter-engine:$junit_version")
        "implementation"("org.jetbrains.exposed:exposed-core:0.29.1")
        "implementation"("org.jetbrains.exposed:exposed-dao:0.29.1")
        "implementation"("org.jetbrains.exposed:exposed-jdbc:0.29.1")
        "implementation"("com.h2database:h2:1.4.199")
    }

    tasks.withType<Test>().all {
        useJUnitPlatform()
    }
}