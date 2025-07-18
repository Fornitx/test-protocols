dependencies {
    api(libs.kotlin.logging)
//    api(libs.kotlin.io)
    api(libs.bundles.kotlin.arrow)

    api(libs.commons.lang3)
    api(libs.guava)

    implementation(libs.commons.math4)

    runtimeOnly(libs.logback)
}
