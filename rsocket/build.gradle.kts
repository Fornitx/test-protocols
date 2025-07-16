dependencies {
    implementation(project(":common"))
    implementation("io.rsocket:rsocket-core:1.1.5")
    implementation("io.rsocket:rsocket-transport-netty:1.1.5")

    val reactorVersion = project.configurations.compileClasspath.get()
        .incoming.resolutionResult.allDependencies.filter {
            val componentSelector = it.requested
            if (componentSelector is ModuleComponentSelector) {
                val id = componentSelector.moduleIdentifier
                id.group == "io.projectreactor" && id.name == "reactor-core"
            } else false
        }.map { (it.requested as ModuleComponentSelector).version }.toSet().single()

    testImplementation("io.projectreactor:reactor-test:$reactorVersion")
}
