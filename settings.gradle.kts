dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            version("kotlin2-lang", providers.gradleProperty("kotlin2-lang.version").get())
            version("kotlin2-logging", providers.gradleProperty("kotlin2-logging.version").get())
//            version("spring-boot", providers.gradleProperty("spring-boot.version").get())
//            version("spring-dm", providers.gradleProperty("spring-dm.version").get())
        }
    }
}

rootProject.name = "demo-protocols"

include("demo-rsocket", "demo-quic")
