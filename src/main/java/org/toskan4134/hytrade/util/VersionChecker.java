package org.toskan4134.hytrade.util;

import com.hypixel.hytale.logger.HytaleLogger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to check for plugin updates from GitHub and CurseForge.
 * Compares versions from both sources and reports the newest available.
 */
public class VersionChecker {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final String GITHUB_API_URL = "https://api.github.com/repos/Toskan4134/HyTrade/releases/latest";
    private static final String CURSEFORGE_API_URL = "https://api.cfwidget.com/1432385"; // Using CFWidget public API

    private static final Pattern GITHUB_VERSION_PATTERN = Pattern.compile("\"tag_name\"\\s*:\\s*\"v?([^\"]+)\"");
    private static final Pattern CURSEFORGE_VERSION_PATTERN = Pattern.compile("\"displayName\"\\s*:\\s*\"[^\"]*?([0-9]+\\.[0-9]+\\.[0-9]+[^\"]*?)\"");
    private static final int TIMEOUT_MS = 5000;

    private final String currentVersion;

    private String latestVersion = null;
    private String updateSource = null;
    private boolean updateAvailable = false;

    // Track versions from each source for comparison
    private String githubVersion = null;
    private String curseforgeVersion = null;

    public VersionChecker(String currentVersion ) {
        this.currentVersion = currentVersion;
    }

    /**
     * Checks for updates asynchronously.
     * Checks both GitHub and CurseForge, then compares to find the newest version.
     *
     * @return CompletableFuture that completes when the check is done
     */
    public CompletableFuture<VersionChecker> checkForUpdatesAsync() {
        return CompletableFuture.supplyAsync(() -> {
            checkForUpdates();
            return this;
        });
    }

    /**
     * Checks for updates synchronously.
     * Checks both GitHub and CurseForge, then compares to find the newest version.
     */
    public void checkForUpdates() {
        Common.logDebug(LOGGER, "Checking for updates...");

        // Fetch versions from both sources
        githubVersion = fetchGitHubVersion();
        curseforgeVersion = fetchCurseForgeVersion();

        Common.logDebug(LOGGER, "GitHub version: " + (githubVersion != null ? githubVersion : "not found"));
        Common.logDebug(LOGGER, "CurseForge version: " + (curseforgeVersion != null ? curseforgeVersion : "not found"));

        // Determine which version is newest
        String newestVersion = null;
        String newestSource = null;

        // Compare GitHub version against current
        if (isNewerVersion(githubVersion, currentVersion)) {
            newestVersion = githubVersion;
            newestSource = "GitHub";
        }

        // Compare CurseForge version against current and GitHub
        if (isNewerVersion(curseforgeVersion, currentVersion)) {
            // Check if CurseForge is newer than GitHub
            if (newestVersion == null || isNewerVersion(curseforgeVersion, newestVersion)) {
                newestVersion = curseforgeVersion;
                newestSource = "CurseForge";
            }
        }

        // Set results
        if (newestVersion != null) {
            latestVersion = newestVersion;
            updateSource = newestSource;
            updateAvailable = true;
            LOGGER.atInfo().log("New version available on " + updateSource + ": v" + latestVersion);
        } else {
            updateAvailable = false;
            LOGGER.atInfo().log("Plugin is up to date (v" + currentVersion + ")");
        }
    }

    /**
     * Fetches the latest version from GitHub releases API.
     */
    private String fetchGitHubVersion() {
        try {
            String response = fetchUrl(GITHUB_API_URL);
            if (response != null) {
                Matcher matcher = GITHUB_VERSION_PATTERN.matcher(response);
                if (matcher.find()) {
                    String version = matcher.group(1);
                    Common.logDebug(LOGGER, "Found GitHub version: " + version);
                    return version;
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to check GitHub for updates: " + e.getMessage());
        }
        return null;
    }

    /**
     * Fetches the latest version from CurseForge using the CFWidget public API.
     * Uses project ID 1432385.
     */
    private String fetchCurseForgeVersion() {
        try {
            Common.logDebug(LOGGER, "Checking CurseForge via CFWidget API...");

            String response = fetchUrl(CURSEFORGE_API_URL);
            if (response == null) {
                LOGGER.atWarning().log("CFWidget API returned null response");
                return null;
            }

            Common.logDebug(LOGGER, "CFWidget response length: " + response.length());

            // Extract version from filename in the download object
            Pattern namePattern = Pattern.compile("\"download\"\\s*:\\s*\\{[^}]*\"name\"\\s*:\\s*\"([^\"]+)\"");
            Matcher matcher = namePattern.matcher(response);
            if (matcher.find()) {
                String fileName = matcher.group(1);
                Common.logDebug(LOGGER, "Found filename: " + fileName);
                // Stop before .jar or end of string
                Pattern versionExtract = Pattern.compile("([0-9]+\\.[0-9]+\\.[0-9]+)(?:\\.jar)?");
                Matcher versionMatcher = versionExtract.matcher(fileName);
                if (versionMatcher.find()) {
                    String version = versionMatcher.group(1);
                    Common.logDebug(LOGGER, "Found CurseForge version: " + version);
                    return version;
                }
            }

            // Alternative: look for display name
            Pattern displayPattern = Pattern.compile("\"display\"\\s*:\\s*\"([^\"]+)\"");
            matcher = displayPattern.matcher(response);
            if (matcher.find()) {
                String displayName = matcher.group(1);
                Pattern versionExtract = Pattern.compile("([0-9]+\\.[0-9]+\\.[0-9]+)(?:\\.jar)?");
                Matcher versionMatcher = versionExtract.matcher(displayName);
                if (versionMatcher.find()) {
                    String version = versionMatcher.group(1);
                    Common.logDebug(LOGGER, "Found CurseForge version (from display): " + version);
                    return version;
                }
            }

            LOGGER.atWarning().log("Could not parse version from CFWidget response");
        } catch (Exception e) {
            LOGGER.atWarning().log("Failed to check CurseForge for updates: " + e.getMessage());
        }
        return null;
    }

    /**
     * Fetches content from a URL.
     *
     * @param urlString the URL to fetch
     */
    private String fetchUrl(String urlString) throws Exception {
        URL url = URI.create(urlString).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setRequestProperty("User-Agent", "HyTrade/" + currentVersion);
        connection.setRequestProperty("Accept", "application/json");

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            Common.logDebug(LOGGER, "HTTP " + responseCode + " from " + urlString);
            return null;
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        return response.toString();
    }

    /**
     * Compares two version strings to determine if the remote version is newer.
     * Supports semantic versioning (e.g., 1.0.0, 1.2.3, 2.0.0-beta).
     *
     * @param remoteVersion the version to compare against
     * @param localVersion  the current local version
     * @return true if remoteVersion is newer than localVersion
     */
    private boolean isNewerVersion(String remoteVersion, String localVersion) {
        if (remoteVersion == null || localVersion == null) {
            return false;
        }

        // Remove 'v' prefix if present
        remoteVersion = remoteVersion.startsWith("v") ? remoteVersion.substring(1) : remoteVersion;
        localVersion = localVersion.startsWith("v") ? localVersion.substring(1) : localVersion;

        // Split by dots and compare each part
        String[] remoteParts = remoteVersion.split("[.\\-]");
        String[] localParts = localVersion.split("[.\\-]");

        int maxLength = Math.max(remoteParts.length, localParts.length);
        for (int i = 0; i < maxLength; i++) {
            int remotePart = i < remoteParts.length ? parseVersionPart(remoteParts[i]) : 0;
            int localPart = i < localParts.length ? parseVersionPart(localParts[i]) : 0;

            if (remotePart > localPart) {
                return true;
            } else if (remotePart < localPart) {
                return false;
            }
        }
        return false;
    }

    /**
     * Parses a version part to an integer, handling non-numeric parts.
     */
    private int parseVersionPart(String part) {
        try {
            // Extract leading numbers
            StringBuilder numStr = new StringBuilder();
            for (char c : part.toCharArray()) {
                if (Character.isDigit(c)) {
                    numStr.append(c);
                } else {
                    break;
                }
            }
            return numStr.length() > 0 ? Integer.parseInt(numStr.toString()) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // Getters

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public String getUpdateSource() {
        return updateSource;
    }

    public String getGithubVersion() {
        return githubVersion;
    }

    public String getCurseforgeVersion() {
        return curseforgeVersion;
    }

    /**
     * Gets the update message for console output.
     */
    public String getConsoleMessage() {
        if (!updateAvailable) {
            return null;
        }

        StringBuilder msg = new StringBuilder();
        msg.append("====================================================\n");
        msg.append("  HyTrade Update Available!\n");
        msg.append("  Current: v").append(currentVersion).append(" -> Latest: v").append(latestVersion).append("\n");
        msg.append("  Newest version found on: ").append(updateSource).append("\n");

        // Show versions from both sources if available
        if (githubVersion != null || curseforgeVersion != null) {
            msg.append("  ---\n");
            if (githubVersion != null) {
                msg.append("  GitHub: v").append(githubVersion).append("\n");
            }
            if (curseforgeVersion != null) {
                msg.append("  CurseForge: v").append(curseforgeVersion).append("\n");
            }
            msg.append("  ---\n");
        }

        msg.append("  Download links:\n");
        msg.append("  GitHub: https://github.com/Toskan4134/HyTrade/releases\n");
        msg.append("  CurseForge: https://www.curseforge.com/hytale/mods/hytrade\n");
        msg.append("====================================================");

        return msg.toString();
    }

    /**
     * Gets the update message for player chat.
     */
    public String getPlayerMessage() {
        if (!updateAvailable) {
            return null;
        }
        return "[HyTrade] Update available! v" + currentVersion + " -> v" + latestVersion +
               " (Download from " + updateSource + ")";
    }
}
