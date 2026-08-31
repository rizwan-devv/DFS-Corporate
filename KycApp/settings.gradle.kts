pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // Tesseract4Android is only published via JitPack, not Maven Central.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "KycApp"
include(":app")