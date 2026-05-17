package net.enelson.sopdisplays.manager;

import net.enelson.sopdisplays.SopDisplays;
import net.enelson.sopdisplays.model.DisplayConditions;
import net.enelson.sopdisplays.model.FocusDisplayDefinition;
import net.enelson.sopdisplays.model.FocusDisplayType;
import net.enelson.sopdisplays.model.LegacySpawnedFocusDisplay;
import net.enelson.sopdisplays.model.ManagedFocusDisplay;
import net.enelson.sopdisplays.model.SpawnedFocusDisplay;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
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
import java.util.HashSet;
import java.util.Set;

public final class FocusDisplayManager {

    private final SopDisplays plugin;
    private final File dataFile;
    private final Map<String, ManagedFocusDisplay> displays = new LinkedHashMap<String, ManagedFocusDisplay>();
    private final Set<String> externalDisplayIds = new HashSet<String>();

    public FocusDisplayManager(SopDisplays plugin) {
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

            if (!this.plugin.supportsDisplayEntities() && definition.getType() != FocusDisplayType.HOLOGRAM) {
                this.plugin.getLogger().warning("Display '" + id + "' uses type " + definition.getType().name()
                        + " which is unavailable on this server version. Converting to HOLOGRAM (ARMOR_STAND).");
                definition = toLegacyHologram(definition);
            }

            ManagedFocusDisplay spawned = createRuntimeDisplay(definition);
            spawned.spawn();
            this.displays.put(id.toLowerCase(), spawned);
        }
    }

    public void shutdown() {
        for (ManagedFocusDisplay display : new ArrayList<ManagedFocusDisplay>(this.displays.values())) {
            display.remove();
        }
        this.displays.clear();
        this.externalDisplayIds.clear();
    }

    public void reloadAll() {
        this.plugin.reloadConfig();
        load();
    }

    public boolean createItem(String id, Location location, ItemStack itemStack) {
        if (!this.plugin.supportsDisplayEntities()) {
            this.plugin.getLogger().warning("Cannot create ITEM display '" + id + "' on this server version (Display API unavailable).");
            return false;
        }
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
        ManagedFocusDisplay display = createRuntimeDisplay(definition);
        display.spawn();
        this.displays.put(id.toLowerCase(), display);
        save();
        return true;
    }

    public boolean createText(String id, Location location, String text) {
        if (this.displays.containsKey(id.toLowerCase())) {
            return false;
        }

        FocusDisplayType type = this.plugin.supportsDisplayEntities() ? FocusDisplayType.TEXT : FocusDisplayType.HOLOGRAM;
        FocusDisplayDefinition definition = new FocusDisplayDefinition(
                id,
                type,
                location,
                null,
                text,
                (float) this.plugin.getConfig().getDouble("display.base-scale", 1.0D),
                (float) this.plugin.getConfig().getDouble("display.focus-scale", 1.25D),
                DisplayConditions.alwaysVisible()
        );
        ManagedFocusDisplay display = createRuntimeDisplay(definition);
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
        ManagedFocusDisplay display = createRuntimeDisplay(definition);
        display.spawn();
        this.displays.put(id.toLowerCase(), display);
        save();
        return true;
    }

    public boolean remove(String id) {
        ManagedFocusDisplay removed = this.displays.remove(id.toLowerCase());
        if (removed == null) {
            return false;
        }
        removed.remove();
        this.externalDisplayIds.remove(id.toLowerCase());
        save();
        return true;
    }

    public boolean moveHere(String id, Location location) {
        ManagedFocusDisplay display = this.displays.get(id.toLowerCase());
        if (display == null) {
            return false;
        }
        display.move(location);
        save();
        return true;
    }

    public boolean updateItem(String id, ItemStack itemStack) {
        ManagedFocusDisplay display = this.displays.get(id.toLowerCase());
        if (display == null || display.getDefinition().getType() != FocusDisplayType.ITEM) {
            return false;
        }
        display.updateItem(itemStack);
        save();
        return true;
    }

    public boolean updateText(String id, String text) {
        ManagedFocusDisplay display = this.displays.get(id.toLowerCase());
        if (display == null || display.getDefinition().getType() == FocusDisplayType.ITEM) {
            return false;
        }
        display.updateText(text);
        save();
        return true;
    }

    public Collection<ManagedFocusDisplay> getDisplays() {
        return Collections.unmodifiableCollection(this.displays.values());
    }

    public boolean upsertExternalDisplay(String id, Location location, String text, Map<String, Object> options) {
        if (id == null || id.trim().isEmpty() || location == null || location.getWorld() == null) {
            return false;
        }
        String normalized = id.toLowerCase();
        ManagedFocusDisplay existing = this.displays.remove(normalized);
        if (existing != null) {
            existing.remove();
        }

        FocusDisplayDefinition definition = buildExternalDefinition(id, location, text, options);
        ManagedFocusDisplay display = createRuntimeDisplay(definition);
        display.spawn();
        this.displays.put(normalized, display);
        this.externalDisplayIds.add(normalized);
        return true;
    }

    public boolean removeExternalDisplay(String id) {
        if (id == null || id.trim().isEmpty()) {
            return false;
        }
        String normalized = id.toLowerCase();
        ManagedFocusDisplay removed = this.displays.remove(normalized);
        this.externalDisplayIds.remove(normalized);
        if (removed == null) {
            return false;
        }
        removed.remove();
        return true;
    }

    public List<String> getIds() {
        List<String> ids = new ArrayList<String>();
        for (ManagedFocusDisplay display : this.displays.values()) {
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
            for (ManagedFocusDisplay display : this.displays.values()) {
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
        for (ManagedFocusDisplay display : this.displays.values()) {
            display.forgetViewer(player.getUniqueId());
        }
    }

    public void preparePlayer(Player player) {
        for (ManagedFocusDisplay display : this.displays.values()) {
            display.hideFor(player);
        }
    }

    public void refreshViewerStates() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (ManagedFocusDisplay display : this.displays.values()) {
                display.invalidateViewerState(player.getUniqueId());
            }
        }
    }

    public void initializePlayer(final Player player) {
        for (final ManagedFocusDisplay display : this.displays.values()) {
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

    private void scheduleTextResend(final Player player, final ManagedFocusDisplay display, long delay) {
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
                if (!(entity instanceof ArmorStand) && !isDisplayEntity(entity)) {
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
        for (Map.Entry<String, ManagedFocusDisplay> entry : this.displays.entrySet()) {
            if (this.externalDisplayIds.contains(entry.getKey())) {
                continue;
            }
            ManagedFocusDisplay display = entry.getValue();
            ConfigurationSection displaySection = section.createSection(display.getDefinition().getId());
            display.getDefinition().save(displaySection);
        }

        try {
            configuration.save(this.dataFile);
        } catch (IOException exception) {
            this.plugin.getLogger().severe("Failed to save data.yml: " + exception.getMessage());
        }
    }

    private FocusDisplayDefinition buildExternalDefinition(String id, Location location, String text, Map<String, Object> options) {
        Map<String, Object> values = options == null ? Collections.<String, Object>emptyMap() : options;
        FocusDisplayType type = FocusDisplayType.fromString(readString(values, "type", "TEXT"));
        if (!this.plugin.supportsDisplayEntities() && type != FocusDisplayType.HOLOGRAM) {
            type = FocusDisplayType.HOLOGRAM;
        }
        float defaultBaseScale = (float) this.plugin.getConfig().getDouble("display.base-scale", 1.0D);
        float defaultFocusScale = (float) this.plugin.getConfig().getDouble("display.focus-scale", 1.25D);
        DisplayConditions conditions = parseConditions(values.get("conditions"));

        return new FocusDisplayDefinition(
                id,
                type,
                location,
                null,
                text == null ? "" : text,
                readFloat(values, "base-scale", defaultBaseScale),
                readFloat(values, "focus-scale", defaultFocusScale),
                conditions,
                readString(values, "hologram-renderer", null),
                readBoolean(values, "hologram-armor-stand-small", null),
                readBoolean(values, "hologram-armor-stand-marker", null),
                readDouble(values, "hologram-line-spacing", null),
                readBoolean(values, "text-background-enabled", null),
                readString(values, "text-background-color", null),
                readBoolean(values, "text-shadowed", null),
                readInt(values, "text-line-width", null),
                readBoolean(values, "text-see-through", null),
                readString(values, "text-alignment", null),
                readString(values, "display-billboard", null),
                readInt(values, "display-brightness-block", null),
                readInt(values, "display-brightness-sky", null),
                readFloat(values, "display-shadow-radius", null),
                readFloat(values, "display-shadow-strength", null),
                readFloat(values, "display-width", null),
                readFloat(values, "display-height", null),
                readString(values, "item-transform", null)
        );
    }

    private DisplayConditions parseConditions(Object raw) {
        if (raw == null) {
            return DisplayConditions.alwaysVisible();
        }
        if (raw instanceof ConfigurationSection) {
            return DisplayConditions.fromSection((ConfigurationSection) raw);
        }
        if (raw instanceof Map<?, ?>) {
            YamlConfiguration configuration = new YamlConfiguration();
            ConfigurationSection section = configuration.createSection("conditions");
            copyMapToSection(section, (Map<?, ?>) raw);
            return DisplayConditions.fromSection(section);
        }
        return DisplayConditions.alwaysVisible();
    }

    private void copyMapToSection(ConfigurationSection section, Map<?, ?> source) {
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (value instanceof Map<?, ?>) {
                ConfigurationSection child = section.createSection(key);
                copyMapToSection(child, (Map<?, ?>) value);
            } else if (value instanceof List<?>) {
                section.set(key, copyList((List<?>) value));
            } else {
                section.set(key, value);
            }
        }
    }

    private List<Object> copyList(List<?> source) {
        List<Object> copied = new ArrayList<Object>();
        for (Object value : source) {
            if (value instanceof Map<?, ?>) {
                Map<String, Object> child = new LinkedHashMap<String, Object>();
                copyMapToMap(child, (Map<?, ?>) value);
                copied.add(child);
            } else if (value instanceof List<?>) {
                copied.add(copyList((List<?>) value));
            } else {
                copied.add(value);
            }
        }
        return copied;
    }

    private void copyMapToMap(Map<String, Object> target, Map<?, ?> source) {
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (value instanceof Map<?, ?>) {
                Map<String, Object> child = new LinkedHashMap<String, Object>();
                copyMapToMap(child, (Map<?, ?>) value);
                target.put(key, child);
            } else if (value instanceof List<?>) {
                target.put(key, copyList((List<?>) value));
            } else {
                target.put(key, value);
            }
        }
    }

    private String readString(Map<String, Object> values, String key, String def) {
        Object value = values.get(key);
        return value == null ? def : String.valueOf(value);
    }

    private Boolean readBoolean(Map<String, Object> values, String key, Boolean def) {
        Object value = values.get(key);
        if (value == null) {
            return def;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.valueOf(String.valueOf(value));
    }

    private Integer readInt(Map<String, Object> values, String key, Integer def) {
        Object value = values.get(key);
        if (value == null) {
            return def;
        }
        if (value instanceof Number) {
            return Integer.valueOf(((Number) value).intValue());
        }
        try {
            return Integer.valueOf(Integer.parseInt(String.valueOf(value)));
        } catch (NumberFormatException exception) {
            return def;
        }
    }

    private Double readDouble(Map<String, Object> values, String key, Double def) {
        Object value = values.get(key);
        if (value == null) {
            return def;
        }
        if (value instanceof Number) {
            return Double.valueOf(((Number) value).doubleValue());
        }
        try {
            return Double.valueOf(Double.parseDouble(String.valueOf(value)));
        } catch (NumberFormatException exception) {
            return def;
        }
    }

    private Float readFloat(Map<String, Object> values, String key, Float def) {
        Double value = readDouble(values, key, null);
        if (value == null) {
            return def;
        }
        return Float.valueOf(value.floatValue());
    }

    private boolean isDisplayEntity(Entity entity) {
        if (!this.plugin.supportsDisplayEntities()) {
            return false;
        }
        try {
            Class<?> displayClass = Class.forName("org.bukkit.entity.Display");
            return displayClass.isInstance(entity);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private FocusDisplayDefinition toLegacyHologram(FocusDisplayDefinition definition) {
        return new FocusDisplayDefinition(
                definition.getId(),
                FocusDisplayType.HOLOGRAM,
                definition.getLocation(),
                null,
                definition.getText(),
                definition.getBaseScale(),
                definition.getFocusScale(),
                definition.getConditions(),
                "ARMOR_STAND",
                definition.getHologramArmorStandSmallOverride(),
                definition.getHologramArmorStandMarkerOverride(),
                definition.getHologramLineSpacingOverride(),
                definition.getBackgroundEnabledOverride(),
                definition.getBackgroundColorOverride(),
                definition.getTextShadowedOverride(),
                definition.getTextLineWidthOverride(),
                definition.getTextSeeThroughOverride(),
                definition.getTextAlignmentOverride(),
                definition.getDisplayBillboardOverride(),
                definition.getDisplayBrightnessBlockOverride(),
                definition.getDisplayBrightnessSkyOverride(),
                definition.getDisplayShadowRadiusOverride(),
                definition.getDisplayShadowStrengthOverride(),
                definition.getDisplayWidthOverride(),
                definition.getDisplayHeightOverride(),
                definition.getItemTransformOverride()
        );
    }

    private ManagedFocusDisplay createRuntimeDisplay(FocusDisplayDefinition definition) {
        if (this.plugin.supportsDisplayEntities()) {
            return new SpawnedFocusDisplay(this.plugin, definition);
        }
        return new LegacySpawnedFocusDisplay(this.plugin, definition);
    }
}
