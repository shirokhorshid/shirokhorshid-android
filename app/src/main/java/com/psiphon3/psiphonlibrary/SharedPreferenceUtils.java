package com.psiphon3.psiphonlibrary;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SharedPreferenceUtils {
    private static final String DELIMITER = ",";

    public static String serializeSet(Set set) {
        StringBuilder stringBuilder = new StringBuilder();
        // iter through set appending each element and then the ,
        for (Object element : set) {
            stringBuilder.append(element.toString());
            stringBuilder.append(DELIMITER);
        }

        // if we actually have elements remove trailing ,
        if (stringBuilder.length() > 0) {
            stringBuilder.setLength(stringBuilder.length() - 1);
        }

        return stringBuilder.toString();
    }

    public static Set<String> deserializeSet(String serializedSet) {
        // empty so return empty set
        if ("".equals(serializedSet)) {
            return new HashSet<>();
        }

        // otherwise split it up
        String[] split = serializedSet.split(DELIMITER);
        List<String> splitList = Arrays.asList(split);
        return new HashSet<>(splitList);
    }
}
