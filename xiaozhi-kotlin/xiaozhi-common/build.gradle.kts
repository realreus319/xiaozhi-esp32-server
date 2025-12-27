plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("tools.jackson.datatype:jackson-datatype-jsr310:3.0.0-rc2")
    implementation("tools.jackson.dataformat:jackson-dataformat-yaml:3.0.3")
}
