package net.enelson.sopfocusdisplays.model;

import java.util.Arrays;
import java.util.List;

public enum DisplayConditionType {

    HAS_PERM(Arrays.asList("has perm", "has permission", "hasperm", "haspermission")),
    HAS_NO_PERM(Arrays.asList("!has perm", "!has permission", "!hasperm", "!haspermission")),
    STRING_EQUALS(Arrays.asList("string equals", "stringequals")),
    STRING_NOT_EQUALS(Arrays.asList("!string equals", "!stringequals")),
    NUMBER_GREATER_OR_EQUALS(Arrays.asList("number >=", "number ge", "number gte", ">=", "ge", "gte")),
    NUMBER_GREATER(Arrays.asList("number >", "number gt", ">", "gt")),
    NUMBER_LESS_OR_EQUALS(Arrays.asList("number <=", "number le", "number lte", "<=", "le", "lte")),
    NUMBER_LESS(Arrays.asList("number <", "number lt", "<", "lt")),
    NUMBER_EQUALS(Arrays.asList("number equals", "numberequals", "number ==", "number =")),
    NUMBER_NOT_EQUALS(Arrays.asList("!number equals", "!numberequals", "number !=", "!="));

    private final List<String> identifiers;

    DisplayConditionType(List<String> identifiers) {
        this.identifiers = identifiers;
    }

    public static DisplayConditionType fromString(String input) {
        if (input == null) {
            return null;
        }

        for (DisplayConditionType type : values()) {
            for (String identifier : type.identifiers) {
                if (identifier.equalsIgnoreCase(input)) {
                    return type;
                }
            }
        }
        return null;
    }
}
