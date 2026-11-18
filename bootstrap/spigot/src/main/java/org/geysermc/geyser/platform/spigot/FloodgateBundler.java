/*
 * Copyright (c) 2019-2022 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.geyser.platform.spigot;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.geyser.GeyserLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.jar.JarFile;

/**
 * Installs the Floodgate plugin that is bundled inside the Geyser Extended jar.
 * <p>
 * Floodgate has to be loaded by the server as its own plugin: it injects into the server connection
 * before Geyser handles a login. Shading it into Geyser would therefore not work, so the official
 * MIT-licensed jar is embedded as a resource and copied into the server's plugins folder instead.
 * The server picks it up on the next start, and Geyser then automatically switches to Floodgate
 * authentication, which lets Bedrock players join without a Java Edition account.
 */
public final class FloodgateBundler {
    private static final String RESOURCE = "/floodgate/floodgate-spigot.jar";
    private static final String FILE_NAME = "floodgate-spigot.jar";

    private FloodgateBundler() {
    }

    /**
     * Copies the bundled Floodgate jar into the plugins folder unless Floodgate is already loaded or
     * a Floodgate jar is already present. Safe to call on every startup.
     */
    public static void installIfMissing(JavaPlugin plugin, GeyserLogger logger) {
        if (!Boolean.parseBoolean(System.getProperty("geyserExtended.bundleFloodgate", "true"))) {
            logger.debug("Bundled Floodgate installation is disabled by a system property.");
            return;
        }
        if (Bukkit.getPluginManager().getPlugin("floodgate") != null) {
            // Already installed and loaded by the server.
            return;
        }

        Path pluginsFolder = plugin.getDataFolder().toPath().getParent();
        if (pluginsFolder == null || !Files.isDirectory(pluginsFolder)) {
            return;
        }

        // Never overwrite a Floodgate that the server already has, even if it failed to load this time.
        try (DirectoryStream<Path> existing = Files.newDirectoryStream(pluginsFolder, "floodgate*.jar")) {
            if (existing.iterator().hasNext()) {
                return;
            }
        } catch (IOException e) {
            logger.debug("Could not check the plugins folder for Floodgate: " + e.getMessage());
            return;
        }

        try (InputStream in = FloodgateBundler.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                logger.debug("This build does not contain a bundled Floodgate jar.");
                return;
            }

            Path target = pluginsFolder.resolve(FILE_NAME);
            Path temporary = pluginsFolder.resolve(FILE_NAME + ".tmp");
            try (OutputStream out = Files.newOutputStream(temporary)) {
                in.transferTo(out);
            }
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);

            logger.info("Installed bundled Floodgate " + version(target) + " as " + target.getFileName() + ".");
            logger.info("Restart the server to let Bedrock players join without a Java Edition account.");
        } catch (IOException e) {
            logger.error("Failed to install the bundled Floodgate plugin", e);
        }
    }

    private static String version(Path jar) {
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            var entry = jarFile.getEntry("plugin.yml");
            if (entry == null) {
                return "(unknown version)";
            }
            try (var reader = new InputStreamReader(jarFile.getInputStream(entry), StandardCharsets.UTF_8)) {
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.load(reader);
                return yaml.getString("version", "(unknown version)");
            }
        } catch (Exception e) {
            return "(unknown version)";
        }
    }
}
