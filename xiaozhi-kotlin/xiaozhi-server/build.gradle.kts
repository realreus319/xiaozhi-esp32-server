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
    implementation(project(":xiaozhi-common"))
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive")


    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("tools.jackson.datatype:jackson-datatype-jsr310:3.0.0-rc2")
    implementation("tools.jackson.dataformat:jackson-dataformat-yaml:3.0.3")

    // jwt
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")

    testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Jar> {
    manifest {
        attributes("Main-Class" to "com.xiaozhi.server.XiaoZhiServerKt")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Jar> {
    manifest {
        attributes("Main-Class" to "com.xiaozhi.server.XiaoZhiServerKt")
    }
}
tasks.withType<Test> {
    useJUnitPlatform()
}