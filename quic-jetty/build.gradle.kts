dependencies {
    implementation(project(":common"))

    implementation("org.eclipse.jetty.quic:jetty-quic-server:${libs.versions.jetty.get()}")
    implementation("org.eclipse.jetty.quic:jetty-quic-client:${libs.versions.jetty.get()}")
}
