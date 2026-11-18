import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.security.MessageDigest

plugins {
    id("geyser.platform-conventions")
    id("geyser.modrinth-uploading-conventions")
    alias(libs.plugins.runpaper)
}

// Opt-in workaround for lossy networks. See the shadowJar block below.
val patchHttpTimeouts = providers.gradleProperty("geyserExtended.patchHttpTimeouts")
    .map(String::toBoolean)
    .getOrElse(false)

// Floodgate is bundled as the official MIT-licensed Spigot jar. It cannot be shaded into Geyser
// because Floodgate has to be loaded by the server as its own plugin: it injects into the server
// connection before Geyser handles a login. Instead the jar is embedded as a resource and copied
// into the plugins folder on first startup.
//
// Pin a build so published jars are reproducible. Override the source with
// -PgeyserExtended.floodgateUrl=<url> or -PgeyserExtended.floodgateJar=<local jar>.
val floodgateJar = layout.buildDirectory.file("bundled/floodgate-spigot.jar")
val floodgateUrl = providers.gradleProperty("geyserExtended.floodgateUrl")
    .getOrElse("https://download.geysermc.org/v2/projects/floodgate/versions/2.2.5/builds/141/downloads/spigot")
val floodgateSha256 = "21570aff9ce17d6983928e8552777760e1ede5050026b04c686b0ae112e6fd7e"
val floodgateLocalJar = providers.gradleProperty("geyserExtended.floodgateJar").orNull

val downloadFloodgate = tasks.register("downloadFloodgate") {
    val output = floodgateJar.get().asFile
    outputs.file(output)
    onlyIf { !output.exists() }
    doLast {
        output.parentFile.mkdirs()
        val temporary = File(output.parentFile, output.name + ".tmp")
        try {
            if (floodgateLocalJar != null) {
                File(floodgateLocalJar).copyTo(temporary, overwrite = true)
            } else {
                URI(floodgateUrl).toURL().openStream().use { input ->
                    temporary.outputStream().use { input.copyTo(it) }
                }
            }

            val actual = MessageDigest.getInstance("SHA-256").run {
                update(temporary.readBytes())
                digest().joinToString("") { "%02x".format(it) }
            }
            if (!actual.equals(floodgateSha256, ignoreCase = true)) {
                throw GradleException("Floodgate jar SHA-256 mismatch: expected $floodgateSha256, got $actual")
            }
            if (!temporary.renameTo(output)) {
                throw GradleException("Failed to move the Floodgate jar to $output")
            }
        } finally {
            temporary.delete()
        }
    }
}

tasks.processResources {
    from(downloadFloodgate) {
        into("floodgate")
    }
}

// Kept off the compile classpath: Geyser compiles against an older API, so it keeps working on older servers
val runServerPaperApi: Configuration by configurations.creating

dependencies {
    api(projects.core)
    api(libs.erosion.bukkit.common) {
        isTransitive = false
    }

    implementation(libs.erosion.bukkit.nms) {
        attributes {
            attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21)
        }
    }

    implementation(variantOf(libs.adapters.spigot) {
        classifier("all") // otherwise the unshaded jar is used without the shaded NMS implementations
    })
    implementation(variantOf(libs.adapters.paper) {
        classifier("all") // otherwise the unshaded jar is used without the shaded NMS implementations
    })

    implementation(libs.cloud.paper)
    implementation(libs.commodore)

    compileOnly(libs.folia.api)

    compileOnlyApi(libs.viaversion)

    // For 1.16.5/1.17.1
    implementation(libs.gson.record.factory) {
        isTransitive = false
    }

    // Servers before 1.21.11 still bundle Netty 4.1, so the codecs we shade must be 4.1 too
    // HTTP tends to be missing, just like haproxy, so we ship those
    implementation(libs.netty.legacy.codec.http) {
        version {
            strictly(libs.versions.netty.legacy.get())
        }
    }
    implementation(libs.netty.legacy.codec.haproxy) {
        version {
            strictly(libs.versions.netty.legacy.get())
        }
    }

    // Test
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation(libs.junit)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly(libs.folia.api) // The API Geyser compiles against, to load its classes in tests

    // Latest paper-api build of the runServer version, see PaperApiCompatibilityTest
    runServerPaperApi("io.papermc.paper:paper-api:${libs.versions.runpaperversion.get()}.build.+")
}

platformRelocate("it.unimi.dsi.fastutil")
// Relocate net.kyori but exclude the component logger
platformRelocate("net.kyori", "net.kyori.adventure.text.logger.slf4j.ComponentLogger")
platformRelocate("org.objectweb.asm")
platformRelocate("me.lucko.commodore")
platformRelocate("org.incendo")
platformRelocate("io.leangen.geantyref") // provided by cloud and Configurate, should also be relocated
platformRelocate("org.yaml") // Broken as of 1.20
platformRelocate("org.spongepowered")
platformRelocate("marcono1234.gson")
platformRelocate("org.bstats")

provided(libs.viaversion)

tasks.withType<Jar> {
    manifest.attributes["Main-Class"] = "org.geysermc.geyser.platform.spigot.GeyserSpigotMain"
}

// Keep the unshaded jar clearly separate from the loadable plugin jar produced by shadowJar.
tasks.named<Jar>("jar") {
    archiveBaseName.set("geyser-extended")
    archiveClassifier.set("dev")
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {

    // Prevents Paper 1.20.5+ from remapping Geyser
    manifest {
        attributes["paperweight-mappings-namespace"] = "mojang"
    }

    archiveBaseName.set("geyser-extended")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("")

    dependencies {
        exclude(dependency("com.google.*:.*"))

        // Netty is provided by the server, except for the haproxy module (missing on older Spigot builds)
        // and the http codec (needed for built-in signaling)
        exclude("io.netty", libs.netty.codec.haproxy, libs.netty.codec.http)

        // Commodore includes Brigadier
        exclude(dependency("com.mojang:.*"))
    }

    // Opt-in workaround for lossy networks: bedrock-connection hardcodes a 5 second connect/read
    // timeout on the Microsoft discovery and OpenID metadata requests that run in EncryptionUtils'
    // static initializer. On a lossy route a single lost SYN already costs a 1-3 second retransmit,
    // so those requests can time out, and Geyser then logs "Unable to fetch OpenID configuration"
    // and disables itself during startup. Enable with -PgeyserExtended.patchHttpTimeouts=true to
    // raise the four `sipush 5000` constants in that class to 20 seconds.
    if (patchHttpTimeouts) {
        outputs.doNotCacheIf("EncryptionUtils timeouts are patched after the jar is written") { true }
        doLast {
            val entryName = "org/cloudburstmc/protocol/bedrock/util/EncryptionUtils.class"
            val fiveSeconds = byteArrayOf(0x11, 0x13, 0x88.toByte()) // sipush 5000
            val twentySeconds = byteArrayOf(0x11, 0x4E, 0x20)        // sipush 20000

            val jar = archiveFile.get().asFile
            var patched = 0
            FileSystems.newFileSystem(jar.toPath(), mapOf("create" to "false")).use { fs ->
                val entry = fs.getPath(entryName)
                val bytes = Files.readAllBytes(entry)
                var i = 0
                while (i <= bytes.size - fiveSeconds.size) {
                    if (bytes[i] == fiveSeconds[0] && bytes[i + 1] == fiveSeconds[1] && bytes[i + 2] == fiveSeconds[2]) {
                        System.arraycopy(twentySeconds, 0, bytes, i, twentySeconds.size)
                        patched++
                        i += fiveSeconds.size
                    } else {
                        i++
                    }
                }
                if (patched == 0) {
                    throw GradleException("Could not find the hardcoded HTTP timeouts in $entryName - did bedrock-connection change?")
                }
                Files.write(entry, bytes)
            }
            logger.lifecycle("Patched $patched hardcoded 5s HTTP timeouts to 20s in $entryName")
        }
    }
}

modrinth {
    uploadFile.set(tasks.getByPath("shadowJar"))
    gameVersions.addAll("1.20.5", "1.20.6", "1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4",
        "1.21.5", "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10", "1.21.11", "26.1", "26.1.1", "26.1.2")
    loaders.addAll("spigot", "paper")
}

tasks {
    runServer {
        minecraftVersion(libs.versions.runpaperversion.get())
        jvmArgs("-Dcom.mojang.eula.agree=true")
    }

    test {
        useJUnitPlatform()
        // Recent paper-api builds target a newer Java than Geyser, so the test JVM has to be able to load them
        javaLauncher = project.javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
        inputs.files(runServerPaperApi)
        // Resolved when the test runs, not while configuring
        jvmArgumentProviders.add(CommandLineArgumentProvider {
            listOf("-Dgeyser.runServerPaperApi=" + runServerPaperApi.asPath)
        })
    }
}
