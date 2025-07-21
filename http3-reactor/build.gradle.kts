dependencies {
    implementation(project(":common"))

    implementation("io.netty.incubator:netty-incubator-codec-http3:0.0.30.Final")
    implementation("io.projectreactor.netty:reactor-netty:1.3.0-M5")

    val reactorVersion = project.configurations.compileClasspath.get()
        .incoming.resolutionResult.allDependencies.filter {
            val componentSelector = it.requested
            if (componentSelector is ModuleComponentSelector) {
                val id = componentSelector.moduleIdentifier
                id.group == "io.projectreactor" && id.name == "reactor-core"
            } else false
        }.map { (it.requested as ModuleComponentSelector).version }.toSet().single()

    testImplementation("io.projectreactor:reactor-test:$reactorVersion")
    testImplementation("io.projectreactor.kotlin:reactor-kotlin-extensions:1.2.3")
}
