plugins {
    id("java-library")
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.14"
}

dependencies {
    compileOnly(project(":"))
    paperweight.paperDevBundle("1.20.4-R0.1-SNAPSHOT")
}