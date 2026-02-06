package org.toskan4134.hytrade.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.hypixel.hytale.logger.HytaleLogger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Storage for plugin messages.
 * Messages are stored in messages.json in the plugin's data folder.
 */
public class MessagesStorage {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()  // Keep & as & instead of \\u0026
            .create();

    private final File dataFolder;
    private final Object fileLock = new Object();
    private Map<String, String> messages = new HashMap<>();

    public MessagesStorage(File dataFolder) {
        this.dataFolder = dataFolder;
    }

    /**
     * Load messages from messages.json.
     * Returns true if file exists and was loaded successfully.
     */
    public boolean load() {
        File file = new File(dataFolder, "messages.json");
        if (!file.exists()) {
            return false;
        }

        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            JsonElement element = gson.fromJson(reader, JsonElement.class);
            Map<String, String> loaded = new HashMap<>();
            if (element != null) {
                flattenJson("", element, loaded);
            }
            if (!loaded.isEmpty()) {
                messages = loaded;
                LOGGER.atInfo().log("Loaded " + messages.size() + " messages from messages.json");
                return true;
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to load messages.json: " + e.getMessage());
        }
        return false;
    }

    /**
     * Save messages to messages.json.
     * Messages are saved as a flat key-value map sorted alphabetically.
     */
    public void save() {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File file = new File(dataFolder, "messages.json");
        synchronized (fileLock) {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                // Save as flat map sorted by key
                Map<String, String> sorted = new LinkedHashMap<>();
                messages.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .forEach(e -> sorted.put(e.getKey(), e.getValue()));
                gson.toJson(sorted, writer);
                LOGGER.atInfo().log("Saved " + messages.size() + " messages to messages.json");
            } catch (Exception e) {
                LOGGER.atWarning().log("Failed to save messages.json: " + e.getMessage());
            }
        }
    }

    /**
     * Set all messages (used during initialization).
     */
    public void setMessages(Map<String, String> messages) {
        this.messages = new HashMap<>(messages);
    }

    /**
     * Get all messages.
     */
    public Map<String, String> getMessages() {
        return messages;
    }

    /**
     * Get a specific message by key.
     * Returns a fallback message if key is not found.
     */
    public String getMessage(String key) {
        return messages.getOrDefault(key, "&cMissing message: " + key);
    }

    /**
     * Get a message with placeholder replacements.
     * Placeholders use {key} format, e.g., {player}, {target}, {seconds}
     *
     * @param key          The message key
     * @param replacements Key-value pairs for replacement (must be even length)
     * @return Message with replacements applied
     */
    public String getMessage(String key, String... replacements) {
        String message = getMessage(key);
        if (replacements.length % 2 != 0) {
            LOGGER.atWarning().log("Invalid replacements for message " + key + ": odd number of arguments");
            return message;
        }
        for (int i = 0; i < replacements.length; i += 2) {
            message = message.replace("{" + replacements[i] + "}", replacements[i + 1]);
        }
        return message;
    }

    /**
     * Set a specific message.
     */
    public void setMessage(String key, String value) {
        messages.put(key, value);
    }

    /**
     * Check if messages.json exists.
     */
    public boolean exists() {
        return new File(dataFolder, "messages.json").exists();
    }

    /**
     * Merge with default messages (add any missing keys).
     * Returns true if new messages were added.
     */
    public boolean mergeWithDefaults(Map<String, String> defaults) {
        boolean added = false;
        for (Map.Entry<String, String> entry : defaults.entrySet()) {
            if (!messages.containsKey(entry.getKey())) {
                messages.put(entry.getKey(), entry.getValue());
                added = true;
            }
        }
        return added;
    }

    /**
     * Flatten nested JSON into dotted key paths.
     */
    private static void flattenJson(String prefix, JsonElement element, Map<String, String> out) {
        if (element == null || element.isJsonNull()) {
            return;
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
                flattenJson(key, entry.getValue(), out);
            }
            return;
        }
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            out.put(prefix, primitive.getAsString());
        }
    }
}
