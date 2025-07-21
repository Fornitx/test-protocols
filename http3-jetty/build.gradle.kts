dependencies {
    implementation(project(":common"))

    implementation("org.eclipse.jetty.http3:jetty-http3-server:${libs.versions.jetty.get()}")
    implementation("org.eclipse.jetty.http3:jetty-http3-client:${libs.versions.jetty.get()}")
}
