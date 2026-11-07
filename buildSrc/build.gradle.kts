plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.android.tools.build:gradle:8.9.0")
    implementation("org.ow2.asm:asm:9.7")
}

gradlePlugin {
    plugins {
        create("charsetShim") {
            id = "seldia.charset-shim"
            implementationClass = "com.seldia.build.CharsetShimPlugin"
        }
    }
}
