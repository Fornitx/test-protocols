dependencies {
    implementation(project(":common"))

    implementation("io.projectreactor.netty:reactor-netty-quic:1.0.0-M7")
    implementation("io.netty:netty-codec-native-quic:4.2.3.Final:windows-x86_64")

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
