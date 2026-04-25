package net.enelson.sopfocusdisplays;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import net.enelson.sopfocusdisplays.command.SopFocusDisplaysCommand;
import net.enelson.sopfocusdisplays.hook.ViaVersionHook;
import net.enelson.sopfocusdisplays.listener.PlayerStateListener;
import net.enelson.sopfocusdisplays.manager.FocusDisplayManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

public final class SopFocusDisplays extends JavaPlugin {

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final LegacyComponentSerializer legacyAmpersand = LegacyComponentSerializer.legacyAmpersand();

    private ProtocolManager protocolManager;
    private ViaVersionHook viaVersionHook;
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
        saveResource("data.yml", false);
        setupPlaceholderApi();

        this.protocolManager = ProtocolLibrary.getProtocolManager();
        this.viaVersionHook = new ViaVersionHook(this);
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

    public ViaVersionHook getViaVersionHook() {
        return this.viaVersionHook;
    }

    public FocusDisplayManager getFocusDisplayManager() {
        return this.focusDisplayManager;
    }

    public String applyPlaceholders(Player player, String input) {
        String value = input == null ? "" : input;
        if (player == null || this.placeholderMethod == null || value.isEmpty()) {
            return value;
        }

        try {
            Object resolved = this.placeholderMethod.invoke(null, player, value);
            return resolved instanceof String ? (String) resolved : value;
        } catch (Throwable throwable) {
            return value;
        }
    }

    public Component miniMessage(String input) {
        return this.miniMessage.deserialize(input == null ? "" : input);
    }

    public Component miniMessage(Player player, String input) {
        return miniMessage(applyPlaceholders(player, input));
    }

    public Component legacy(String input) {
        return this.legacyAmpersand.deserialize(input == null ? "" : input);
    }

    public Component legacy(Player player, String input) {
        return legacy(applyPlaceholders(player, input));
    }
}