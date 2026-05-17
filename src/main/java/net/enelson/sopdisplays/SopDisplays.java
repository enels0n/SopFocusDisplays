package net.enelson.sopdisplays;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;

import net.enelson.sopdisplays.command.SopDisplaysCommand;
import net.enelson.sopdisplays.listener.PlayerStateListener;
import net.enelson.sopdisplays.manager.FocusDisplayManager;
import net.enelson.sopli.lib.SopLib;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class SopDisplays extends JavaPlugin {

    private static final String VIA_PROTOCOL_PLACEHOLDER = "%viaversion_player_protocol_id%";

    private final LegacyComponentSerializer legacyAmpersand = LegacyComponentSerializer.legacyAmpersand();
    private final LegacyComponentSerializer legacySection = LegacyComponentSerializer.legacySection();
    private final Map<UUID, String> protocolPlaceholderCache = new HashMap<UUID, String>();

    private ProtocolManager protocolManager;
    private FocusDisplayManager focusDisplayManager;
    private Method placeholderMethod;
    private boolean displayEntitiesSupported;

    @Override
    public void onEnable() {
        if (SopLib.getInstance() == null) {
            getLogger().severe("SopLib is not initialized. SopDisplays requires SopLib.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        this.displayEntitiesSupported = isModernDisplayServer();
        if (!this.displayEntitiesSupported) {
            getLogger().warning("Display entities are not supported on this server version. Running in ARMOR_STAND-only mode.");
        }

        saveDefaultConfig();
        if (!new File(getDataFolder(), "data.yml").exists()) {
            saveResource("data.yml", false);
        }
        setupPlaceholderApi();

        this.protocolManager = ProtocolLibrary.getProtocolManager();
        this.focusDisplayManager = new FocusDisplayManager(this);
        this.focusDisplayManager.load();

        SopDisplaysCommand command = new SopDisplaysCommand(this);
        if (getCommand("sopdisplays") != null) {
            getCommand("sopdisplays").setExecutor(command);
            getCommand("sopdisplays").setTabCompleter(command);
        }

        Bukkit.getPluginManager().registerEvents(new PlayerStateListener(this), this);

        long interval = Math.max(1L, getConfig().getLong("focus.update-interval-ticks", 2L));
        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                focusDisplayManager.tickViewers();
            }
        }, 1L, interval);
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
            try {
                this.placeholderMethod = placeholderApiClass.getMethod("setPlaceholders", Player.class, String.class);
            } catch (NoSuchMethodException ignored) {
                Class<?> offlinePlayerClass = Class.forName("org.bukkit.OfflinePlayer");
                this.placeholderMethod = placeholderApiClass.getMethod("setPlaceholders", offlinePlayerClass, String.class);
            }
            getLogger().info("Hooked into PlaceholderAPI.");
        } catch (Throwable throwable) {
            this.placeholderMethod = null;
            getLogger().warning("Failed to hook PlaceholderAPI, text placeholders will stay disabled: " + throwable.getMessage());
        }
    }

    public ProtocolManager getProtocolManager() {
        return this.protocolManager;
    }

    public boolean supportsDisplayEntities() {
        return this.displayEntitiesSupported;
    }

    public boolean supportsEntityVisibilityApi() {
        try {
            Player.class.getMethod("hideEntity", Plugin.class, Entity.class);
            Player.class.getMethod("showEntity", Plugin.class, Entity.class);
            return true;
        } catch (NoSuchMethodException exception) {
            return false;
        }
    }

    public FocusDisplayManager getFocusDisplayManager() {
        return this.focusDisplayManager;
    }

    public void clearTransientPlayerState(UUID uniqueId) {
        if (uniqueId == null) {
            return;
        }
        this.protocolPlaceholderCache.remove(uniqueId);
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

        if (player != null && input.contains(VIA_PROTOCOL_PLACEHOLDER)) {
            String cached = this.protocolPlaceholderCache.get(player.getUniqueId());
            String trimmed = value.trim();
            if (isPositiveInteger(trimmed)) {
                this.protocolPlaceholderCache.put(player.getUniqueId(), trimmed);
            } else if (cached != null) {
                if (VIA_PROTOCOL_PLACEHOLDER.equals(input.trim())) {
                    value = cached;
                } else {
                    value = value.replace(VIA_PROTOCOL_PLACEHOLDER, cached);
                }
            }
        }

        return value;
    }

    public String applyPlaceholders(Player player, String input) {
        return resolvePlaceholders(player, input);
    }

    public Component miniMessage(String input) {
        String raw = input == null ? "" : input;
        SopLib lib = SopLib.getInstance();
        if (lib == null || lib.getTextUtils() == null) {
            return this.legacy(raw);
        }
        String colored = lib.getTextUtils().color(raw);
        return this.legacySection.deserialize(colored);
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

    public void hideEntityCompat(Player viewer, Entity entity) {
        if (viewer == null || entity == null) {
            return;
        }
        try {
            Method hideEntity = Player.class.getMethod("hideEntity", Plugin.class, Entity.class);
            hideEntity.invoke(viewer, this, entity);
        } catch (NoSuchMethodException ignored) {
            if (entity instanceof Player) {
                viewer.hidePlayer(this, (Player) entity);
            }
        } catch (Throwable ignored) {
        }
    }

    public void showEntityCompat(Player viewer, Entity entity) {
        if (viewer == null || entity == null) {
            return;
        }
        try {
            Method showEntity = Player.class.getMethod("showEntity", Plugin.class, Entity.class);
            showEntity.invoke(viewer, this, entity);
        } catch (NoSuchMethodException ignored) {
            if (entity instanceof Player) {
                viewer.showPlayer(this, (Player) entity);
            }
        } catch (Throwable ignored) {
        }
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

    private boolean isPositiveInteger(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        try {
            return Integer.parseInt(value) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
