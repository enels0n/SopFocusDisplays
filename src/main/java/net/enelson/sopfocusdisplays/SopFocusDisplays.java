package net.enelson.sopfocusdisplays;

import java.io.File;
import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;

import net.enelson.sopfocusdisplays.command.SopFocusDisplaysCommand;
import net.enelson.sopfocusdisplays.listener.PlayerStateListener;
import net.enelson.sopfocusdisplays.manager.FocusDisplayManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class SopFocusDisplays extends JavaPlugin {

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacyAmpersand = LegacyComponentSerializer.legacyAmpersand();

    private ProtocolManager protocolManager;
    private FocusDisplayManager focusDisplayManager;
    private Method placeholderMethod;

    @Override
    public void onEnable() {
        if (!isModernDisplayServer()) {
            getLogger().severe("ItemDisplay is not supported on this server version.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        if (!new File(getDataFolder(), "data.yml").exists()) {
            saveResource("data.yml", false);
        }
        setupPlaceholderApi();

        this.protocolManager = ProtocolLibrary.getProtocolManager();
        this.focusDisplayManager = new FocusDisplayManager(this);
        this.focusDisplayManager.load();

        SopFocusDisplaysCommand command = new SopFocusDisplaysCommand(this);
        if (getCommand("sopfocusdisplays") != null) {
            getCommand("sopfocusdisplays").setExecutor(command);
            getCommand("sopfocusdisplays").setTabCompleter(command);
        }

        Bukkit.getPluginManager().registerEvents(new PlayerStateListener(this), this);

        long interval = Math.max(1L, getConfig().getLong("focus.update-interval-ticks", 2L));
        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                focusDisplayManager.tickViewers();
            }
        }, 1L, interval);

        long refreshInterval = Math.max(1L, getConfig().getLong("conditions.refresh-interval-ticks", 20L));
        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                focusDisplayManager.refreshViewerStates();
            }
        }, refreshInterval, refreshInterval);
    }

    @Override
    public void onDisable() {
        if (this.focusDisplayManager != null) {
            this.focusDisplayManager.shutdown();
        }
    }

    private boolean isModernDisplayServer() {
        try {
            Class.forName("org.bukkit.entity.ItemDisplay");
            Class.forName("org.bukkit.entity.TextDisplay");
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }

    private void setupPlaceholderApi() {
        Plugin placeholderApi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        if (placeholderApi == null || !placeholderApi.isEnabled()) {
            this.placeholderMethod = null;
            return;
        }

        try {
            Class<?> placeholderApiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            this.placeholderMethod = placeholderApiClass.getMethod("setPlaceholders", Player.class, String.class);
            getLogger().info("Hooked into PlaceholderAPI.");
        } catch (Throwable throwable) {
            this.placeholderMethod = null;
            getLogger().warning("Failed to hook PlaceholderAPI, text placeholders will stay disabled: " + throwable.getMessage());
        }
    }

    public ProtocolManager getProtocolManager() {
        return this.protocolManager;
    }

    public FocusDisplayManager getFocusDisplayManager() {
        return this.focusDisplayManager;
    }

    public String resolvePlaceholders(Player player, String input) {
        String value = input == null ? "" : input;
        if (value.isEmpty()) {
            return value;
        }

        if (player != null && this.placeholderMethod != null) {
            try {
                Object resolved = this.placeholderMethod.invoke(null, player, value);
                value = resolved instanceof String ? (String) resolved : value;
            } catch (Throwable ignored) {
            }
        }

        return value;
    }

    public String applyPlaceholders(Player player, String input) {
        return this.normalizeFormatting(resolvePlaceholders(player, input));
    }

    public Component miniMessage(String input) {
        return this.miniMessage.deserialize(this.normalizeFormatting(input));
    }

    public Component miniMessage(Player player, String input) {
        return miniMessage(applyPlaceholders(player, input));
    }

    public String[] splitLines(String input) {
        String value = input == null ? "" : input.replace("\r\n", "\n").replace('\r', '\n');
        String[] parts = value.split("\n", -1);
        if (parts.length == 0) {
            return new String[] { "" };
        }
        return parts;
    }

    public Component legacy(String input) {
        return this.legacyAmpersand.deserialize(input == null ? "" : input);
    }

    public Component legacy(Player player, String input) {
        return legacy(applyPlaceholders(player, input));
    }

    private String normalizeFormatting(String input) {
        String value = input == null ? "" : input;
        if (value.isEmpty()) {
            return value;
        }

        String normalized = value.replace('\u00A7', '&');
        normalized = this.normalizeLegacyHex(normalized);
        normalized = this.normalizeBukkitHex(normalized);
        normalized = this.normalizeLegacyCodes(normalized);
        return normalized;
    }

    private String normalizeLegacyHex(String input) {
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char current = input.charAt(i);
            if (current == '&' && i + 13 < input.length()
                    && (input.charAt(i + 1) == 'x' || input.charAt(i + 1) == 'X')) {
                StringBuilder hex = new StringBuilder(6);
                boolean valid = true;
                int cursor = i + 2;
                for (int part = 0; part < 6; part++) {
                    if (cursor + 1 >= input.length() || input.charAt(cursor) != '&') {
                        valid = false;
                        break;
                    }
                    char hexChar = input.charAt(cursor + 1);
                    if (Character.digit(hexChar, 16) < 0) {
                        valid = false;
                        break;
                    }
                    hex.append(hexChar);
                    cursor += 2;
                }
                if (valid) {
                    out.append("<#").append(hex).append(">");
                    i = cursor - 1;
                    continue;
                }
            }
            out.append(current);
        }
        return out.toString();
    }

    private String normalizeBukkitHex(String input) {
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char current = input.charAt(i);
            if (current == '&' && i + 7 < input.length() && input.charAt(i + 1) == '#') {
                String hex = input.substring(i + 2, i + 8);
                if (this.isHex(hex)) {
                    out.append("<#").append(hex).append(">");
                    i += 7;
                    continue;
                }
            }
            out.append(current);
        }
        return out.toString();
    }

    private String normalizeLegacyCodes(String input) {
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char current = input.charAt(i);
            if (current == '&' && i + 1 < input.length()) {
                char code = Character.toLowerCase(input.charAt(i + 1));
                String tag = this.mapLegacyCodeToTag(code);
                if (tag != null) {
                    out.append(tag);
                    i++;
                    continue;
                }
            }
            out.append(current);
        }
        return out.toString();
    }

    private String mapLegacyCodeToTag(char code) {
        switch (code) {
            case '0': return "<black>";
            case '1': return "<dark_blue>";
            case '2': return "<dark_green>";
            case '3': return "<dark_aqua>";
            case '4': return "<dark_red>";
            case '5': return "<dark_purple>";
            case '6': return "<gold>";
            case '7': return "<gray>";
            case '8': return "<dark_gray>";
            case '9': return "<blue>";
            case 'a': return "<green>";
            case 'b': return "<aqua>";
            case 'c': return "<red>";
            case 'd': return "<light_purple>";
            case 'e': return "<yellow>";
            case 'f': return "<white>";
            case 'k': return "<obfuscated>";
            case 'l': return "<bold>";
            case 'm': return "<strikethrough>";
            case 'n': return "<underlined>";
            case 'o': return "<italic>";
            case 'r': return "<reset>";
            default: return null;
        }
    }

    private boolean isHex(String value) {
        if (value == null || value.length() != 6) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.digit(value.charAt(i), 16) < 0) {
                return false;
            }
        }
        return true;
    }
}
