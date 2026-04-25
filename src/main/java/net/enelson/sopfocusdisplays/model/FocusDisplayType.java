package net.enelson.sopfocusdisplays.model;

public enum FocusDisplayType {
    ITEM,
    TEXT;

    public static FocusDisplayType fromString(String input) {
        if (input == null) {
            return ITEM;
        }
        for (FocusDisplayType type : values()) {
            if (type.name().equalsIgnoreCase(input)) {
                return type;
            }
        }
        return ITEM;
    }
}