plugins {
    alias(libs.plugins.kotlin.jvm)
}

allprojects {
    group = "org.example"
    version = "1.0-SNAPSHOT"
}

subprojects {
    apply(plugin = rootProject.libs.plugins.kotlin.jvm.get().pluginId)

    dependencies {
        implementation(platform(rootProject.libs.kotlin.bom))

        implementation(kotlin("reflect"))
        implementation(rootProject.libs.kotlin.logging)
        implementation(rootProject.libs.logback)

        testImplementation(kotlin("test-junit5"))
    }

    kotlin {
        jvmToolchain(21)
        compilerOptions {
            freeCompilerArgs.addAll("-Xjsr305=strict", "-Xemit-jvm-type-annotations", "-Xjvm-default=all")
        }
    }

    tasks.test {
        useJUnitPlatform()
    }
}

tasks.build {
    enabled = false
}
