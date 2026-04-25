package net.enelson.sopfocusdisplays.listener;

import net.enelson.sopfocusdisplays.SopFocusDisplays;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class PlayerStateListener implements Listener {

    private final SopFocusDisplays plugin;

    public PlayerStateListener(SopFocusDisplays plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        scheduleInit(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.plugin.getFocusDisplayManager().handlePlayerQuit(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        scheduleInit(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        scheduleInit(event.getPlayer());
    }

    private void scheduleInit(final Player player) {
        Bukkit.getScheduler().runTaskLater(this.plugin, new Runnable() {
            @Override
            public void run() {
                plugin.getFocusDisplayManager().initializePlayer(player);
            }
        }, 2L);
    }
}