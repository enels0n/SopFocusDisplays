package net.enelson.sopfocusdisplays.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

public final class FocusDisplayDefinition {

    private final String id;
    private final FocusDisplayType type;
    private Location location;
    private ItemStack itemStack;
    private String text;
    private float baseScale;
    private float focusScale;
    private DisplayConditions conditions;

    public FocusDisplayDefinition(String id, FocusDisplayType type, Location location, ItemStack itemStack, String text, float baseScale, float focusScale, DisplayConditions conditions) {
        this.id = id;
        this.type = type;
        this.location = location;
        this.itemStack = itemStack;
        this.text = text;
        this.baseScale = baseScale;
        this.focusScale = focusScale;
        this.conditions = conditions == null ? DisplayConditions.alwaysVisible() : conditions;
    }

    public String getId() {
        return this.id;
    }

    public FocusDisplayType getType() {
        return this.type;
    }

    public Location getLocation() {
        return this.location.clone();
    }

    public void setLocation(Location location) {
        this.location = location.clone();
    }

    public ItemStack getItemStack() {
        return this.itemStack == null ? null : this.itemStack.clone();
    }

    public void setItemStack(ItemStack itemStack) {
        this.itemStack = itemStack == null ? null : itemStack.clone();
    }

    public String getText() {
        return this.text == null ? "" : this.text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public float getBaseScale() {
        return this.baseScale;
    }

    public float getFocusScale() {
        return this.focusScale;
    }

    public DisplayConditions getConditions() {
        return this.conditions;
    }

    public void save(ConfigurationSection section) {
        section.set("type", this.type.name());
        section.set("world", this.location.getWorld().getName());
        section.set("x", this.location.getX());
        section.set("y", this.location.getY());
        section.set("z", this.location.getZ());
        section.set("yaw", this.location.getYaw());
        section.set("pitch", this.location.getPitch());
        section.set("item", this.itemStack == null ? null : this.itemStack.clone());
        section.set("text", this.text);
        if (this.type != FocusDisplayType.HOLOGRAM) {
            section.set("base-scale", this.baseScale);
            section.set("focus-scale", this.focusScale);
        }
        if (this.conditions != null && !this.conditions.isEmpty()) {
            this.conditions.save(section.createSection("conditions"));
        }
    }

    public static FocusDisplayDefinition fromSection(String id, ConfigurationSection section, float defaultBaseScale, float defaultFocusScale, String defaultText) {
        if (section == null) {
            return null;
        }

        String worldName = section.getString("world");
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }

        Location location = new Location(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch")
        );

        FocusDisplayType type = FocusDisplayType.fromString(section.getString("type", "ITEM"));
        ItemStack itemStack = section.getItemStack("item");
        String text = section.getString("text", defaultText);
        float baseScale = (float) section.getDouble("base-scale", defaultBaseScale);
        float focusScale = (float) section.getDouble("focus-scale", defaultFocusScale);
        DisplayConditions conditions = DisplayConditions.fromSection(section.getConfigurationSection("conditions"));
        return new FocusDisplayDefinition(id, type, location, itemStack, text, baseScale, focusScale, conditions);
    }
}
