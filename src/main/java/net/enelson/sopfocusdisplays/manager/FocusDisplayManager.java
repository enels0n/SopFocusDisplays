package net.enelson.sopfocusdisplays.manager;

import net.enelson.sopfocusdisplays.SopFocusDisplays;
import net.enelson.sopfocusdisplays.model.DisplayConditions;
import net.enelson.sopfocusdisplays.model.FocusDisplayDefinition;
import net.enelson.sopfocusdisplays.model.FocusDisplayType;
import net.enelson.sopfocusdisplays.model.SpawnedFocusDisplay;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FocusDisplayManager {

    private final SopFocusDisplays plugin;
    private final File dataFile;
    private final Map<String, SpawnedFocusDisplay> displays = new LinkedHashMap<String, SpawnedFocusDisplay>();

    public FocusDisplayManager(SopFocusDisplays plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        shutdown();
        cleanupManagedEntities();
        if (!this.dataFile.exists()) {
            this.plugin.saveResource("data.yml", false);
        }

        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(this.dataFile);
        ConfigurationSection section = configuration.getConfigurationSection("displays");
        if (section == null) {
            return;
        }

        for (String id : section.getKeys(false)) {
            FocusDisplayDefinition definition = FocusDisplayDefinition.fromSection(
                    id,
                    section.getConfigurationSection(id),
                    (float) this.plugin.getConfig().getDouble("display.base-scale", 1.0D),
                    (float) this.plugin.getConfig().getDouble("display.focus-scale", 1.25D),
                    this.plugin.getConfig().getString("text.default-text", "<gold>Focus Text</gold>")
            );
            if (definition == null) {
                this.plugin.getLogger().warning("Skipped display '" + id + "' because its world could not be loaded.");
                continue;
            }

            SpawnedFocusDisplay spawned = new SpawnedFocusDisplay(this.plugin, definition);
            spawned.spawn();
            this.displays.put(id.toLowerCase(), spawned);
        }
    }

    public void shutdown() {
        for (SpawnedFocusDisplay display : new ArrayList<SpawnedFocusDisplay>(this.displays.values())) {
            display.remove();
        }
        this.displays.clear();
    }

    public void reloadAll() {
        this.plugin.reloadConfig();
        load();
    }

    public boolean createItem(String id, Location location, ItemStack itemStack) {
        if (this.displays.containsKey(id.toLowerCase())) {
            return false;
        }

        FocusDisplayDefinition definition = new FocusDisplayDefinition(
                id,
                FocusDisplayType.ITEM,
                location,
                itemStack,
                "",
                (float) this.plugin.getConfig().getDouble("display.base-scale", 1.0D),
                (float) this.plugin.getConfig().getDouble("display.focus-scale", 1.25D),
                DisplayConditions.alwaysVisible()
        );
        SpawnedFocusDisplay display = new SpawnedFocusDisplay(this.plugin, definition);
        display.spawn();
        this.displays.put(id.toLowerCase(), display);
        save();
        return true;
    }

    public boolean createText(String id, Location location, String text) {
        if (this.displays.containsKey(id.toLowerCase())) {
            return false;
        }

        FocusDisplayDefinition definition = new FocusDisplayDefinition(
                id,
                FocusDisplayType.TEXT,
                location,
                null,
                text,
                (float) this.plugin.getConfig().getDouble("display.base-scale", 1.0D),
                (float) this.plugin.getConfig().getDouble("display.focus-scale", 1.25D),
                DisplayConditions.alwaysVisible()
        );
        SpawnedFocusDisplay display = new SpawnedFocusDisplay(this.plugin, definition);
        display.spawn();
        this.displays.put(id.toLowerCase(), display);
        save();
        return true;
    }

    public boolean createHologram(String id, Location location, String text) {
        if (this.displays.containsKey(id.toLowerCase())) {
            return false;
        }

        FocusDisplayDefinition definition = new FocusDisplayDefinition(
                id,
                FocusDisplayType.HOLOGRAM,
                location,
                null,
                text,
                (float) this.plugin.getConfig().getDouble("display.base-scale", 1.0D),
                (float) this.plugin.getConfig().getDouble("display.focus-scale", 1.25D),
                DisplayConditions.alwaysVisible()
        );
        SpawnedFocusDisplay display = new SpawnedFocusDisplay(this.plugin, definition);
        display.spawn();
        this.displays.put(id.toLowerCase(), display);
        save();
        return true;
    }

    public boolean remove(String id) {
        SpawnedFocusDisplay removed = this.displays.remove(id.toLowerCase());
        if (removed == null) {
            return false;
        }
        removed.remove();
        save();
        return true;
    }

    public boolean moveHere(String id, Location location) {
        SpawnedFocusDisplay display = this.displays.get(id.toLowerCase());
        if (display == null) {
            return false;
        }
        display.move(location);
        save();
        return true;
    }

    public boolean updateItem(String id, ItemStack itemStack) {
        SpawnedFocusDisplay display = this.displays.get(id.toLowerCase());
        if (display == null || display.getDefinition().getType() != FocusDisplayType.ITEM) {
            return false;
        }
        display.updateItem(itemStack);
        save();
        return true;
    }

    public boolean updateText(String id, String text) {
        SpawnedFocusDisplay display = this.displays.get(id.toLowerCase());
        if (display == null || display.getDefinition().getType() == FocusDisplayType.ITEM) {
            return false;
        }
        display.updateText(text);
        save();
        return true;
    }

    public Collection<SpawnedFocusDisplay> getDisplays() {
        return Collections.unmodifiableCollection(this.displays.values());
    }

    public List<String> getIds() {
        List<String> ids = new ArrayList<String>();
        for (SpawnedFocusDisplay display : this.displays.values()) {
            ids.add(display.getDefinition().getId());
        }
        return ids;
    }

    public void tickViewers() {
        if (this.displays.isEmpty()) {
            return;
        }

        double maxDistance = this.plugin.getConfig().getDouble("focus.max-distance", 16.0D);
        double angle = Math.toRadians(this.plugin.getConfig().getDouble("focus.focus-angle-degrees", 10.0D));
        double focusCosine = Math.cos(angle);
        float lerpSpeed = (float) this.plugin.getConfig().getDouble("focus.lerp-speed", 0.25D);
        lerpSpeed = Math.max(0.0F, Math.min(1.0F, lerpSpeed));

        for (Player player : Bukkit.getOnlinePlayers()) {
            for (SpawnedFocusDisplay display : this.displays.values()) {
                if (!display.isSameWorld(player)) {
                    continue;
                }

                display.ensureViewerMode(player);
                if (display.getDefinition().getType() != FocusDisplayType.ITEM) {
                    display.syncViewerText(player);
                }

                float current = display.getViewerScale(player.getUniqueId());
                float target = display.isLookingAt(player, maxDistance, focusCosine)
                        ? display.getDefinition().getFocusScale()
                        : display.getDefinition().getBaseScale();

                float next = lerp(current, target, lerpSpeed);
                if (Math.abs(next - current) > 0.01F || Math.abs(current - target) > 0.01F) {
                    display.sendScale(player, next);
                    display.setViewerScale(player.getUniqueId(), next);
                }
            }
        }
    }

    public void handlePlayerQuit(Player player) {
        for (SpawnedFocusDisplay display : this.displays.values()) {
            display.forgetViewer(player.getUniqueId());
        }
    }

    public void preparePlayer(Player player) {
        for (SpawnedFocusDisplay display : this.displays.values()) {
            display.hideFor(player);
        }
    }

    public void refreshViewerStates() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (SpawnedFocusDisplay display : this.displays.values()) {
                display.invalidateViewerState(player.getUniqueId());
            }
        }
    }

    public void initializePlayer(final Player player) {
        for (final SpawnedFocusDisplay display : this.displays.values()) {
            if (!display.isSameWorld(player)) {
                continue;
            }

            display.ensureViewerMode(player);
            if (display.getDefinition().getType() != FocusDisplayType.ITEM) {
                display.resetViewerText(player.getUniqueId());
                display.syncViewerText(player);
            }
            display.sendScale(player, display.getDefinition().getBaseScale());
            display.setViewerScale(player.getUniqueId(), display.getDefinition().getBaseScale());

            if (display.getDefinition().getType() != FocusDisplayType.ITEM) {
                scheduleTextResend(player, display, 4L);
                scheduleTextResend(player, display, 12L);
                scheduleTextResend(player, display, 30L);
            }
        }
    }

    private void scheduleTextResend(final Player player, final SpawnedFocusDisplay display, long delay) {
        Bukkit.getScheduler().runTaskLater(this.plugin, new Runnable() {
            @Override
            public void run() {
                if (player.isOnline() && display.isSameWorld(player)) {
                    display.resetViewerText(player.getUniqueId());
                    display.syncViewerText(player);
                }
            }
        }, delay);
    }

    private float lerp(float current, float target, float speed) {
        return current + (target - current) * speed;
    }

    private void cleanupManagedEntities() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof Display) && !(entity instanceof ArmorStand)) {
                    continue;
                }
                String value = entity.getPersistentDataContainer().get(
                        new org.bukkit.NamespacedKey(this.plugin, "display-id"),
                        PersistentDataType.STRING
                );
                if (value != null) {
                    entity.remove();
                }
            }
        }
    }

    private void save() {
        YamlConfiguration configuration = new YamlConfiguration();
        ConfigurationSection section = configuration.createSection("displays");
        for (SpawnedFocusDisplay display : this.displays.values()) {
            ConfigurationSection displaySection = section.createSection(display.getDefinition().getId());
            display.getDefinition().save(displaySection);
        }

        try {
            configuration.save(this.dataFile);
        } catch (IOException exception) {
            this.plugin.getLogger().severe("Failed to save data.yml: " + exception.getMessage());
        }
    }
}
