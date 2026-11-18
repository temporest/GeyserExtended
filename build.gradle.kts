plugins {
    // Ensure AP works in eclipse (no effect on other IDEs)
    eclipse
    id("geyser.base-conventions")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.named<Jar>("jar") {
    // The root project has no sources of its own, so its jar is empty. Disable it so it cannot be
    // mistaken for a loadable plugin jar (use :spigot:shadowJar for the Spigot plugin instead).
    enabled = false
}
