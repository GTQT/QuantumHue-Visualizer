package meowmel.quantumhue.wiki;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.text.translation.I18n;
import net.minecraftforge.fml.common.Loader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class WikiJsonLoader {
    private static final Logger LOG = LogManager.getLogger("Wiki");

    private WikiJsonLoader() {
    }

    private static Path getWikiDir() {
        return Loader.instance().getConfigDir().toPath().resolve("wiki");
    }

    private static String localize(String key) {
        if (key == null || key.isEmpty()) return "";
        String translated = I18n.translateToLocal(key);
        return translated.equals(key) ? key : translated;
    }

    public static List<WikiCategory> loadCategories() {
        List<WikiCategory> categories = new ArrayList<>();
        try {
            Path wikiDir = getWikiDir();
            extractDefaultsIfNeeded(wikiDir);
            JsonObject index = readJson(wikiDir, "_index");
            if (index == null || !index.has("categories")) {
                LOG.warn("Wiki _index.json not found or has no categories");
                return categories;
            }

            JsonArray catArray = index.getAsJsonArray("categories");
            for (JsonElement catEl : catArray) {
                JsonObject catObj = catEl.getAsJsonObject();
                String name = localize(catObj.get("name").getAsString());
                String iconStr = catObj.has("icon") ? catObj.get("icon").getAsString() : "";
                WikiCategory cat = new WikiCategory(name, WikiIconResolver.resolve(iconStr));

                if (catObj.has("pages")) {
                    JsonArray pageIds = catObj.getAsJsonArray("pages");
                    for (JsonElement pageIdEl : pageIds) {
                        String pageId = pageIdEl.getAsString();
                        WikiPage page = loadPage(wikiDir, pageId);
                        if (page != null) cat.add(page);
                        else LOG.warn("Wiki page not found: {}", pageId);
                    }
                }
                categories.add(cat);
            }
        } catch (Exception e) {
            LOG.error("Failed to load wiki index", e);
        }
        return categories;
    }

    private static WikiPage loadPage(Path wikiDir, String pageId) {
        try {
            JsonObject obj = readJson(wikiDir, pageId);
            if (obj == null) return null;

            String id = obj.has("id") ? obj.get("id").getAsString() : pageId;
            String title = obj.has("title") ? localize(obj.get("title").getAsString()) : pageId;
            String iconStr = obj.has("icon") ? obj.get("icon").getAsString() : "";

            WikiPage page = new WikiPage(id, title, WikiIconResolver.resolve(iconStr));
            if (obj.has("content")) page.content(localize(obj.get("content").getAsString()));

            return page;
        } catch (Exception e) {
            LOG.error("Failed to load wiki page: {}", pageId, e);
            return null;
        }
    }

    private static JsonObject readJson(Path wikiDir, String name) {
        Path file = wikiDir.resolve(name + ".json");
        if (!Files.exists(file)) return null;
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            JsonParser parser = new JsonParser();
            JsonElement el = parser.parse(reader);
            return el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (Exception e) {
            LOG.error("Failed to read wiki JSON: {}", file, e);
            return null;
        }
    }

    private static void extractDefaultsIfNeeded(Path wikiDir) {
        if (Files.exists(wikiDir.resolve("_index.json"))) return;
        LOG.info("Extracting default wiki files to {}", wikiDir);
        extractFromAssets(wikiDir);
    }

    /**
     * Force copy all .json files from /assets/wiki (mod JAR/resource) to config/wiki.
     * Always overwrites existing files. Used by /wiki reload command.
     */
    public static void reloadFromAssets() {
        Path wikiDir = getWikiDir();
        LOG.info("Reloading wiki files from assets to {}", wikiDir);
        extractFromAssets(wikiDir);
    }

    private static void extractFromAssets(Path wikiDir) {
        FileSystem zipFs = null;
        try {
            Files.createDirectories(wikiDir);
            URL marker = WikiJsonLoader.class.getResource("/assets/.gtassetsroot");
            if (marker == null) {
                LOG.error("Could not find .gtassetsroot - cannot extract wiki defaults");
                return;
            }
            URI markerUri = marker.toURI();
            Path jarWikiPath;
            if (markerUri.getScheme().equals("jar") || markerUri.getScheme().equals("zip")) {
                zipFs = FileSystems.newFileSystem(markerUri, Collections.emptyMap());
                jarWikiPath = zipFs.getPath("/assets/wiki");
            } else if (markerUri.getScheme().equals("file")) {
                URL wikiUrl = WikiJsonLoader.class.getResource("/assets/wiki");
                if (wikiUrl == null) {
                    LOG.error("Could not find /assets/wiki in classpath");
                    return;
                }
                jarWikiPath = Paths.get(wikiUrl.toURI());
            } else {
                LOG.error("Unsupported URI scheme for wiki extraction: {}", markerUri);
                return;
            }

            if (!Files.isDirectory(jarWikiPath)) {
                LOG.warn("No wiki directory found in JAR at {}", jarWikiPath);
                return;
            }

            List<Path> jsonFiles;
            try (Stream<Path> stream = Files.walk(jarWikiPath)) {
                jsonFiles = stream.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".json"))
                        .collect(Collectors.toList());
            }

            for (Path jarFile : jsonFiles) {
                Path relative = jarWikiPath.relativize(jarFile);
                Path target = wikiDir.resolve(relative.toString());
                Files.createDirectories(target.getParent());
                Files.copy(jarFile, target, StandardCopyOption.REPLACE_EXISTING);
            }
            LOG.info("Extracted {} default wiki files", jsonFiles.size());
        } catch (Exception e) {
            LOG.error("Failed to extract default wiki files", e);
        } finally {
            if (zipFs != null) try {
                zipFs.close();
            } catch (IOException ignored) {
            }
        }
    }
}