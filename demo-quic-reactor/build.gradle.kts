dependencies {
    implementation(project(":common"))
    implementation("io.projectreactor.netty:reactor-netty-quic:1.0.0-M4")
    implementation("io.netty:netty-codec-native-quic:4.2.2.Final:windows-x86_64")
}
