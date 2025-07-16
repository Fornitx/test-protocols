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
        implementation(platform(rootProject.libs.kotlin.coroutines))

        implementation("org.jetbrains.kotlin:kotlin-reflect")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j")

        testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
        testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
        testImplementation("org.junit.jupiter:junit-jupiter-params")
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
