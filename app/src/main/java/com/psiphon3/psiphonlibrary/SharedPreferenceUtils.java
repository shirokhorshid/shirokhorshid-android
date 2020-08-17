/*
 * Copyright (c) 2020, Psiphon Inc.
 * All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.psiphon3.psiphonlibrary;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
            return new LinkedHashSet<>();
        }

        // otherwise split it up
        String[] split = serializedSet.split(DELIMITER);
        List<String> splitList = Arrays.asList(split);
        return new LinkedHashSet<>(splitList);
    }
}
