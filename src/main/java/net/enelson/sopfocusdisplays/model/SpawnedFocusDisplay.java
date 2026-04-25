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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SpawnedFocusDisplay {

    private static final String KIND_MAIN = "main";
    private static final String KIND_SHADOW = "shadow";
    private static final String KIND_FALLBACK = "fallback";
    private static final String KIND_FALLBACK_SHADOW = "fallback-shadow";

    private final SopFocusDisplays plugin;
    private final FocusDisplayDefinition definition;
    private final NamespacedKey idKey;
    private final NamespacedKey kindKey;
    private final Map<UUID, Float> viewerScales = new HashMap<UUID, Float>();
    private final Map<UUID, Boolean> modernViewerState = new HashMap<UUID, Boolean>();
    private final Map<UUID, String> viewerTexts = new HashMap<UUID, String>();

    private Display mainDisplay;
    private Display shadowDisplay;
    private ArmorStand fallbackStand;
    private ArmorStand shadowFallbackStand;

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
        } else {
            this.mainDisplay = location.getWorld().spawn(location, ItemDisplay.class);
            prepareItemDisplay((ItemDisplay) this.mainDisplay, this.definition.getItemStack(), this.definition.getBaseScale(), KIND_MAIN);

            this.shadowDisplay = location.getWorld().spawn(location, ItemDisplay.class);
            prepareItemDisplay((ItemDisplay) this.shadowDisplay, this.definition.getItemStack(), this.definition.getBaseScale(), KIND_SHADOW);
        }

        this.fallbackStand = location.getWorld().spawn(location, ArmorStand.class);
        prepareFallback(this.fallbackStand, KIND_FALLBACK, "");

        this.shadowFallbackStand = location.getWorld().spawn(location, ArmorStand.class);
        prepareFallback(this.shadowFallbackStand, KIND_FALLBACK_SHADOW, "");

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.hideEntity(this.plugin, this.shadowDisplay);
            player.hideEntity(this.plugin, this.shadowFallbackStand);
        }
    }

    public void remove() {
        if (this.mainDisplay != null && this.mainDisplay.isValid()) {
            this.mainDisplay.remove();
        }
        if (this.shadowDisplay != null && this.shadowDisplay.isValid()) {
            this.shadowDisplay.remove();
        }
        if (this.fallbackStand != null && this.fallbackStand.isValid()) {
            this.fallbackStand.remove();
        }
        if (this.shadowFallbackStand != null && this.shadowFallbackStand.isValid()) {
            this.shadowFallbackStand.remove();
        }
        this.viewerScales.clear();
        this.modernViewerState.clear();
        this.viewerTexts.clear();
    }

    public void updateItem(ItemStack itemStack) {
        this.definition.setItemStack(itemStack);
        if (this.definition.getType() != FocusDisplayType.ITEM) {
            return;
        }
        ((ItemDisplay) this.mainDisplay).setItemStack(itemStack == null ? null : itemStack.clone());
        ((ItemDisplay) this.shadowDisplay).setItemStack(itemStack == null ? null : itemStack.clone());
        this.fallbackStand.getEquipment().setHelmet(itemStack == null ? null : itemStack.clone());
        this.shadowFallbackStand.getEquipment().setHelmet(itemStack == null ? null : itemStack.clone());
    }

    public void updateText(String text) {
        this.definition.setText(text);
        this.viewerTexts.clear();
        if (this.definition.getType() != FocusDisplayType.TEXT) {
            return;
        }
        ((TextDisplay) this.mainDisplay).text(this.plugin.miniMessage(""));
        ((TextDisplay) this.shadowDisplay).text(this.plugin.miniMessage(""));
        this.fallbackStand.customName(this.plugin.legacy(""));
        this.shadowFallbackStand.customName(this.plugin.legacy(""));
    }

    public void move(Location location) {
        this.definition.setLocation(location);
        this.mainDisplay.teleport(location);
        this.shadowDisplay.teleport(location);
        this.fallbackStand.teleport(location);
        this.shadowFallbackStand.teleport(location);
    }

    public FocusDisplayDefinition getDefinition() {
        return this.definition;
    }

    public Display getMainDisplay() {
        return this.mainDisplay;
    }

    public boolean isSameWorld(Player player) {
        return this.mainDisplay != null && this.mainDisplay.getWorld().equals(player.getWorld());
    }

    public void ensureViewerMode(Player player, boolean modernClient) {
        Boolean currentState = this.modernViewerState.get(player.getUniqueId());
        if (currentState != null && currentState.booleanValue() == modernClient) {
            return;
        }

        this.modernViewerState.put(player.getUniqueId(), modernClient);
        this.viewerTexts.remove(player.getUniqueId());
        if (modernClient) {
            player.showEntity(this.plugin, this.mainDisplay);
            player.hideEntity(this.plugin, this.fallbackStand);
            player.hideEntity(this.plugin, this.shadowDisplay);
            player.hideEntity(this.plugin, this.shadowFallbackStand);
        } else {
            player.hideEntity(this.plugin, this.mainDisplay);
            player.hideEntity(this.plugin, this.shadowDisplay);
            player.hideEntity(this.plugin, this.shadowFallbackStand);
            player.showEntity(this.plugin, this.fallbackStand);
        }
    }

    public void forgetViewer(UUID uniqueId) {
        this.viewerScales.remove(uniqueId);
        this.modernViewerState.remove(uniqueId);
        this.viewerTexts.remove(uniqueId);
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
        applyScale(this.shadowDisplay, scale);
        ProtocolManager protocolManager = this.plugin.getProtocolManager();
        MetadataPackets.sendEntityMetadata(protocolManager, player, this.mainDisplay.getEntityId(), this.shadowDisplay);
    }

    public void syncViewerText(Player player, boolean modernClient) {
        if (this.definition.getType() != FocusDisplayType.TEXT) {
            return;
        }

        String resolved = this.plugin.applyPlaceholders(player, this.definition.getText());
        String current = this.viewerTexts.get(player.getUniqueId());
        if (resolved.equals(current)) {
            return;
        }

        ProtocolManager protocolManager = this.plugin.getProtocolManager();
        if (modernClient) {
            ((TextDisplay) this.shadowDisplay).text(this.plugin.miniMessage(resolved));
            MetadataPackets.sendEntityMetadata(protocolManager, player, this.mainDisplay.getEntityId(), this.shadowDisplay);
        } else {
            this.shadowFallbackStand.customName(this.plugin.legacy(resolved));
            MetadataPackets.sendEntityMetadata(protocolManager, player, this.fallbackStand.getEntityId(), this.shadowFallbackStand);
        }

        this.viewerTexts.put(player.getUniqueId(), resolved);
    }

    public boolean isLookingAt(Player player, double maxDistance, double focusCosine) {
        if (!player.getWorld().equals(this.mainDisplay.getWorld())) {
            return false;
        }
        if (player.getLocation().distanceSquared(this.mainDisplay.getLocation()) > maxDistance * maxDistance) {
            return false;
        }
        if (!player.hasLineOfSight(this.mainDisplay)) {
            return false;
        }

        Location eye = player.getEyeLocation();
        Vector3f direction = new Vector3f((float) eye.getDirection().getX(), (float) eye.getDirection().getY(), (float) eye.getDirection().getZ());
        Location anchor = this.mainDisplay.getLocation().clone().add(0.0D, 0.1D, 0.0D);
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

    private void prepareFallback(ArmorStand armorStand, String kind, String text) {
        armorStand.setPersistent(false);
        armorStand.setInvulnerable(true);
        armorStand.setGravity(false);
        armorStand.setVisible(false);
        armorStand.setBasePlate(false);
        armorStand.setArms(false);
        armorStand.setMarker(this.plugin.getConfig().getBoolean("fallback.armor-stand-marker", true));
        armorStand.setSmall(this.plugin.getConfig().getBoolean("fallback.armor-stand-small", false));
        armorStand.setRotation(this.definition.getLocation().getYaw(), this.definition.getLocation().getPitch());
        armorStand.getPersistentDataContainer().set(this.idKey, PersistentDataType.STRING, this.definition.getId());
        armorStand.getPersistentDataContainer().set(this.kindKey, PersistentDataType.STRING, kind);

        if (this.definition.getType() == FocusDisplayType.TEXT) {
            armorStand.setCustomNameVisible(true);
            armorStand.customName(this.plugin.legacy(text));
        } else {
            armorStand.getEquipment().setHelmet(this.definition.getItemStack() == null ? null : this.definition.getItemStack().clone());
        }
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