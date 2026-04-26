package net.enelson.sopfocusdisplays.model;

import com.comphenix.protocol.ProtocolManager;
import net.enelson.sopfocusdisplays.SopFocusDisplays;
import net.enelson.sopfocusdisplays.util.MetadataPackets;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SpawnedFocusDisplay {

    private static final String KIND_MAIN = "main";
    private static final String KIND_SHADOW = "shadow";

    private final SopFocusDisplays plugin;
    private final FocusDisplayDefinition definition;
    private final NamespacedKey idKey;
    private final NamespacedKey kindKey;
    private final Map<UUID, Float> viewerScales = new HashMap<UUID, Float>();
    private final Map<UUID, String> viewerTexts = new HashMap<UUID, String>();
    private final Map<UUID, Boolean> viewerVisibility = new HashMap<UUID, Boolean>();
    private final List<ArmorStand> hologramMain = new ArrayList<ArmorStand>();
    private final List<ArmorStand> hologramShadow = new ArrayList<ArmorStand>();
    private final Map<UUID, List<ArmorStand>> viewerHolograms = new HashMap<UUID, List<ArmorStand>>();

    private Display mainDisplay;
    private Display shadowDisplay;

    public SpawnedFocusDisplay(SopFocusDisplays plugin, FocusDisplayDefinition definition) {
        this.plugin = plugin;
        this.definition = definition;
        this.idKey = new NamespacedKey(plugin, "display-id");
        this.kindKey = new NamespacedKey(plugin, "display-kind");
    }

    public void spawn() {
        Location location = this.definition.getLocation();
        if (this.definition.getType() == FocusDisplayType.TEXT) {
            this.mainDisplay = location.getWorld().spawn(location, TextDisplay.class);
            prepareTextDisplay((TextDisplay) this.mainDisplay, "", this.definition.getBaseScale(), KIND_MAIN);

            this.shadowDisplay = location.getWorld().spawn(location, TextDisplay.class);
            prepareTextDisplay((TextDisplay) this.shadowDisplay, "", this.definition.getBaseScale(), KIND_SHADOW);
        } else if (this.definition.getType() == FocusDisplayType.HOLOGRAM) {
            spawnHologramLines();
        } else {
            this.mainDisplay = location.getWorld().spawn(location, ItemDisplay.class);
            prepareItemDisplay((ItemDisplay) this.mainDisplay, this.definition.getItemStack(), this.definition.getBaseScale(), KIND_MAIN);

            this.shadowDisplay = location.getWorld().spawn(location, ItemDisplay.class);
            prepareItemDisplay((ItemDisplay) this.shadowDisplay, this.definition.getItemStack(), this.definition.getBaseScale(), KIND_SHADOW);
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (this.definition.getType() == FocusDisplayType.HOLOGRAM) {
                for (ArmorStand armorStand : this.hologramMain) {
                    player.hideEntity(this.plugin, armorStand);
                }
                for (ArmorStand armorStand : this.hologramShadow) {
                    player.hideEntity(this.plugin, armorStand);
                }
            } else {
                player.hideEntity(this.plugin, this.mainDisplay);
                player.hideEntity(this.plugin, this.shadowDisplay);
            }
        }
    }

    public void remove() {
        removeHologramLines(this.hologramMain);
        removeHologramLines(this.hologramShadow);
        removeAllViewerHolograms();
        if (this.mainDisplay != null && this.mainDisplay.isValid()) {
            this.mainDisplay.remove();
        }
        if (this.shadowDisplay != null && this.shadowDisplay.isValid()) {
            this.shadowDisplay.remove();
        }
        this.viewerScales.clear();
        this.viewerTexts.clear();
        this.viewerVisibility.clear();
    }

    public void updateItem(ItemStack itemStack) {
        this.definition.setItemStack(itemStack);
        if (this.definition.getType() != FocusDisplayType.ITEM) {
            return;
        }
        ((ItemDisplay) this.mainDisplay).setItemStack(itemStack == null ? null : itemStack.clone());
        ((ItemDisplay) this.shadowDisplay).setItemStack(itemStack == null ? null : itemStack.clone());
    }

    public void updateText(String text) {
        this.definition.setText(text);
        this.viewerTexts.clear();
    }

    public void move(Location location) {
        this.definition.setLocation(location);
        if (this.definition.getType() == FocusDisplayType.HOLOGRAM) {
            repositionHologramLines(this.hologramMain);
            repositionHologramLines(this.hologramShadow);
            repositionViewerHolograms();
        } else {
            this.mainDisplay.teleport(location);
            this.shadowDisplay.teleport(location);
        }
    }

    public FocusDisplayDefinition getDefinition() {
        return this.definition;
    }

    public Display getMainDisplay() {
        return this.mainDisplay;
    }

    public boolean isSameWorld(Player player) {
        if (this.definition.getType() == FocusDisplayType.HOLOGRAM) {
            return !this.hologramMain.isEmpty() && this.hologramMain.get(0).getWorld().equals(player.getWorld());
        }
        return this.mainDisplay != null && this.mainDisplay.getWorld().equals(player.getWorld());
    }

    public void ensureViewerMode(Player player) {
        boolean visible = this.definition.getConditions().test(this.plugin, player);
        if (this.plugin.getConfig().getBoolean("debug.conditions", false)) {
            this.plugin.getLogger().info("[debug] display player=" + player.getName()
                    + " id=" + this.definition.getId()
                    + " type=" + this.definition.getType().name()
                    + " visible=" + visible);
        }
        Boolean currentVisibility = this.viewerVisibility.get(player.getUniqueId());
        if (currentVisibility != null && currentVisibility.booleanValue() == visible) {
            return;
        }

        this.viewerVisibility.put(player.getUniqueId(), visible);
        this.viewerTexts.remove(player.getUniqueId());
        if (visible) {
            if (this.definition.getType() == FocusDisplayType.HOLOGRAM) {
                for (ArmorStand armorStand : this.hologramMain) {
                    player.hideEntity(this.plugin, armorStand);
                }
                for (ArmorStand armorStand : this.hologramShadow) {
                    player.hideEntity(this.plugin, armorStand);
                }
                hideViewerHologram(player.getUniqueId(), player);
            } else {
                player.showEntity(this.plugin, this.mainDisplay);
                player.hideEntity(this.plugin, this.shadowDisplay);
            }
        } else {
            if (this.definition.getType() == FocusDisplayType.HOLOGRAM) {
                for (ArmorStand armorStand : this.hologramMain) {
                    player.hideEntity(this.plugin, armorStand);
                }
                for (ArmorStand armorStand : this.hologramShadow) {
                    player.hideEntity(this.plugin, armorStand);
                }
                hideViewerHologram(player.getUniqueId(), player);
            } else {
                player.hideEntity(this.plugin, this.mainDisplay);
                player.hideEntity(this.plugin, this.shadowDisplay);
            }
        }
    }

    public void forgetViewer(UUID uniqueId) {
        this.viewerScales.remove(uniqueId);
        this.viewerTexts.remove(uniqueId);
        this.viewerVisibility.remove(uniqueId);
        removeViewerHologram(uniqueId);
    }

    public void invalidateViewerState(UUID uniqueId) {
        this.viewerVisibility.remove(uniqueId);
    }

    public void resetViewerText(UUID uniqueId) {
        this.viewerTexts.remove(uniqueId);
    }

    public float getViewerScale(UUID uniqueId) {
        Float scale = this.viewerScales.get(uniqueId);
        return scale == null ? this.definition.getBaseScale() : scale.floatValue();
    }

    public void setViewerScale(UUID uniqueId, float scale) {
        this.viewerScales.put(uniqueId, scale);
    }

    public void sendScale(Player player, float scale) {
        if (!isVisibleTo(player) || this.definition.getType() == FocusDisplayType.HOLOGRAM) {
            return;
        }

        ProtocolManager protocolManager = this.plugin.getProtocolManager();
        if (this.definition.getType() == FocusDisplayType.TEXT) {
            String currentText = this.viewerTexts.get(player.getUniqueId());
            ((TextDisplay) this.shadowDisplay).text(this.plugin.miniMessage(currentText == null ? "" : currentText));
        }
        applyScale(this.shadowDisplay, scale);
        MetadataPackets.sendEntityMetadata(protocolManager, player, this.mainDisplay.getEntityId(), this.shadowDisplay);
    }

    public void syncViewerText(Player player) {
        if ((this.definition.getType() != FocusDisplayType.TEXT && this.definition.getType() != FocusDisplayType.HOLOGRAM) || !isVisibleTo(player)) {
            return;
        }

        String resolved = this.plugin.applyPlaceholders(player, this.definition.getText());
        String current = this.viewerTexts.get(player.getUniqueId());
        if (resolved.equals(current)) {
            return;
        }

        if (this.definition.getType() == FocusDisplayType.HOLOGRAM) {
            String[] lines = this.plugin.splitLines(resolved);
            List<ArmorStand> personal = ensureViewerHologram(player, lines.length);
            for (int i = 0; i < personal.size(); i++) {
                String line = i < lines.length ? lines[i] : "";
                ArmorStand personalLine = personal.get(i);
                applyHologramLine(personalLine, line);
            }
            hideBaseHologramFor(player);
            showViewerHologram(player.getUniqueId(), player);
        } else {
            ((TextDisplay) this.shadowDisplay).text(this.plugin.miniMessage(resolved));
            MetadataPackets.sendEntityMetadata(this.plugin.getProtocolManager(), player, this.mainDisplay.getEntityId(), this.shadowDisplay);
            player.showEntity(this.plugin, this.mainDisplay);
        }
        this.viewerTexts.put(player.getUniqueId(), resolved);
    }

    public boolean isLookingAt(Player player, double maxDistance, double focusCosine) {
        Location baseLocation;
        if (this.definition.getType() == FocusDisplayType.HOLOGRAM) {
            if (this.hologramMain.isEmpty() || !player.getWorld().equals(this.hologramMain.get(0).getWorld())) {
                return false;
            }
            baseLocation = this.hologramMain.get(0).getLocation();
        } else {
            if (this.mainDisplay == null || !player.getWorld().equals(this.mainDisplay.getWorld())) {
                return false;
            }
            baseLocation = this.mainDisplay.getLocation();
        }
        if (player.getLocation().distanceSquared(baseLocation) > maxDistance * maxDistance) {
            return false;
        }
        if (this.definition.getType() != FocusDisplayType.HOLOGRAM && !player.hasLineOfSight(this.mainDisplay)) {
            return false;
        }

        Location eye = player.getEyeLocation();
        Vector3f direction = new Vector3f((float) eye.getDirection().getX(), (float) eye.getDirection().getY(), (float) eye.getDirection().getZ());
        Location anchor = baseLocation.clone().add(0.0D, 0.1D, 0.0D);
        Vector3f toDisplay = new Vector3f(
                (float) (anchor.getX() - eye.getX()),
                (float) (anchor.getY() - eye.getY()),
                (float) (anchor.getZ() - eye.getZ())
        );
        if (toDisplay.lengthSquared() <= 0.0001F) {
            return true;
        }
        toDisplay.normalize();
        direction.normalize();
        return direction.dot(toDisplay) >= focusCosine;
    }

    private boolean isVisibleTo(Player player) {
        Boolean visible = this.viewerVisibility.get(player.getUniqueId());
        return visible == null || visible.booleanValue();
    }

    private void prepareItemDisplay(ItemDisplay display, ItemStack itemStack, float scale, String kind) {
        prepareDisplayBase(display, scale, kind);
        display.setItemStack(itemStack == null ? null : itemStack.clone());
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.valueOf(this.plugin.getConfig().getString("display.item-transform", "FIXED")));
    }

    private void prepareTextDisplay(TextDisplay display, String text, float scale, String kind) {
        prepareDisplayBase(display, scale, kind);
        display.text(this.plugin.miniMessage(text));
        display.setLineWidth(this.plugin.getConfig().getInt("text.line-width", 200));
        display.setSeeThrough(this.plugin.getConfig().getBoolean("text.see-through", true));
        display.setShadowed(this.plugin.getConfig().getBoolean("text.shadowed", false));
        display.setAlignment(TextDisplay.TextAlignment.valueOf(this.plugin.getConfig().getString("text.default-alignment", "CENTER")));
        if (this.plugin.getConfig().getBoolean("text.background-enabled", false)) {
            display.setBackgroundColor(parseColor(this.plugin.getConfig().getString("text.background-color", "#00000000")));
        } else {
            display.setBackgroundColor(Color.fromARGB(0x00000000));
        }
    }

    private void spawnHologramLines() {
        String[] lines = this.plugin.splitLines(this.definition.getText());
        int lineCount = Math.max(1, lines.length);
        for (int i = 0; i < lineCount; i++) {
            Location lineLocation = getHologramLineLocation(i);
            ArmorStand mainLine = lineLocation.getWorld().spawn(lineLocation, ArmorStand.class);
            prepareHologramStand(mainLine, "", KIND_MAIN + "-" + i);
            this.hologramMain.add(mainLine);
            if (i == 0) {
                this.mainDisplay = null;
            }

            ArmorStand shadowLine = lineLocation.getWorld().spawn(lineLocation, ArmorStand.class);
            prepareHologramStand(shadowLine, "", KIND_SHADOW + "-" + i);
            this.hologramShadow.add(shadowLine);
        }
    }

    private List<ArmorStand> ensureViewerHologram(Player owner, int lineCount) {
        UUID viewerId = owner.getUniqueId();
        int normalized = Math.max(1, lineCount);
        List<ArmorStand> existing = this.viewerHolograms.get(viewerId);
        if (existing != null && existing.size() == normalized) {
            return existing;
        }

        removeViewerHologram(viewerId);
        List<ArmorStand> created = new ArrayList<ArmorStand>();
        for (int i = 0; i < normalized; i++) {
            Location lineLocation = getHologramLineLocation(i);
            ArmorStand armorStand = lineLocation.getWorld().spawn(lineLocation, ArmorStand.class);
            prepareHologramStand(armorStand, "", KIND_SHADOW + "-viewer-" + i);
            created.add(armorStand);
        }
        this.viewerHolograms.put(viewerId, created);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(viewerId)) {
                showViewerHologram(viewerId, online);
            } else {
                hideViewerHologram(viewerId, online);
            }
        }
        return created;
    }

    private void prepareHologramStand(ArmorStand armorStand, String text, String kind) {
        armorStand.setPersistent(false);
        armorStand.setInvulnerable(true);
        armorStand.setGravity(false);
        armorStand.setVisible(false);
        armorStand.setBasePlate(false);
        armorStand.setArms(false);
        armorStand.setMarker(this.plugin.getConfig().getBoolean("hologram.armor-stand-marker", true));
        armorStand.setSmall(this.plugin.getConfig().getBoolean("hologram.armor-stand-small", false));
        applyHologramLine(armorStand, text);
        armorStand.getPersistentDataContainer().set(this.idKey, PersistentDataType.STRING, this.definition.getId());
        armorStand.getPersistentDataContainer().set(this.kindKey, PersistentDataType.STRING, kind);
    }

    private void applyHologramLine(ArmorStand armorStand, String text) {
        String line = text == null ? "" : text;
        if (line.trim().isEmpty()) {
            armorStand.customName(this.plugin.miniMessage("<reset>\u00A0"));
            armorStand.setCustomNameVisible(true);
            return;
        }

        armorStand.customName(this.plugin.miniMessage(line));
        armorStand.setCustomNameVisible(true);
    }

    private void repositionHologramLines(List<ArmorStand> stands) {
        for (int i = 0; i < stands.size(); i++) {
            ArmorStand armorStand = stands.get(i);
            if (armorStand != null && armorStand.isValid()) {
                armorStand.teleport(getHologramLineLocation(i));
            }
        }
    }

    private void repositionViewerHolograms() {
        for (List<ArmorStand> stands : this.viewerHolograms.values()) {
            repositionHologramLines(stands);
        }
    }

    private void removeHologramLines(List<ArmorStand> stands) {
        for (ArmorStand armorStand : stands) {
            if (armorStand != null && armorStand.isValid()) {
                armorStand.remove();
            }
        }
        stands.clear();
    }

    private void removeViewerHologram(UUID viewerId) {
        List<ArmorStand> stands = this.viewerHolograms.remove(viewerId);
        if (stands != null) {
            removeHologramLines(stands);
        }
    }

    private void removeAllViewerHolograms() {
        for (UUID viewerId : new ArrayList<UUID>(this.viewerHolograms.keySet())) {
            removeViewerHologram(viewerId);
        }
    }

    private void hideBaseHologramFor(Player player) {
        for (ArmorStand armorStand : this.hologramMain) {
            player.hideEntity(this.plugin, armorStand);
        }
        for (ArmorStand armorStand : this.hologramShadow) {
            player.hideEntity(this.plugin, armorStand);
        }
    }

    private void showViewerHologram(UUID viewerId, Player player) {
        List<ArmorStand> stands = this.viewerHolograms.get(viewerId);
        if (stands == null) {
            return;
        }
        for (ArmorStand armorStand : stands) {
            player.showEntity(this.plugin, armorStand);
        }
    }

    private void hideViewerHologram(UUID viewerId, Player player) {
        List<ArmorStand> stands = this.viewerHolograms.get(viewerId);
        if (stands == null) {
            return;
        }
        for (ArmorStand armorStand : stands) {
            player.hideEntity(this.plugin, armorStand);
        }
    }

    private Location getHologramLineLocation(int lineIndex) {
        Location base = this.definition.getLocation().clone();
        double spacing = this.plugin.getConfig().getDouble("hologram.line-spacing", 0.27D);
        return base.add(0.0D, -spacing * lineIndex, 0.0D);
    }

    private void prepareDisplayBase(Display display, float scale, String kind) {
        display.setPersistent(false);
        display.setInvulnerable(true);
        display.setGravity(false);
        display.setBillboard(Display.Billboard.valueOf(this.plugin.getConfig().getString("display.billboard", "FIXED")));
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(1);
        display.setTeleportDuration(1);
        display.setDisplayWidth((float) this.plugin.getConfig().getDouble("display.display-width", 0.7D) * scale);
        display.setDisplayHeight((float) this.plugin.getConfig().getDouble("display.display-height", 0.7D) * scale);
        int blockBrightness = this.plugin.getConfig().getInt("display.brightness-block", -1);
        int skyBrightness = this.plugin.getConfig().getInt("display.brightness-sky", -1);
        if (blockBrightness >= 0 || skyBrightness >= 0) {
            display.setBrightness(new Display.Brightness(Math.max(0, blockBrightness), Math.max(0, skyBrightness)));
        }
        display.setShadowRadius((float) this.plugin.getConfig().getDouble("display.shadow-radius", 0.0D));
        display.setShadowStrength((float) this.plugin.getConfig().getDouble("display.shadow-strength", 0.0D));
        display.setRotation(this.definition.getLocation().getYaw(), this.definition.getLocation().getPitch());
        applyScale(display, scale);
        display.getPersistentDataContainer().set(this.idKey, PersistentDataType.STRING, this.definition.getId());
        display.getPersistentDataContainer().set(this.kindKey, PersistentDataType.STRING, kind);
    }

    private void applyScale(Display display, float scale) {
        display.setDisplayWidth((float) this.plugin.getConfig().getDouble("display.display-width", 0.7D) * scale);
        display.setDisplayHeight((float) this.plugin.getConfig().getDouble("display.display-height", 0.7D) * scale);
        display.setTransformation(new Transformation(
                new Vector3f(0F, 0F, 0F),
                new AxisAngle4f(),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f()
        ));
    }

    private Color parseColor(String input) {
        try {
            String value = input.startsWith("#") ? input.substring(1) : input;
            long raw = Long.parseLong(value, 16);
            if (value.length() <= 6) {
                int rgb = (int) raw;
                return Color.fromARGB(0xFF000000 | rgb);
            }
            return Color.fromARGB((int) raw);
        } catch (Throwable ignored) {
            return Color.fromARGB(0x00000000);
        }
    }
}
