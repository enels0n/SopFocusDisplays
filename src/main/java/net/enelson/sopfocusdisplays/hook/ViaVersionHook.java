package net.enelson.sopfocusdisplays.hook;

import net.enelson.sopfocusdisplays.SopFocusDisplays;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

public final class ViaVersionHook {

    private final SopFocusDisplays plugin;
    private final boolean available;
    private final Method getApiMethod;
    private final Method getPlayerVersionMethod;

    public ViaVersionHook(SopFocusDisplays plugin) {
        this.plugin = plugin;

        Method resolvedGetApi = null;
        Method resolvedGetPlayerVersion = null;
        boolean resolvedAvailable = false;

        try {
            Class<?> viaClass = Class.forName("com.viaversion.viaversion.api.Via");
            Class<?> viaApiClass = Class.forName("com.viaversion.viaversion.api.ViaAPI");
            resolvedGetApi = viaClass.getMethod("getAPI");
            resolvedGetPlayerVersion = viaApiClass.getMethod("getPlayerVersion", UUID.class);
            resolvedAvailable = true;
        } catch (Throwable ignored) {
        }

        this.available = resolvedAvailable;
        this.getApiMethod = resolvedGetApi;
        this.getPlayerVersionMethod = resolvedGetPlayerVersion;
    }

    public boolean isClientModernEnough(Player player) {
        if (!this.available) {
            return true;
        }

        int minSupportedProtocol = this.plugin.getConfig().getInt("fallback.min-supported-protocol", 762);
        int protocol = getProtocolVersion(player);
        return protocol < 0 || protocol >= minSupportedProtocol;
    }

    public int getProtocolVersion(Player player) {
        if (!this.available) {
            return -1;
        }

        try {
            Object api = this.getApiMethod.invoke(null);
            Object version = this.getPlayerVersionMethod.invoke(api, player.getUniqueId());
            return version instanceof Integer ? (Integer) version : -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }
}