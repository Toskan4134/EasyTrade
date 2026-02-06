package org.toskan4134.hytrade.util;

import com.hypixel.hytale.server.core.Message;

import javax.annotation.Nonnull;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for formatting messages with Minecraft-style color codes.
 * Supports color codes (&0-f), hex colors (&#RRGGBB), and formatting codes.
 *
 * IMPORTANT: Hytale chat only supports &l (bold) and &r (reset) for formatting.
 *
 * Examples:
 * - &a = Green
 * - &c = Red
 * - &l = Bold
 * - &#FF0000 = Red (hex)
 * - &r = Reset formatting
 */
public class MessageFormatter {

    private static final Map<Character, Color> COLOR_MAP = new HashMap<>();
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([0-9A-Fa-f]{6})");
    private static final Color DEFAULT_COLOR = Color.WHITE;

    static {
        COLOR_MAP.put('0', Color.BLACK);
        COLOR_MAP.put('1', new Color(0x0000AA)); // Dark Blue
        COLOR_MAP.put('2', new Color(0x00AA00)); // Dark Green
        COLOR_MAP.put('3', new Color(0x00AAAA)); // Dark Aqua
        COLOR_MAP.put('4', new Color(0xAA0000)); // Dark Red
        COLOR_MAP.put('5', new Color(0xAA00AA)); // Dark Purple
        COLOR_MAP.put('6', new Color(0xFFAA00)); // Gold
        COLOR_MAP.put('7', new Color(0xAAAAAA)); // Gray
        COLOR_MAP.put('8', new Color(0x555555)); // Dark Gray
        COLOR_MAP.put('9', new Color(0x5555FF)); // Blue
        COLOR_MAP.put('a', new Color(0x55FF55)); // Green
        COLOR_MAP.put('b', new Color(0x55FFFF)); // Aqua
        COLOR_MAP.put('c', new Color(0xFF5555)); // Red
        COLOR_MAP.put('d', new Color(0xFF55FF)); // Light Purple
        COLOR_MAP.put('e', new Color(0xFFFF55)); // Yellow
        COLOR_MAP.put('f', Color.WHITE);
    }

    /**
     * Formats text with color codes and converts to Hytale Message.
     *
     * @param text Text with color codes (& prefix)
     * @return Formatted Message object (never null)
     */
    @Nonnull
    public static Message format(String text) {
        if (text == null || text.isEmpty()) {
            return Message.raw("");
        }

        // Replace hex colors with temporary markers
        Matcher hexMatcher = HEX_PATTERN.matcher(text);
        StringBuilder processed = new StringBuilder();
        int lastEnd = 0;

        while (hexMatcher.find()) {
            processed.append(text, lastEnd, hexMatcher.start());
            // Convert hex to §x format (Minecraft style for internal use)
            processed.append("§x").append(hexMatcher.group(1));
            lastEnd = hexMatcher.end();
        }
        processed.append(text.substring(lastEnd));
        text = processed.toString();

        // Now process standard color codes
        return processColorCodes(text);
    }

    /**
     * Process color codes and build Message with proper colors.
     */
    private static Message processColorCodes(String text) {
        if (!text.contains("&") && !text.contains("§")) {
            return Message.raw(text);
        }

        StringBuilder current = new StringBuilder();
        Color currentColor = DEFAULT_COLOR;
        boolean bold = false;
        Message result = Message.raw("");

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if ((c == '&' || c == '§') && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));

                // Flush current segment if exists
                if (current.length() > 0) {
                    Message segment = Message.raw(current.toString()).color(currentColor);
                    if (bold) segment = segment.bold(true);
                    result = Message.join(result, segment);
                    current = new StringBuilder();
                }

                // Handle hex color (§x followed by 6 hex digits)
                if (code == 'x' && i + 8 <= text.length()) {
                    String hexCode = text.substring(i + 2, i + 8);
                    try {
                        currentColor = Color.decode("#" + hexCode);
                    } catch (NumberFormatException e) {
                        currentColor = DEFAULT_COLOR;
                    }
                    i += 7; // Skip §x + 6 hex digits
                }
                // Handle standard color code
                else if (COLOR_MAP.containsKey(code)) {
                    currentColor = COLOR_MAP.get(code);
                    i++; // Skip the color code character
                }
                // Handle formatting
                else if (code == 'l') {
                    bold = true;
                    i++;
                }
                else if (code == 'r') {
                    currentColor = DEFAULT_COLOR;
                    bold = false;
                    i++;
                }
                else {
                    // Unknown code, treat as literal
                    current.append(c);
                }
            } else {
                current.append(c);
            }
        }

        // Flush remaining text
        if (current.length() > 0) {
            Message segment = Message.raw(current.toString()).color(currentColor);
            if (bold) segment = segment.bold(true);
            result = Message.join(result, segment);
        }

        return result;
    }

    /**
     * Formats text with a fallback color if no color codes are present.
     *
     * @param text Text with optional color codes (& prefix)
     * @param fallbackColorHex Hex color to use if no color codes present (e.g., "#FF5555")
     * @return Formatted Message object (never null)
     */
    @Nonnull
    public static Message formatWithFallback(String text, @Nonnull String fallbackColorHex) {
        if (text == null || text.isEmpty()) {
            return Message.raw("");
        }

        // Check if text contains color codes
        if (text.contains("&") || text.contains("§")) {
            return format(text);
        }

        // No color codes, apply fallback color
        try {
            Color color = Color.decode(fallbackColorHex);
            return Message.raw(text).color(color);
        } catch (NumberFormatException e) {
            return Message.raw(text).color(DEFAULT_COLOR);
        }
    }

    /**
     * Extracts plain text from a formatted message (removes all color codes).
     * Useful for UI elements that need plain strings.
     */
    @Nonnull
    public static String stripColors(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // Remove hex colors
        text = HEX_PATTERN.matcher(text).replaceAll("");

        // Remove standard color codes
        return text.replaceAll("[&§][0-9a-flr]", "");
    }
}
