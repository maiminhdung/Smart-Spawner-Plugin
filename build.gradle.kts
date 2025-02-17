import org.gradle.kotlin.dsl.accessors.runtime.applySoftwareType

plugins {
    id("java")
    id("java-library")
    id("xyz.jpenilla.run-paper") version "2.3.1"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.14"
}

runPaper.folia.registerTask()

group = "me.nighter"
version = "1.2.3"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://oss.sonatype.org/content/groups/public/") {
        name = "sonatype"
    }
    maven("https://repo.opencollab.dev/main") {
        name = "opencollabRepositoryMain"
    }
    maven("https://jitpack.io") {
        name = "jitpack"
    }
    maven("https://maven.enginehub.org/repo/") {
        name = "enginehub"
    }
    maven("https://repo.glaremasters.me/repository/towny/") {
        name = "glaremasters repo"
    }
}

paperweight {
    paperweight.reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.MOJANG_PRODUCTION
}

dependencies {
    // Paper API
    paperweight.paperDevBundle("1.20.4-R0.1-SNAPSHOT")

    // Hooks plugin
    compileOnly("org.geysermc.floodgate:api:2.2.4-SNAPSHOT")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.13-SNAPSHOT")
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.4.0-SNAPSHOT")
    compileOnly("com.github.brcdev-minecraft:shopgui-api:3.0.0") {
        exclude(group = "org.spigotmc", module = "spigot-api")
    }
    compileOnly("com.palmergames.bukkit.towny:towny:0.101.1.3")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    implementation("org.bstats:bstats-bukkit:3.0.2")
    implementation("net.kyori:adventure-api:4.19.0")
    implementation("net.kyori:adventure-text-minimessage:4.19.0")
    implementation("com.github.maiminhdung:zShop-API:9cb1b3e140")
    implementation("com.github.Gypopo:EconomyShopGUI-API:1.7.3")
    implementation("com.github.GriefPrevention:GriefPrevention:17.0.0")
    implementation("com.github.IncrediblePlugins:LandsAPI:7.10.13")
}

val targetJavaVersion = 21
java {
    val javaVersion = JavaVersion.toVersion(targetJavaVersion)
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
    if (JavaVersion.current() < javaVersion) {
        toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    if (targetJavaVersion >= 10 || JavaVersion.current().isJava10Compatible) {
        options.release.set(targetJavaVersion)
    }
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    archiveBaseName.set("SmartSpawner")
    archiveVersion.set("Paper-$version")
    //destinationDirectory.set(file("C:\\Users\\ADMIN\\OneDrive\\Desktop\\TestServer\\plugins\\"))

    // Combine subprojects
    from({
        subprojects.map { project ->
            project.extensions.getByType<SourceSetContainer>()["main"].output
        }
    })

    from(sourceSets["main"].output)

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

// Exclude unnecessary files
    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
}