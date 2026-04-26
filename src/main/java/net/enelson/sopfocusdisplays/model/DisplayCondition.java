package net.enelson.sopfocusdisplays.model;

import net.enelson.sopfocusdisplays.SopFocusDisplays;
import org.bukkit.entity.Player;

public final class DisplayCondition {

    private final DisplayConditionType type;
    private final String typeId;
    private final String input;
    private final String output;

    public DisplayCondition(DisplayConditionType type, String typeId, String input, String output) {
        this.type = type;
        this.typeId = typeId == null ? "" : typeId;
        this.input = input == null ? "" : input;
        this.output = output == null ? "" : output;
    }

    public DisplayConditionType getType() {
        return this.type;
    }

    public String getTypeId() {
        return this.typeId;
    }

    public String getInput() {
        return this.input;
    }

    public String getOutput() {
        return this.output;
    }

    public boolean test(SopFocusDisplays plugin, Player player) {
        if (this.type == null) {
            return true;
        }

        String resolvedInput = plugin.resolvePlaceholders(player, this.input).trim();
        String resolvedOutput = plugin.resolvePlaceholders(player, this.output).trim();
        if (isUnavailableNumericPlaceholder(this.input, resolvedInput) || isUnavailableNumericPlaceholder(this.output, resolvedOutput)) {
            return false;
        }
        boolean result;
        switch (this.type) {
            case HAS_PERM:
                result = player != null && player.hasPermission(this.input);
                break;
            case HAS_NO_PERM:
                result = player == null || !player.hasPermission(this.input);
                break;
            case STRING_EQUALS:
                result = resolvedInput.equalsIgnoreCase(resolvedOutput);
                break;
            case STRING_NOT_EQUALS:
                result = !resolvedInput.equalsIgnoreCase(resolvedOutput);
                break;
            case NUMBER_GREATER_OR_EQUALS:
                result = compareNumbers(resolvedInput, resolvedOutput, DisplayConditionType.NUMBER_GREATER_OR_EQUALS);
                break;
            case NUMBER_GREATER:
                result = compareNumbers(resolvedInput, resolvedOutput, DisplayConditionType.NUMBER_GREATER);
                break;
            case NUMBER_LESS_OR_EQUALS:
                result = compareNumbers(resolvedInput, resolvedOutput, DisplayConditionType.NUMBER_LESS_OR_EQUALS);
                break;
            case NUMBER_LESS:
                result = compareNumbers(resolvedInput, resolvedOutput, DisplayConditionType.NUMBER_LESS);
                break;
            case NUMBER_EQUALS:
                result = compareNumbers(resolvedInput, resolvedOutput, DisplayConditionType.NUMBER_EQUALS);
                break;
            case NUMBER_NOT_EQUALS:
                result = compareNumbers(resolvedInput, resolvedOutput, DisplayConditionType.NUMBER_NOT_EQUALS);
                break;
            default:
                result = true;
                break;
        }

        if (player != null && plugin.getConfig().getBoolean("debug.conditions", false)) {
            plugin.getLogger().info("[debug] condition player=" + player.getName()
                    + " type=" + this.typeId
                    + " input=" + resolvedInput
                    + " output=" + resolvedOutput
                    + " result=" + result);
        }
        return result;
    }

    private boolean isUnavailableNumericPlaceholder(String original, String resolved) {
        if (original == null || resolved == null) {
            return false;
        }
        if (!original.contains("%")) {
            return false;
        }
        if (resolved.contains("%")) {
            return true;
        }
        return "0".equals(resolved.trim());
    }

    private boolean compareNumbers(String input, String output, DisplayConditionType comparisonType) {
        try {
            double left = Double.parseDouble(input);
            double right = Double.parseDouble(output);
            int result = Double.compare(left, right);
            switch (comparisonType) {
                case NUMBER_GREATER_OR_EQUALS:
                    return result >= 0;
                case NUMBER_GREATER:
                    return result > 0;
                case NUMBER_LESS_OR_EQUALS:
                    return result <= 0;
                case NUMBER_LESS:
                    return result < 0;
                case NUMBER_EQUALS:
                    return result == 0;
                case NUMBER_NOT_EQUALS:
                    return result != 0;
                default:
                    return false;
            }
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
