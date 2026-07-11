package codechicken.nei.asm;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import codechicken.core.CommonUtils;
import codechicken.nei.KeyManager;
import codechicken.nei.NEIClientConfig;

public final class SaveOptionsFixHooks {

    private static final String KEY_PREFIX = "key_nei.options.keys.";

    private static List<String> pendingRestoreLines;

    private SaveOptionsFixHooks() {}

    public static void onBeforeSaveOptions() {
        pendingRestoreLines = null;

        if (NEIClientConfig.isLoaded()) {
            return;
        }

        final File optionsFile = new File(CommonUtils.getMinecraftDir(), "options.txt");
        if (!optionsFile.exists()) {
            return;
        }

        final List<String> missing = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(optionsFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith(KEY_PREFIX)) continue;
                final int colon = line.indexOf(':');
                if (colon <= 0) continue;

                final String description = line.substring(4, colon);
                if (!KeyManager.isRegistered(description)) {
                    missing.add(line);
                }
            }
        } catch (IOException e) {
            NEIClientConfig.logger.error("Failed to read key bindings before options save", e);
            return;
        }

        if (!missing.isEmpty()) {
            pendingRestoreLines = missing;
        }
    }

    public static void onAfterSaveOptions() {
        final List<String> restore = pendingRestoreLines;
        pendingRestoreLines = null;

        if (restore == null) {
            return;
        }

        final File optionsFile = new File(CommonUtils.getMinecraftDir(), "options.txt");

        try (FileWriter writer = new FileWriter(optionsFile, true)) {
            for (String line : restore) {
                writer.write(line);
                writer.write(System.lineSeparator());
            }
        } catch (IOException e) {
            NEIClientConfig.logger.error("Failed to restore NEI key bindings after options save", e);
        }
    }
}
