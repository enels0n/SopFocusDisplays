package net.enelson.sopdisplays.model;

import com.comphenix.protocol.ProtocolManager;
import net.enelson.sopdisplays.SopDisplays;
import net.enelson.sopdisplays.util.MetadataPackets;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SpawnedFocusDisplay implements ManagedFocusDisplay {

    private static final String KIND_MAIN = "main";
    private static final String KIND_SHADOW = "shadow";

    private final SopDisplays plugin;
    private final FocusDisplayDefinition definition;
    private final NamespacedKey idKey;
    private final NamespacedKey kindKey;
    private final Map<UUID, Float> viewerScales = new HashMap<UUID, Float>();
    private final Map<UUID, String> viewerTexts = new HashMap<UUID, String>();
    private final Map<UUID, Boolean> viewerVisibility = new HashMap<UUID, Boolean>();
    private final List<ArmorStand> hologramMain = new ArrayList<ArmorStand>();
    private final List<ArmorStand> hologramShadow = new ArrayList<ArmorStand>();
    private final Map<UUID, List<ArmorStand>> viewerHolograms = new HashMap<UUID, List<ArmorStand>>();
    private final Map<UUID, TextDisplay> viewerTextDisplays = new HashMap<UUID, TextDisplay>();

    private Display mainDisplay;
    private Display shadowDisplay;

    public SpawnedFocusDisplay(SopDisplays plugin, FocusDisplayDefinition definition) {
        this.plugin = plugin;
        this.definition = definition;
        this.idKey = new NamespacedKey(plugin, "display-id");
        this.kindKey = new NamespacedKey(plugin, "display-kind");
    }

    @Override
    public void spawn() {
        cleanupUnexpectedEntities();
        Location location = this.definition.getLocation();
        if (usesTextDisplayEntity()) {
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
            if (usesArmorStandHologram()) {
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

    @Override
    public void remove() {
        removeHologramLines(this.hologramMain);
        removeHologramLines(this.hologramShadow);
        removeAllViewerHolograms();
        removeAllViewerTextDisplays();
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

    @Override
    public void updateItem(ItemStack itemStack) {
        this.definition.setItemStack(itemStack);
        if (this.definition.getType() != FocusDisplayType.ITEM) {
            return;
        }
        ((ItemDisplay) this.mainDisplay).setItemStack(itemStack == null ? null : itemStack.clone());
        ((ItemDisplay) this.shadowDisplay).setItemStack(itemStack == null ? null : itemStack.clone());
    }

    @Override
    public void updateText(String text) {
        this.definition.setText(text);
        this.viewerTexts.clear();
    }

    @Override
    public void move(Location location) {
        this.definition.setLocation(location);
        if (usesArmorStandHologram()) {
            repositionHologramLines(this.hologramMain);
            repositionHologramLines(this.hologramShadow);
            repositionViewerHolograms();
        } else if (usesTextDisplayEntity()) {
            this.mainDisplay.teleport(location);
            this.shadowDisplay.teleport(location);
            repositionViewerTextDisplays();
        } else {
            this.mainDisplay.teleport(location);
            this.shadowDisplay.teleport(location);
        }
    }

    @Override
    public FocusDisplayDefinition getDefinition() {
        return this.definition;
    }

    public Display getMainDisplay() {
        return this.mainDisplay;
    }

    @Override
    public boolean isSameWorld(Player player) {
        if (usesArmorStandHologram()) {
            return !this.hologramMain.isEmpty() && this.hologramMain.get(0).getWorld().equals(player.getWorld());
        }
        return this.mainDisplay != null && this.mainDisplay.getWorld().equals(player.getWorld());
    }

    @Override
    public void ensureViewerMode(Player player) {
        boolean visible = this.definition.getConditions().test(this.plugin, player);
        if (this.plugin.getConfig().getBoolean("debug.conditions", false)) {
            this.plugin.getLogger().info("[debug] display player=" + player.getName()
                    + " id=" + this.definition.getId()
                    + " type=" + this.definition.getType().name()
                    + " visible=" + visible);
        }
        Boolean currentVisibility = this.viewerVisibility.get(player.getUniqueId());
        boolean changed = currentVisibility == null || currentVisibility.booleanValue() != visible;

        this.viewerVisibility.put(player.getUniqueId(), visible);
        if (changed) {
            this.viewerTexts.remove(player.getUniqueId());
        }

        if (usesArmorStandHologram()) {
            for (ArmorStand armorStand : this.hologramMain) {
                player.hideEntity(this.plugin, armorStand);
            }
            for (ArmorStand armorStand : this.hologramShadow) {
                player.hideEntity(this.plugin, armorStand);
            }
            if (!visible) {
                hideViewerHologram(player.getUniqueId(), player);
            }
            return;
        }

        if (usesTextDisplayEntity()) {
            player.hideEntity(this.plugin, this.mainDisplay);
            player.hideEntity(this.plugin, this.shadowDisplay);
            if (!visible) {
                hideViewerTextDisplay(player.getUniqueId(), player);
            }
            return;
        }

        if (visible) {
            player.showEntity(this.plugin, this.mainDisplay);
            player.hideEntity(this.plugin, this.shadowDisplay);
        } else {
            player.hideEntity(this.plugin, this.mainDisplay);
            player.hideEntity(this.plugin, this.shadowDisplay);
        }
    }

    @Override
    public void forgetViewer(UUID uniqueId) {
        this.viewerScales.remove(uniqueId);
        this.viewerTexts.remove(uniqueId);
        this.viewerVisibility.remove(uniqueId);
        removeViewerHologram(uniqueId);
        removeViewerTextDisplay(uniqueId);
    }

    @Override
    public void hideFor(Player player) {
        if (player == null) {
            return;
        }

        if (this.mainDisplay != null) {
            player.hideEntity(this.plugin, this.mainDisplay);
        }
        if (this.shadowDisplay != null) {
            player.hideEntity(this.plugin, this.shadowDisplay);
        }
        for (TextDisplay textDisplay : this.viewerTextDisplays.values()) {
            player.hideEntity(this.plugin, textDisplay);
        }
        for (ArmorStand armorStand : this.hologramMain) {
            player.hideEntity(this.plugin, armorStand);
        }
        for (ArmorStand armorStand : this.hologramShadow) {
            player.hideEntity(this.plugin, armorStand);
        }
        for (List<ArmorStand> viewerLines : this.viewerHolograms.values()) {
            for (ArmorStand armorStand : viewerLines) {
                player.hideEntity(this.plugin, armorStand);
            }
        }
    }

    @Override
    public void invalidateViewerState(UUID uniqueId) {
        this.viewerVisibility.remove(uniqueId);
    }

    @Override
    public void resetViewerText(UUID uniqueId) {
        this.viewerTexts.remove(uniqueId);
    }

    @Override
    public float getViewerScale(UUID uniqueId) {
        Float scale = this.viewerScales.get(uniqueId);
        return scale == null ? this.definition.getBaseScale() : scale.floatValue();
    }

    @Override
    public void setViewerScale(UUID uniqueId, float scale) {
        this.viewerScales.put(uniqueId, scale);
    }

    @Override
    public void sendScale(Player player, float scale) {
        if (!isVisibleTo(player) || this.definition.getType() == FocusDisplayType.HOLOGRAM) {
            return;
        }

        if (usesTextDisplayEntity()) {
            TextDisplay viewerDisplay = ensureViewerTextDisplay(player);
            applyScale(viewerDisplay, scale);
            showViewerTextDisplay(player.getUniqueId(), player);
            return;
        }
        ProtocolManager protocolManager = this.plugin.getProtocolManager();
        applyScale(this.shadowDisplay, scale);
        MetadataPackets.sendEntityMetadata(protocolManager, player, this.mainDisplay.getEntityId(), this.shadowDisplay);
    }

    @Override
    public void syncViewerText(Player player) {
        if ((this.definition.getType() != FocusDisplayType.TEXT && this.definition.getType() != FocusDisplayType.HOLOGRAM) || !isVisibleTo(player)) {
            return;
        }

        String resolved = this.plugin.applyPlaceholders(player, this.definition.getText());
        String current = this.viewerTexts.get(player.getUniqueId());
        if (resolved.equals(current)) {
            return;
        }

        if (usesArmorStandHologram()) {
            String[] lines = this.plugin.splitLines(resolved);
            List<ArmorStand> personal = ensureViewerHologram(player, lines.length);
            for (int i = 0; i < personal.size(); i++) {
                String line = i < lines.length ? lines[i] : "";
                ArmorStand personalLine = personal.get(i);
                applyHologramLine(personalLine, line);
            }
            hideBaseHologramFor(player);
            showViewerHologram(player.getUniqueId(), player);
        } else if (usesTextDisplayEntity()) {
            TextDisplay viewerDisplay = ensureViewerTextDisplay(player);
            viewerDisplay.text(this.plugin.miniMessage(resolved));
            applyScale(viewerDisplay, getViewerScale(player.getUniqueId()));
            player.hideEntity(this.plugin, this.mainDisplay);
            player.hideEntity(this.plugin, this.shadowDisplay);
            showViewerTextDisplay(player.getUniqueId(), player);
        } else {
            ((TextDisplay) this.shadowDisplay).text(this.plugin.miniMessage(resolved));
            MetadataPackets.sendEntityMetadata(this.plugin.getProtocolManager(), player, this.mainDisplay.getEntityId(), this.shadowDisplay);
            player.showEntity(this.plugin, this.mainDisplay);
        }
        this.viewerTexts.put(player.getUniqueId(), resolved);
    }

    @Override
    public boolean isLookingAt(Player player, double maxDistance, double focusCosine) {
        Location baseLocation;
        if (usesArmorStandHologram()) {
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
        if (!usesArmorStandHologram() && !player.hasLineOfSight(this.mainDisplay)) {
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
        display.setItemDisplayTransform(resolveItemTransform());
    }

    private void prepareTextDisplay(TextDisplay display, String text, float scale, String kind) {
        prepareDisplayBase(display, scale, kind);
        display.text(this.plugin.miniMessage(text));
        display.setLineWidth(getTextLineWidth());
        display.setSeeThrough(isTextSeeThrough());
        display.setShadowed(isTextShadowed());
        display.setAlignment(resolveTextAlignment());
        if (isBackgroundEnabled()) {
            display.setBackgroundColor(parseColor(getBackgroundColor()));
        } else {
            display.setBackgroundColor(Color.fromARGB(0x00000000));
        }
    }

    private boolean usesArmorStandHologram() {
        return this.definition.getType() == FocusDisplayType.HOLOGRAM && !usesTextDisplayHologram();
    }

    private boolean usesTextDisplayEntity() {
        return this.definition.getType() == FocusDisplayType.TEXT || usesTextDisplayHologram();
    }

    private boolean usesTextDisplayHologram() {
        if (!this.plugin.supportsDisplayEntities()) {
            return false;
        }
        if (this.definition.getType() != FocusDisplayType.HOLOGRAM) {
            return false;
        }
        String renderer = this.definition.getHologramRenderer();
        if (renderer == null || renderer.trim().isEmpty()) {
            renderer = this.plugin.getConfig().getString("hologram.renderer", "ARMOR_STAND");
        }
        return "TEXT_DISPLAY".equalsIgnoreCase(renderer);
    }

    private boolean isBackgroundEnabled() {
        Boolean override = this.definition.getBackgroundEnabledOverride();
        if (override != null) {
            return override.booleanValue();
        }
        return this.plugin.getConfig().getBoolean("text.background-enabled", false);
    }

    private String getBackgroundColor() {
        String override = this.definition.getBackgroundColorOverride();
        if (override != null && !override.trim().isEmpty()) {
            return override;
        }
        return this.plugin.getConfig().getString("text.background-color", "#00000000");
    }

    private boolean isTextShadowed() {
        Boolean override = this.definition.getTextShadowedOverride();
        if (override != null) {
            return override.booleanValue();
        }
        return this.plugin.getConfig().getBoolean("text.shadowed", false);
    }

    private int getTextLineWidth() {
        Integer override = this.definition.getTextLineWidthOverride();
        if (override != null) {
            return Math.max(1, override.intValue());
        }
        return this.plugin.getConfig().getInt("text.line-width", 200);
    }

    private boolean isTextSeeThrough() {
        Boolean override = this.definition.getTextSeeThroughOverride();
        if (override != null) {
            return override.booleanValue();
        }
        return this.plugin.getConfig().getBoolean("text.see-through", true);
    }

    private TextDisplay.TextAlignment resolveTextAlignment() {
        String value = this.definition.getTextAlignmentOverride();
        if (value == null || value.trim().isEmpty()) {
            value = this.plugin.getConfig().getString("text.default-alignment", "CENTER");
        }
        try {
            return TextDisplay.TextAlignment.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return TextDisplay.TextAlignment.CENTER;
        }
    }

    private ItemDisplay.ItemDisplayTransform resolveItemTransform() {
        String value = this.definition.getItemTransformOverride();
        if (value == null || value.trim().isEmpty()) {
            value = this.plugin.getConfig().getString("display.item-transform", "FIXED");
        }
        try {
            return ItemDisplay.ItemDisplayTransform.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return ItemDisplay.ItemDisplayTransform.FIXED;
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
        cleanupUnexpectedEntities();
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

    private TextDisplay ensureViewerTextDisplay(Player owner) {
        cleanupUnexpectedEntities();
        UUID viewerId = owner.getUniqueId();
        TextDisplay existing = this.viewerTextDisplays.get(viewerId);
        if (existing != null && existing.isValid()) {
            return existing;
        }

        TextDisplay created = this.definition.getLocation().getWorld().spawn(this.definition.getLocation(), TextDisplay.class);
        prepareTextDisplay(created, "", this.definition.getBaseScale(), KIND_SHADOW + "-viewer");
        this.viewerTextDisplays.put(viewerId, created);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getUniqueId().equals(viewerId)) {
                online.showEntity(this.plugin, created);
            } else {
                online.hideEntity(this.plugin, created);
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
        armorStand.setMarker(isHologramArmorStandMarker());
        armorStand.setSmall(isHologramArmorStandSmall());
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

    private void repositionViewerTextDisplays() {
        Location location = this.definition.getLocation();
        for (TextDisplay textDisplay : this.viewerTextDisplays.values()) {
            if (textDisplay != null && textDisplay.isValid()) {
                textDisplay.teleport(location);
            }
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

    private void removeViewerTextDisplay(UUID viewerId) {
        TextDisplay textDisplay = this.viewerTextDisplays.remove(viewerId);
        if (textDisplay != null && textDisplay.isValid()) {
            textDisplay.remove();
        }
    }

    private void removeAllViewerTextDisplays() {
        for (UUID viewerId : new ArrayList<UUID>(this.viewerTextDisplays.keySet())) {
            removeViewerTextDisplay(viewerId);
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

    private void showViewerTextDisplay(UUID viewerId, Player player) {
        TextDisplay textDisplay = this.viewerTextDisplays.get(viewerId);
        if (textDisplay != null) {
            player.showEntity(this.plugin, textDisplay);
        }
    }

    private void hideViewerTextDisplay(UUID viewerId, Player player) {
        TextDisplay textDisplay = this.viewerTextDisplays.get(viewerId);
        if (textDisplay != null) {
            player.hideEntity(this.plugin, textDisplay);
        }
    }

    private Location getHologramLineLocation(int lineIndex) {
        Location base = this.definition.getLocation().clone();
        double spacing = getHologramLineSpacing();
        return base.add(0.0D, -spacing * lineIndex, 0.0D);
    }

    private void prepareDisplayBase(Display display, float scale, String kind) {
        display.setPersistent(false);
        display.setInvulnerable(true);
        display.setGravity(false);
        display.setBillboard(resolveDisplayBillboard());
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(1);
        display.setTeleportDuration(1);
        display.setDisplayWidth(getDisplayWidth() * scale);
        display.setDisplayHeight(getDisplayHeight() * scale);
        int blockBrightness = getDisplayBrightnessBlock();
        int skyBrightness = getDisplayBrightnessSky();
        if (blockBrightness >= 0 || skyBrightness >= 0) {
            display.setBrightness(new Display.Brightness(Math.max(0, blockBrightness), Math.max(0, skyBrightness)));
        }
        display.setShadowRadius(getDisplayShadowRadius());
        display.setShadowStrength(getDisplayShadowStrength());
        display.setRotation(this.definition.getLocation().getYaw(), this.definition.getLocation().getPitch());
        applyScale(display, scale);
        display.getPersistentDataContainer().set(this.idKey, PersistentDataType.STRING, this.definition.getId());
        display.getPersistentDataContainer().set(this.kindKey, PersistentDataType.STRING, kind);
    }

    private void applyScale(Display display, float scale) {
        display.setDisplayWidth(getDisplayWidth() * scale);
        display.setDisplayHeight(getDisplayHeight() * scale);
        display.setTransformation(new Transformation(
                new Vector3f(0F, 0F, 0F),
                new AxisAngle4f(),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f()
        ));
    }

    private boolean isHologramArmorStandSmall() {
        Boolean override = this.definition.getHologramArmorStandSmallOverride();
        if (override != null) {
            return override.booleanValue();
        }
        return this.plugin.getConfig().getBoolean("hologram.armor-stand-small", false);
    }

    private boolean isHologramArmorStandMarker() {
        Boolean override = this.definition.getHologramArmorStandMarkerOverride();
        if (override != null) {
            return override.booleanValue();
        }
        return this.plugin.getConfig().getBoolean("hologram.armor-stand-marker", true);
    }

    private double getHologramLineSpacing() {
        Double override = this.definition.getHologramLineSpacingOverride();
        if (override != null) {
            return override.doubleValue();
        }
        return this.plugin.getConfig().getDouble("hologram.line-spacing", 0.27D);
    }

    private Display.Billboard resolveDisplayBillboard() {
        String value = this.definition.getDisplayBillboardOverride();
        if (value == null || value.trim().isEmpty()) {
            value = this.plugin.getConfig().getString("display.billboard", "FIXED");
        }
        try {
            return Display.Billboard.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return Display.Billboard.FIXED;
        }
    }

    private int getDisplayBrightnessBlock() {
        Integer override = this.definition.getDisplayBrightnessBlockOverride();
        if (override != null) {
            return override.intValue();
        }
        return this.plugin.getConfig().getInt("display.brightness-block", -1);
    }

    private int getDisplayBrightnessSky() {
        Integer override = this.definition.getDisplayBrightnessSkyOverride();
        if (override != null) {
            return override.intValue();
        }
        return this.plugin.getConfig().getInt("display.brightness-sky", -1);
    }

    private float getDisplayShadowRadius() {
        Float override = this.definition.getDisplayShadowRadiusOverride();
        if (override != null) {
            return override.floatValue();
        }
        return (float) this.plugin.getConfig().getDouble("display.shadow-radius", 0.0D);
    }

    private float getDisplayShadowStrength() {
        Float override = this.definition.getDisplayShadowStrengthOverride();
        if (override != null) {
            return override.floatValue();
        }
        return (float) this.plugin.getConfig().getDouble("display.shadow-strength", 0.0D);
    }

    private float getDisplayWidth() {
        Float override = this.definition.getDisplayWidthOverride();
        if (override != null) {
            return override.floatValue();
        }
        return (float) this.plugin.getConfig().getDouble("display.display-width", 0.7D);
    }

    private float getDisplayHeight() {
        Float override = this.definition.getDisplayHeightOverride();
        if (override != null) {
            return override.floatValue();
        }
        return (float) this.plugin.getConfig().getDouble("display.display-height", 0.7D);
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

    private void cleanupUnexpectedEntities() {
        Location base = this.definition.getLocation();
        if (base == null || base.getWorld() == null) {
            return;
        }
        Set<UUID> keep = new HashSet<UUID>();
        if (this.mainDisplay != null && this.mainDisplay.isValid()) {
            keep.add(this.mainDisplay.getUniqueId());
        }
        if (this.shadowDisplay != null && this.shadowDisplay.isValid()) {
            keep.add(this.shadowDisplay.getUniqueId());
        }
        collectArmorStandIds(this.hologramMain, keep);
        collectArmorStandIds(this.hologramShadow, keep);
        for (List<ArmorStand> stands : this.viewerHolograms.values()) {
            collectArmorStandIds(stands, keep);
        }
        for (TextDisplay textDisplay : this.viewerTextDisplays.values()) {
            if (textDisplay != null && textDisplay.isValid()) {
                keep.add(textDisplay.getUniqueId());
            }
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
