package net.enelson.sopfocusdisplays.model;

import net.enelson.sopfocusdisplays.SopFocusDisplays;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class DisplayConditions {

    private final boolean any;
    private final List<DisplayCondition> checks;

    public DisplayConditions(boolean any, List<DisplayCondition> checks) {
        this.any = any;
        this.checks = checks == null ? Collections.<DisplayCondition>emptyList() : new ArrayList<DisplayCondition>(checks);
    }

    public static DisplayConditions alwaysVisible() {
        return new DisplayConditions(false, Collections.<DisplayCondition>emptyList());
    }

    public boolean isEmpty() {
        return this.checks.isEmpty();
    }

    public boolean test(SopFocusDisplays plugin, Player player) {
        if (this.checks.isEmpty()) {
            return true;
        }

        if (this.any) {
            for (DisplayCondition check : this.checks) {
                if (check.test(plugin, player)) {
                    return true;
                }
            }
            return false;
        }

        for (DisplayCondition check : this.checks) {
            if (!check.test(plugin, player)) {
                return false;
            }
        }
        return true;
    }

    public void save(ConfigurationSection section) {
        if (section == null || this.checks.isEmpty()) {
            return;
        }

        section.set("type", this.any ? "any" : "all");
        List<Map<String, Object>> serialized = new ArrayList<Map<String, Object>>();
        for (DisplayCondition check : this.checks) {
            java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<String, Object>();
            map.put("type", check.getTypeId().isEmpty() ? (check.getType() == null ? "" : check.getType().name().toLowerCase().replace('_', ' ')) : check.getTypeId());
            if (!check.getInput().isEmpty()) {
                map.put("input", check.getInput());
            }
            if (!check.getOutput().isEmpty()) {
                map.put("output", check.getOutput());
            }
            serialized.add(map);
        }
        section.set("checks", serialized);
    }

    public static DisplayConditions fromSection(ConfigurationSection section) {
        if (section == null) {
            return alwaysVisible();
        }

        boolean any = "any".equalsIgnoreCase(section.getString("type", "all"));
        List<Map<?, ?>> rawChecks = section.getMapList("checks");
        List<DisplayCondition> checks = new ArrayList<DisplayCondition>();
        for (Map<?, ?> raw : rawChecks) {
            if (raw == null) {
                continue;
            }
            Object typeValue = raw.get("type");
            DisplayConditionType type = DisplayConditionType.fromString(typeValue == null ? null : String.valueOf(typeValue));
            if (type == null) {
                continue;
            }
            Object inputValue = raw.get("input");
            Object outputValue = raw.get("output");
            checks.add(new DisplayCondition(
                    type,
                    typeValue == null ? "" : String.valueOf(typeValue),
                    inputValue == null ? "" : String.valueOf(inputValue),
                    outputValue == null ? "" : String.valueOf(outputValue)
            ));
        }

        if (checks.isEmpty()) {
            return alwaysVisible();
        }
        return new DisplayConditions(any, checks);
    }
}
