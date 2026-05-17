package net.enelson.sopdisplays.model;

import net.enelson.sopdisplays.SopDisplays;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LegacySpawnedFocusDisplay implements ManagedFocusDisplay {

    private static final String KIND_MAIN = "main";
    private static final String KIND_VIEWER = "viewer";

    private final SopDisplays plugin;
    private final FocusDisplayDefinition definition;
    private final NamespacedKey idKey;
    private final NamespacedKey kindKey;
    private final boolean perViewerMode;
    private final List<ArmorStand> baseLines = new ArrayList<ArmorStand>();
    private final Map<UUID, List<ArmorStand>> viewerLines = new HashMap<UUID, List<ArmorStand>>();
    private final Map<UUID, String> viewerTexts = new HashMap<UUID, String>();
    private final Map<UUID, Boolean> viewerVisibility = new HashMap<UUID, Boolean>();
    private UUID sharedContextViewer;

    public LegacySpawnedFocusDisplay(SopDisplays plugin, FocusDisplayDefinition definition) {
        this.plugin = plugin;
        this.definition = definition;
        this.idKey = new NamespacedKey(plugin, "display-id");
        this.kindKey = new NamespacedKey(plugin, "display-kind");
        this.perViewerMode = plugin.supportsEntityVisibilityApi();
    }

    @Override
    public void spawn() {
        cleanupUnexpectedEntities();
        spawnBaseLines();
        if (!this.perViewerMode) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (ArmorStand armorStand : this.baseLines) {
                this.plugin.hideEntityCompat(player, armorStand);
            }
        }
    }

    @Override
    public void remove() {
        removeLines(this.baseLines);
        for (UUID viewerId : new ArrayList<UUID>(this.viewerLines.keySet())) {
            removeViewerLines(viewerId);
        }
        this.viewerTexts.clear();
        this.viewerVisibility.clear();
    }

    @Override
    public void updateItem(ItemStack itemStack) {
        // Legacy mode has no ITEM display support.
    }

    @Override
    public void updateText(String text) {
        this.definition.setText(text);
        this.viewerTexts.clear();
        for (int i = 0; i < this.baseLines.size(); i++) {
            String[] lines = this.plugin.splitLines(this.definition.getText());
            applyLine(this.baseLines.get(i), i < lines.length ? lines[i] : "");
        }
    }

    @Override
    public void move(Location location) {
        this.definition.setLocation(location);
        reposition(this.baseLines);
        for (List<ArmorStand> lines : this.viewerLines.values()) {
            reposition(lines);
        }
    }

    @Override
    public FocusDisplayDefinition getDefinition() {
        return this.definition;
    }

    @Override
    public boolean isSameWorld(Player player) {
        return !this.baseLines.isEmpty() && this.baseLines.get(0).getWorld().equals(player.getWorld());
    }

    @Override
    public void ensureViewerMode(Player player) {
        if (!this.perViewerMode) {
            return;
        }
        boolean visible = this.definition.getConditions().test(this.plugin, player);
        Boolean current = this.viewerVisibility.get(player.getUniqueId());
        boolean changed = current == null || current.booleanValue() != visible;
        this.viewerVisibility.put(player.getUniqueId(), visible);
        if (changed) {
            this.viewerTexts.remove(player.getUniqueId());
        }

        if (visible) {
            hideBaseFor(player);
        } else {
            hideBaseFor(player);
            hideViewerLines(player.getUniqueId(), player);
        }
    }

    @Override
    public void syncViewerText(Player player) {
        if (!this.perViewerMode) {
            syncSharedText(player);
            return;
        }
        if (!isVisibleTo(player)) {
            return;
        }
        String resolved = this.plugin.applyPlaceholders(player, this.definition.getText());
        String current = this.viewerTexts.get(player.getUniqueId());
        if (resolved.equals(current)) {
            return;
        }
        String[] lines = this.plugin.splitLines(resolved);
        List<ArmorStand> personal = ensureViewerLines(player, lines.length);
        for (int i = 0; i < personal.size(); i++) {
            applyLine(personal.get(i), i < lines.length ? lines[i] : "");
        }
        hideBaseFor(player);
        showViewerLines(player.getUniqueId(), player);
        this.viewerTexts.put(player.getUniqueId(), resolved);
    }

    @Override
    public boolean isLookingAt(Player player, double maxDistance, double focusCosine) {
        return false;
    }

    @Override
    public float getViewerScale(UUID uniqueId) {
        return this.definition.getBaseScale();
    }

    @Override
    public void setViewerScale(UUID uniqueId, float scale) {
        // No scale animations for legacy armor-stand mode.
    }

    @Override
    public void sendScale(Player player, float scale) {
        // No scale animations for legacy armor-stand mode.
    }

    @Override
    public void forgetViewer(UUID uniqueId) {
        if (!this.perViewerMode) {
            if (uniqueId != null && uniqueId.equals(this.sharedContextViewer)) {
                this.sharedContextViewer = null;
                this.viewerTexts.clear();
            }
            return;
        }
        this.viewerTexts.remove(uniqueId);
        this.viewerVisibility.remove(uniqueId);
        removeViewerLines(uniqueId);
    }

    @Override
    public void hideFor(Player player) {
        if (!this.perViewerMode) {
            return;
        }
        for (ArmorStand armorStand : this.baseLines) {
            this.plugin.hideEntityCompat(player, armorStand);
        }
        for (List<ArmorStand> lines : this.viewerLines.values()) {
            for (ArmorStand armorStand : lines) {
                this.plugin.hideEntityCompat(player, armorStand);
            }
        }
    }

    @Override
    public void invalidateViewerState(UUID uniqueId) {
        if (!this.perViewerMode) {
            return;
        }
        this.viewerVisibility.remove(uniqueId);
    }

    @Override
    public void resetViewerText(UUID uniqueId) {
        if (!this.perViewerMode) {
            this.viewerTexts.clear();
            return;
        }
        this.viewerTexts.remove(uniqueId);
    }

    private void spawnBaseLines() {
        String[] lines = this.plugin.splitLines(this.definition.getText());
        int count = Math.max(1, lines.length);
        for (int i = 0; i < count; i++) {
            Location lineLocation = lineLocation(i);
            ArmorStand armorStand = lineLocation.getWorld().spawn(lineLocation, ArmorStand.class);
            prepareStand(armorStand, KIND_MAIN + "-" + i);
            applyLine(armorStand, i < lines.length ? lines[i] : "");
            this.baseLines.add(armorStand);
        }
    }

    private List<ArmorStand> ensureViewerLines(Player owner, int lineCount) {
        cleanupUnexpectedEntities();
        UUID viewerId = owner.getUniqueId();
        int normalized = Math.max(1, lineCount);
        List<ArmorStand> existing = this.viewerLines.get(viewerId);
        if (existing != null && existing.size() == normalized) {
            return existing;
        }

        removeViewerLines(viewerId);
        List<ArmorStand> created = new ArrayList<ArmorStand>();
        for (int i = 0; i < normalized; i++) {
            Location lineLocation = lineLocation(i);
            ArmorStand armorStand = lineLocation.getWorld().spawn(lineLocation, ArmorStand.class);
            prepareStand(armorStand, KIND_VIEWER + "-" + i);
            created.add(armorStand);
        }
        this.viewerLines.put(viewerId, created);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(viewerId)) {
                showViewerLines(viewerId, online);
            } else {
                hideViewerLines(viewerId, online);
            }
        }
        return created;
    }

    private void removeViewerLines(UUID viewerId) {
        List<ArmorStand> lines = this.viewerLines.remove(viewerId);
        if (lines != null) {
            removeLines(lines);
        }
    }

    private void removeLines(List<ArmorStand> lines) {
        for (ArmorStand armorStand : lines) {
            if (armorStand != null && armorStand.isValid()) {
                armorStand.remove();
            }
        }
        lines.clear();
    }

    private void reposition(List<ArmorStand> lines) {
        for (int i = 0; i < lines.size(); i++) {
            ArmorStand armorStand = lines.get(i);
            if (armorStand != null && armorStand.isValid()) {
                armorStand.teleport(lineLocation(i));
            }
        }
    }

    private void prepareStand(ArmorStand armorStand, String kind) {
        armorStand.setPersistent(false);
        armorStand.setInvulnerable(true);
        armorStand.setGravity(false);
        armorStand.setVisible(false);
        armorStand.setBasePlate(false);
        armorStand.setArms(false);
        boolean marker = this.definition.getHologramArmorStandMarkerOverride() != null
                ? this.definition.getHologramArmorStandMarkerOverride().booleanValue()
                : this.plugin.getConfig().getBoolean("hologram.armor-stand-marker", true);
        boolean small = this.definition.getHologramArmorStandSmallOverride() != null
                ? this.definition.getHologramArmorStandSmallOverride().booleanValue()
                : this.plugin.getConfig().getBoolean("hologram.armor-stand-small", false);
        armorStand.setMarker(marker);
        armorStand.setSmall(small);
        armorStand.getPersistentDataContainer().set(this.idKey, PersistentDataType.STRING, this.definition.getId());
        armorStand.getPersistentDataContainer().set(this.kindKey, PersistentDataType.STRING, kind);
    }

    private void applyLine(ArmorStand armorStand, String text) {
        String line = text == null ? "" : text;
        if (line.trim().isEmpty()) {
            armorStand.customName(this.plugin.miniMessage("<reset>\u00A0"));
        } else {
            armorStand.customName(this.plugin.miniMessage(line));
        }
        armorStand.setCustomNameVisible(true);
    }

    private Location lineLocation(int index) {
        double spacing = this.definition.getHologramLineSpacingOverride() != null
                ? this.definition.getHologramLineSpacingOverride().doubleValue()
                : this.plugin.getConfig().getDouble("hologram.line-spacing", 0.27D);
        return this.definition.getLocation().clone().add(0.0D, -spacing * index, 0.0D);
    }

    private boolean isVisibleTo(Player player) {
        Boolean visible = this.viewerVisibility.get(player.getUniqueId());
        return visible == null || visible.booleanValue();
    }

    private void syncSharedText(Player fallback) {
        Player context = resolveSharedContextPlayer(fallback);
        if (context == null) {
            return;
        }
        String resolved = this.plugin.applyPlaceholders(context, this.definition.getText());
        String current = this.viewerTexts.get(context.getUniqueId());
        if (resolved.equals(current)) {
            return;
        }
        String[] lines = this.plugin.splitLines(resolved);
        for (int i = 0; i < this.baseLines.size(); i++) {
            applyLine(this.baseLines.get(i), i < lines.length ? lines[i] : "");
        }
        this.viewerTexts.clear();
        this.viewerTexts.put(context.getUniqueId(), resolved);
    }

    private Player resolveSharedContextPlayer(Player fallback) {
        if (this.sharedContextViewer != null) {
            Player existing = Bukkit.getPlayer(this.sharedContextViewer);
            if (existing != null && existing.isOnline()) {
                return existing;
            }
            this.sharedContextViewer = null;
        }
        if (fallback != null && fallback.isOnline()) {
            this.sharedContextViewer = fallback.getUniqueId();
            return fallback;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            this.sharedContextViewer = online.getUniqueId();
            return online;
        }
        return null;
    }

    private void hideBaseFor(Player player) {
        for (ArmorStand armorStand : this.baseLines) {
            this.plugin.hideEntityCompat(player, armorStand);
        }
    }

    private void showViewerLines(UUID viewerId, Player player) {
        List<ArmorStand> lines = this.viewerLines.get(viewerId);
        if (lines == null) {
            return;
        }
        for (ArmorStand armorStand : lines) {
            this.plugin.showEntityCompat(player, armorStand);
        }
    }

    private void hideViewerLines(UUID viewerId, Player player) {
        List<ArmorStand> lines = this.viewerLines.get(viewerId);
        if (lines == null) {
            return;
        }
        for (ArmorStand armorStand : lines) {
            this.plugin.hideEntityCompat(player, armorStand);
        }
    }

    private void cleanupUnexpectedEntities() {
        Location base = this.definition.getLocation();
        if (base == null || base.getWorld() == null) {
            return;
        }
        Set<UUID> keep = new HashSet<UUID>();
        collectArmorStandIds(this.baseLines, keep);
        for (List<ArmorStand> stands : this.viewerLines.values()) {
            collectArmorStandIds(stands, keep);
        }
        for (Entity entity : new ArrayList<Entity>(base.getWorld().getEntities())) {
            String value = entity.getPersistentDataContainer().get(this.idKey, PersistentDataType.STRING);
            if (value == null || !this.definition.getId().equalsIgnoreCase(value)) {
                continue;
            }
            if (!keep.contains(entity.getUniqueId())) {
                entity.remove();
            }
        }
    }

    private static void collectArmorStandIds(List<ArmorStand> stands, Set<UUID> keep) {
        for (ArmorStand armorStand : stands) {
            if (armorStand != null && armorStand.isValid()) {
                keep.add(armorStand.getUniqueId());
            }
        }
    }
}
