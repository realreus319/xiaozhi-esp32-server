plugins {
    kotlin("jvm") version "2.3.0" apply false
    kotlin("plugin.spring") version "2.3.0" apply false

    id("org.springframework.boot") version "4.0.1" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

allprojects {
    group = "com.xiaozhi"
    version = "2.0.0"

    repositories {
        maven { url = uri("https://repo.huaweicloud.com/repository/maven/") }
        maven { url = uri("https://maven.aliyun.com/repository/google/") }
        maven { url = uri("https://maven.aliyun.com/repository/central/") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin/") }
        maven { url = uri("https://maven.aliyun.com/repository/releases/") }
        maven { url = uri("https://maven.aliyun.com/repository/public/") }
        mavenCentral()
        maven { url = uri("https://repo.spring.io/milestone") }
        maven { url = uri("https://repo.spring.io/snapshot") }
        maven {
            name = "Central Portal Snapshots"
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        }
    }
}

subprojects {
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            freeCompilerArgs.add("-Xjsr305=strict")
            freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
        }
    }

    tasks.withType<JavaCompile> {
        sourceCompatibility = "25"
        targetCompatibility = "25"
    }

    tasks.withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
