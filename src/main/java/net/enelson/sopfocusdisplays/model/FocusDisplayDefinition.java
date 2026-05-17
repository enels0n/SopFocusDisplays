package net.enelson.sopdisplays.model;

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
    private String hologramRenderer;
    private Boolean hologramArmorStandSmallOverride;
    private Boolean hologramArmorStandMarkerOverride;
    private Double hologramLineSpacingOverride;
    private Boolean backgroundEnabledOverride;
    private String backgroundColorOverride;
    private Boolean textShadowedOverride;
    private Integer textLineWidthOverride;
    private Boolean textSeeThroughOverride;
    private String textAlignmentOverride;
    private String displayBillboardOverride;
    private Integer displayBrightnessBlockOverride;
    private Integer displayBrightnessSkyOverride;
    private Float displayShadowRadiusOverride;
    private Float displayShadowStrengthOverride;
    private Float displayWidthOverride;
    private Float displayHeightOverride;
    private String itemTransformOverride;

    public FocusDisplayDefinition(String id, FocusDisplayType type, Location location, ItemStack itemStack, String text, float baseScale, float focusScale, DisplayConditions conditions) {
        this(id, type, location, itemStack, text, baseScale, focusScale, conditions,
                null, null, null, null,
                null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
    }

    public FocusDisplayDefinition(
            String id,
            FocusDisplayType type,
            Location location,
            ItemStack itemStack,
            String text,
            float baseScale,
            float focusScale,
            DisplayConditions conditions,
            String hologramRenderer,
            Boolean hologramArmorStandSmallOverride,
            Boolean hologramArmorStandMarkerOverride,
            Double hologramLineSpacingOverride,
            Boolean backgroundEnabledOverride,
            String backgroundColorOverride,
            Boolean textShadowedOverride,
            Integer textLineWidthOverride,
            Boolean textSeeThroughOverride,
            String textAlignmentOverride,
            String displayBillboardOverride,
            Integer displayBrightnessBlockOverride,
            Integer displayBrightnessSkyOverride,
            Float displayShadowRadiusOverride,
            Float displayShadowStrengthOverride,
            Float displayWidthOverride,
            Float displayHeightOverride,
            String itemTransformOverride
    ) {
        this.id = id;
        this.type = type;
        this.location = location;
        this.itemStack = itemStack;
        this.text = text;
        this.baseScale = baseScale;
        this.focusScale = focusScale;
        this.conditions = conditions == null ? DisplayConditions.alwaysVisible() : conditions;
        this.hologramRenderer = hologramRenderer;
        this.hologramArmorStandSmallOverride = hologramArmorStandSmallOverride;
        this.hologramArmorStandMarkerOverride = hologramArmorStandMarkerOverride;
        this.hologramLineSpacingOverride = hologramLineSpacingOverride;
        this.backgroundEnabledOverride = backgroundEnabledOverride;
        this.backgroundColorOverride = backgroundColorOverride;
        this.textShadowedOverride = textShadowedOverride;
        this.textLineWidthOverride = textLineWidthOverride;
        this.textSeeThroughOverride = textSeeThroughOverride;
        this.textAlignmentOverride = textAlignmentOverride;
        this.displayBillboardOverride = displayBillboardOverride;
        this.displayBrightnessBlockOverride = displayBrightnessBlockOverride;
        this.displayBrightnessSkyOverride = displayBrightnessSkyOverride;
        this.displayShadowRadiusOverride = displayShadowRadiusOverride;
        this.displayShadowStrengthOverride = displayShadowStrengthOverride;
        this.displayWidthOverride = displayWidthOverride;
        this.displayHeightOverride = displayHeightOverride;
        this.itemTransformOverride = itemTransformOverride;
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

    public String getHologramRenderer() {
        return this.hologramRenderer;
    }

    public Boolean getHologramArmorStandSmallOverride() {
        return this.hologramArmorStandSmallOverride;
    }

    public Boolean getHologramArmorStandMarkerOverride() {
        return this.hologramArmorStandMarkerOverride;
    }

    public Double getHologramLineSpacingOverride() {
        return this.hologramLineSpacingOverride;
    }

    public Boolean getBackgroundEnabledOverride() {
        return this.backgroundEnabledOverride;
    }

    public String getBackgroundColorOverride() {
        return this.backgroundColorOverride;
    }

    public Boolean getTextShadowedOverride() {
        return this.textShadowedOverride;
    }

    public Integer getTextLineWidthOverride() {
        return this.textLineWidthOverride;
    }

    public Boolean getTextSeeThroughOverride() {
        return this.textSeeThroughOverride;
    }

    public String getTextAlignmentOverride() {
        return this.textAlignmentOverride;
    }

    public String getDisplayBillboardOverride() {
        return this.displayBillboardOverride;
    }

    public Integer getDisplayBrightnessBlockOverride() {
        return this.displayBrightnessBlockOverride;
    }

    public Integer getDisplayBrightnessSkyOverride() {
        return this.displayBrightnessSkyOverride;
    }

    public Float getDisplayShadowRadiusOverride() {
        return this.displayShadowRadiusOverride;
    }

    public Float getDisplayShadowStrengthOverride() {
        return this.displayShadowStrengthOverride;
    }

    public Float getDisplayWidthOverride() {
        return this.displayWidthOverride;
    }

    public Float getDisplayHeightOverride() {
        return this.displayHeightOverride;
    }

    public String getItemTransformOverride() {
        return this.itemTransformOverride;
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
        if (this.type == FocusDisplayType.HOLOGRAM && this.hologramRenderer != null && !this.hologramRenderer.trim().isEmpty()) {
            section.set("hologram-renderer", this.hologramRenderer);
        }
        if (this.type == FocusDisplayType.HOLOGRAM) {
            section.set("hologram-armor-stand-small", this.hologramArmorStandSmallOverride);
            section.set("hologram-armor-stand-marker", this.hologramArmorStandMarkerOverride);
            section.set("hologram-line-spacing", this.hologramLineSpacingOverride);
        }
        if (this.type != FocusDisplayType.ITEM) {
            section.set("text-background-enabled", this.backgroundEnabledOverride);
            section.set("text-background-color", this.backgroundColorOverride);
            section.set("text-shadowed", this.textShadowedOverride);
            section.set("text-line-width", this.textLineWidthOverride);
            section.set("text-see-through", this.textSeeThroughOverride);
            section.set("text-alignment", this.textAlignmentOverride);
        }
        section.set("display-billboard", this.displayBillboardOverride);
        section.set("display-brightness-block", this.displayBrightnessBlockOverride);
        section.set("display-brightness-sky", this.displayBrightnessSkyOverride);
        section.set("display-shadow-radius", this.displayShadowRadiusOverride);
        section.set("display-shadow-strength", this.displayShadowStrengthOverride);
        section.set("display-width", this.displayWidthOverride);
        section.set("display-height", this.displayHeightOverride);
        if (this.type == FocusDisplayType.ITEM) {
            section.set("item-transform", this.itemTransformOverride);
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
        String hologramRenderer = section.getString("hologram-renderer", null);
        Boolean hologramArmorStandSmallOverride = section.contains("hologram-armor-stand-small")
                ? Boolean.valueOf(section.getBoolean("hologram-armor-stand-small"))
                : null;
        Boolean hologramArmorStandMarkerOverride = section.contains("hologram-armor-stand-marker")
                ? Boolean.valueOf(section.getBoolean("hologram-armor-stand-marker"))
                : null;
        Double hologramLineSpacingOverride = section.contains("hologram-line-spacing")
                ? Double.valueOf(section.getDouble("hologram-line-spacing"))
                : null;
        Boolean backgroundEnabledOverride = section.contains("text-background-enabled")
                ? Boolean.valueOf(section.getBoolean("text-background-enabled"))
                : null;
        String backgroundColorOverride = section.getString("text-background-color", null);
        Boolean textShadowedOverride = section.contains("text-shadowed")
                ? Boolean.valueOf(section.getBoolean("text-shadowed"))
                : null;
        Integer textLineWidthOverride = section.contains("text-line-width")
                ? Integer.valueOf(section.getInt("text-line-width"))
                : null;
        Boolean textSeeThroughOverride = section.contains("text-see-through")
                ? Boolean.valueOf(section.getBoolean("text-see-through"))
                : null;
        String textAlignmentOverride = section.getString("text-alignment", null);
        String displayBillboardOverride = section.getString("display-billboard", null);
        Integer displayBrightnessBlockOverride = section.contains("display-brightness-block")
                ? Integer.valueOf(section.getInt("display-brightness-block"))
                : null;
        Integer displayBrightnessSkyOverride = section.contains("display-brightness-sky")
                ? Integer.valueOf(section.getInt("display-brightness-sky"))
                : null;
        Float displayShadowRadiusOverride = section.contains("display-shadow-radius")
                ? Float.valueOf((float) section.getDouble("display-shadow-radius"))
                : null;
        Float displayShadowStrengthOverride = section.contains("display-shadow-strength")
                ? Float.valueOf((float) section.getDouble("display-shadow-strength"))
                : null;
        Float displayWidthOverride = section.contains("display-width")
                ? Float.valueOf((float) section.getDouble("display-width"))
                : null;
        Float displayHeightOverride = section.contains("display-height")
                ? Float.valueOf((float) section.getDouble("display-height"))
                : null;
        String itemTransformOverride = section.getString("item-transform", null);
        DisplayConditions conditions = DisplayConditions.fromSection(section.getConfigurationSection("conditions"));
        return new FocusDisplayDefinition(
                id,
                type,
                location,
                itemStack,
                text,
                baseScale,
                focusScale,
                conditions,
                hologramRenderer,
                hologramArmorStandSmallOverride,
                hologramArmorStandMarkerOverride,
                hologramLineSpacingOverride,
                backgroundEnabledOverride,
                backgroundColorOverride,
                textShadowedOverride,
                textLineWidthOverride,
                textSeeThroughOverride,
                textAlignmentOverride,
                displayBillboardOverride,
                displayBrightnessBlockOverride,
                displayBrightnessSkyOverride,
                displayShadowRadiusOverride,
                displayShadowStrengthOverride,
                displayWidthOverride,
                displayHeightOverride,
                itemTransformOverride
        );
    }
}
