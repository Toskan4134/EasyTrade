package org.toskan4134.hytrade.translation;

import com.hypixel.hytale.logger.HytaleLogger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Manages translations for the HyTrade plugin.
 * Follows the ExampleMod pattern for language file loading.
 *
 * Language files are loaded from: Server/Languages/{locale}/trade.lang
 * Format: key = value (with {placeholder} support)
 */
public class TranslationManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static TranslationManager instance;

    private final Map<String, String> translations;
    private Locale currentLocale;

    private TranslationManager(Locale locale) {
        this.translations = new HashMap<>();
        this.currentLocale = locale;
        loadTranslations(locale);
    }

    /**
     * Get the singleton instance of the TranslationManager.
     */
    public static synchronized TranslationManager getInstance() {
        if (instance == null) {
            instance = new TranslationManager(Locale.US); // Default to English
        }
        return instance;
    }

    /**
     * Set the current locale and reload translations.
     */
    public void setLocale(Locale locale) {
        if (!this.currentLocale.equals(locale)) {
            this.currentLocale = locale;
            loadTranslations(locale);
        }
    }

    /**
     * Get the current locale.
     */
    public Locale getCurrentLocale() {
        return currentLocale;
    }

    /**
     * Get a translated string by key.
     * @param key The translation key (e.g., "trade.request.sent")
     * @return The translated string, or the key itself if not found
     */
    public String get(String key) {
        String translation = translations.get(key);
        if (translation == null) {
            LOGGER.atWarning().log("Translation not found: " + key);
            return key;
        }
        return translation;
    }

    /**
     * Get a translated string by key with placeholder replacement.
     * @param key The translation key
     * @param placeholders Placeholder name-value pairs (alternating key, value)
     *                     Example: get("trade.request.sent", "target", "PlayerName")
     * @return The translated string with placeholders replaced
     */
    public String get(String key, String... placeholders) {
        String translation = get(key);

        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                String placeholder = "{" + placeholders[i] + "}";
                String value = placeholders[i + 1];
                translation = translation.replace(placeholder, value);
            }
        }

        return translation;
    }

    /**
     * Load translations from the language file for the given locale.
     * Follows the ExampleMod pattern for resource loading.
     */
    private void loadTranslations(Locale locale) {
        translations.clear();

        String localeCode = locale.toLanguageTag(); // e.g., "en-US", "es-ES"
        String resourcePath = "Server/Languages/" + localeCode + "/trade.lang";

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                LOGGER.atWarning().log("Translation file not found: " + resourcePath + ", falling back to en-US");
                loadFallbackTranslations();
                return;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();

                    // Skip empty lines and comments
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }

                    // Parse key = value
                    int eqIndex = line.indexOf('=');
                    if (eqIndex > 0) {
                        String key = line.substring(0, eqIndex).trim();
                        String value = line.substring(eqIndex + 1).trim();
                        translations.put(key, value);
                    }
                }
            }

            LOGGER.atInfo().log("Loaded " + translations.size() + " translations for locale: " + localeCode);

        } catch (IOException e) {
            LOGGER.atWarning().withCause(e).log("Failed to load translations from " + resourcePath + ", using fallback");
            loadFallbackTranslations();
        }
    }

    /**
     * Load fallback English translations when the requested locale is not available.
     */
    private void loadFallbackTranslations() {
        String resourcePath = "Server/Languages/en-US/trade.lang";

        try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                LOGGER.atSevere().log("Fallback translation file not found: " + resourcePath);
                return;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();

                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }

                    int eqIndex = line.indexOf('=');
                    if (eqIndex > 0) {
                        String key = line.substring(0, eqIndex).trim();
                        String value = line.substring(eqIndex + 1).trim();
                        translations.put(key, value);
                    }
                }
            }

            LOGGER.atInfo().log("Loaded " + translations.size() + " fallback translations (en-US)");

        } catch (IOException e) {
            LOGGER.atSevere().withCause(e).log("Failed to load fallback translations");
        }
    }

    /**
     * Clear all translations (for testing purposes).
     */
    public void clear() {
        translations.clear();
    }

    /**
     * Get the number of loaded translations.
     */
    public int size() {
        return translations.size();
    }

    /**
     * Reload translations from the current locale.
     */
    public void reload() {
        loadTranslations(currentLocale);
    }
}
