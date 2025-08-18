/*
 * Copyright (c) 2025, Psiphon Inc.
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

package com.psiphon3;

import android.content.Context;

import androidx.annotation.NonNull;

import com.psiphon3.log.MyLog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

abstract class VersionRule {
    public abstract boolean matches(int versionCode);

    public static List<VersionRule> parseVersionRules(List<String> ruleStrings) {
        List<VersionRule> rules = new ArrayList<>();
        for (String ruleString : ruleStrings) {
            VersionRule rule = parseVersionRule(ruleString);
            if (rule != null) {
                rules.add(rule);
            }
        }
        return rules;
    }

    private static VersionRule parseVersionRule(String ruleString) {
        if (ruleString == null || ruleString.trim().isEmpty()) {
            return null;
        }

        String rule = ruleString.trim();

        // Match wildcard: "*"
        if ("*".equals(rule)) {
            return new AnyVersionRule();
        }

        // Match range: "[100-200]"
        Pattern rangePattern = Pattern.compile("\\[(\\d+)-(\\d+)\\]");
        Matcher rangeMatcher = rangePattern.matcher(rule);
        if (rangeMatcher.matches()) {
            try {
                int min = Integer.parseInt(rangeMatcher.group(1));
                int max = Integer.parseInt(rangeMatcher.group(2));
                if (min <= max) {
                    return new RangeRule(min, max);
                }
            } catch (NumberFormatException e) {
                // Invalid number format, skip this rule
            }
            return null;
        }

        // Match comparison operators: ">=100", ">100", "<=100", "<100"
        Pattern comparisonPattern = Pattern.compile("(>=|>|<=|<)(\\d+)");
        Matcher comparisonMatcher = comparisonPattern.matcher(rule);
        if (comparisonMatcher.matches()) {
            try {
                String operator = comparisonMatcher.group(1);
                int value = Integer.parseInt(comparisonMatcher.group(2));

                switch (operator) {
                    case ">=":
                        return new ComparisonRule(ComparisonRule.Operator.GTE, value);
                    case ">":
                        return new ComparisonRule(ComparisonRule.Operator.GT, value);
                    case "<=":
                        return new ComparisonRule(ComparisonRule.Operator.LTE, value);
                    case "<":
                        return new ComparisonRule(ComparisonRule.Operator.LT, value);
                }
            } catch (NumberFormatException e) {
                // Invalid number format, skip this rule
            }
        }

        // Match exact version: "150"
        Pattern exactPattern = Pattern.compile("^(\\d+)$");
        Matcher exactMatcher = exactPattern.matcher(rule);
        if (exactMatcher.matches()) {
            try {
                int exactVersion = Integer.parseInt(exactMatcher.group(1));
                return new ExactVersionRule(exactVersion);
            } catch (NumberFormatException e) {
                // Invalid number format, skip this rule
            }
        }

        return null;
    }
}

class AnyVersionRule extends VersionRule {
    @Override
    public boolean matches(int versionCode) {
        return true;
    }
}

class ComparisonRule extends VersionRule {
    enum Operator {GT, GTE, LT, LTE}

    private final Operator operator;
    private final int value;

    public ComparisonRule(Operator operator, int value) {
        this.operator = operator;
        this.value = value;
    }

    @Override
    public boolean matches(int versionCode) {
        switch (operator) {
            case GT:
                return versionCode > value;
            case GTE:
                return versionCode >= value;
            case LT:
                return versionCode < value;
            case LTE:
                return versionCode <= value;
            default:
                return false;
        }
    }
}

class RangeRule extends VersionRule {
    private final int minVersion;
    private final int maxVersion;

    public RangeRule(int minVersion, int maxVersion) {
        this.minVersion = minVersion;
        this.maxVersion = maxVersion;
    }

    @Override
    public boolean matches(int versionCode) {
        return versionCode >= minVersion && versionCode <= maxVersion;
    }
}

class ExactVersionRule extends VersionRule {
    private final int version;

    public ExactVersionRule(int version) {
        this.version = version;
    }

    @Override
    public boolean matches(int versionCode) {
        return versionCode == version;
    }
}

class VpnRulesStorage extends SafeFileStorage<Map<String, Map<String, List<String>>>> {
    private static final String LOCK_FILE = "vpn_rules.lock";
    private static final String TEMP_FILE = "vpn_rules_temp.json";
    private static final String RULES_FILE = "vpn_rules.json";

    public VpnRulesStorage() {
        super(LOCK_FILE, TEMP_FILE, RULES_FILE);
    }

    @Override
    protected void writeDataToStream(Map<String, Map<String, List<String>>> data,
                                     OutputStreamWriter writer) throws IOException {
        try {
            JSONObject jsonObject = new JSONObject();
            for (Map.Entry<String, Map<String, List<String>>> categoryEntry : data.entrySet()) {
                String category = categoryEntry.getKey(); // "exclude" or "include"
                Map<String, List<String>> rules = categoryEntry.getValue();

                JSONObject categoryObject = new JSONObject();
                for (Map.Entry<String, List<String>> ruleEntry : rules.entrySet()) {
                    String packageName = ruleEntry.getKey();
                    List<String> versionRules = ruleEntry.getValue();
                    categoryObject.put(packageName, new JSONArray(versionRules));
                }
                jsonObject.put(category, categoryObject);
            }
            writer.write(jsonObject.toString());
        } catch (JSONException e) {
            throw new IOException("Failed to serialize VPN rules to JSON", e);
        }
    }

    @Override
    protected Map<String, Map<String, List<String>>> readDataFromStream(BufferedReader reader) throws IOException {
        try {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }

            JSONObject jsonObject = new JSONObject(builder.toString());
            Map<String, Map<String, List<String>>> vpnRules = new HashMap<>();

            // Parse exclude rules
            JSONObject excludeRules = jsonObject.optJSONObject("exclude");
            if (excludeRules != null) {
                vpnRules.put("exclude", VpnRulesHelper.parseRulesCategory(excludeRules));
            } else {
                vpnRules.put("exclude", new HashMap<>());
            }

            // Parse include rules
            JSONObject includeRules = jsonObject.optJSONObject("include");
            if (includeRules != null) {
                vpnRules.put("include", VpnRulesHelper.parseRulesCategory(includeRules));
            } else {
                vpnRules.put("include", new HashMap<>());
            }

            return vpnRules;
        } catch (JSONException e) {
            throw new IOException("Failed to parse VPN rules from JSON", e);
        }
    }

    private Map<String, List<String>> parseRulesCategory(JSONObject categoryObject) throws JSONException {
        return VpnRulesHelper.parseRulesCategory(categoryObject);
    }

    @Override
    protected Map<String, Map<String, List<String>>> getDefaultValue() {
        Map<String, Map<String, List<String>>> defaultRules = new HashMap<>();
        defaultRules.put("exclude", new HashMap<>());
        defaultRules.put("include", new HashMap<>());
        return defaultRules;
    }
}

public class VpnRulesHelper {

    private static final VpnRulesStorage vpnRulesStorage = new VpnRulesStorage();

    // Public helper method for parsing JSON rules category - reused by both storage and TunnelManager
    public static Map<String, List<String>> parseRulesCategory(JSONObject categoryObject) throws JSONException {
        Map<String, List<String>> categoryRules = new HashMap<>();
        java.util.Iterator<String> keys = categoryObject.keys();
        while (keys.hasNext()) {
            String packageName = keys.next();
            JSONArray versionRulesArray = categoryObject.getJSONArray(packageName);
            List<String> versionRules = new ArrayList<>();
            for (int i = 0; i < versionRulesArray.length(); i++) {
                versionRules.add(versionRulesArray.getString(i));
            }
            categoryRules.put(packageName, versionRules);
        }
        return categoryRules;
    }

    // Hardcoded default VPN rules with version constraints (like TRUSTED_PACKAGES pattern)
    private static final Map<String, List<String>> DEFAULT_EXCLUDED_VPN_RULES;
    private static final Map<String, List<String>> DEFAULT_INCLUDED_VPN_RULES;

    static {
        // Default excluded apps with version rules
        Map<String, List<String>> excludeMap = new HashMap<>();
        excludeMap.put("ca.psiphon.conduit",
                Collections.singletonList("*")); // Conduit, must be listed in PackageHelper.TRUSTED_PACKAGES
        excludeMap.put("network.ryve.app",
                Collections.singletonList("*")); // Ryve, must be listed in PackageHelper.TRUSTED_PACKAGES
        DEFAULT_EXCLUDED_VPN_RULES = Collections.unmodifiableMap(excludeMap);

        // Default included apps with version rules
        Map<String, List<String>> includeMap = new HashMap<>();
        // Any default included apps would go here and must be listed in PackageHelper.TRUSTED_PACKAGES
        DEFAULT_INCLUDED_VPN_RULES = Collections.unmodifiableMap(includeMap);
    }

    // Runtime VPN rules
    private static final ConcurrentHashMap<String, List<VersionRule>> runtimeExcludedVpnRules =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, List<VersionRule>> runtimeIncludedVpnRules =
            new ConcurrentHashMap<>();

    // Get ALL apps that have exclusion rules (hardcoded + runtime) - for UI hiding, etc.
    @NonNull
    public static Set<String> getAllManagedExclusionApps() {
        Set<String> allApps = new HashSet<>();
        allApps.addAll(DEFAULT_EXCLUDED_VPN_RULES.keySet());
        allApps.addAll(runtimeExcludedVpnRules.keySet());
        return allApps;
    }

    // Get ALL apps that have inclusion rules (hardcoded + runtime) - for UI hiding, etc.
    @NonNull
    public static Set<String> getAllManagedInclusionApps() {
        Set<String> allApps = new HashSet<>();
        allApps.addAll(DEFAULT_INCLUDED_VPN_RULES.keySet());
        allApps.addAll(runtimeIncludedVpnRules.keySet());
        return allApps;
    }

    // Save VPN rules to file (similar to saveTrustedSignaturesToFile)
    public static void saveVpnRulesToFile(Context context, Map<String, Map<String, List<String>>> vpnRules) {
        vpnRulesStorage.save(context, vpnRules);
    }

    // Read VPN rules from file (similar to readTrustedSignaturesFromFile)
    public static Map<String, Map<String, List<String>>> readVpnRulesFromFile(Context context) {
        return vpnRulesStorage.load(context);
    }

    // Configure runtime VPN rules (similar to configureRuntimeTrustedSignatures)
    public static void configureRuntimeVpnRules(Map<String, Map<String, List<String>>> vpnRules) {
        runtimeExcludedVpnRules.clear();
        runtimeIncludedVpnRules.clear();

        // Process excluded apps
        Map<String, List<String>> excludeRules = vpnRules.get("exclude");
        if (excludeRules != null) {
            for (Map.Entry<String, List<String>> entry : excludeRules.entrySet()) {
                String packageName = entry.getKey();
                List<String> versionRuleStrings = entry.getValue();
                List<VersionRule> versionRules = VersionRule.parseVersionRules(versionRuleStrings);
                if (!versionRules.isEmpty()) {
                    runtimeExcludedVpnRules.put(packageName, Collections.unmodifiableList(versionRules));
                }
            }
        }

        // Process included apps
        Map<String, List<String>> includeRules = vpnRules.get("include");
        if (includeRules != null) {
            for (Map.Entry<String, List<String>> entry : includeRules.entrySet()) {
                String packageName = entry.getKey();
                List<String> versionRuleStrings = entry.getValue();
                List<VersionRule> versionRules = VersionRule.parseVersionRules(versionRuleStrings);
                if (!versionRules.isEmpty()) {
                    runtimeIncludedVpnRules.put(packageName, Collections.unmodifiableList(versionRules));
                }
            }
        }

        MyLog.i("VpnRulesHelper: loaded runtime VPN rules for " +
                (excludeRules != null ? excludeRules.size() : 0) + " excluded apps and " +
                (includeRules != null ? includeRules.size() : 0) + " included apps");
    }

    // Get VPN exclusion rules for a package (combines default + runtime, similar to getExpectedSignaturesForPackage)
    @NonNull
    public static List<VersionRule> getVpnExclusionRulesForPackage(String packageName) {
        List<VersionRule> rules = new ArrayList<>();

        // Add default excluded rules
        List<String> defaultRules = DEFAULT_EXCLUDED_VPN_RULES.get(packageName);
        if (defaultRules != null) {
            rules.addAll(VersionRule.parseVersionRules(defaultRules));
        }

        // Add runtime excluded rules
        List<VersionRule> runtimeRules = runtimeExcludedVpnRules.get(packageName);
        if (runtimeRules != null) {
            rules.addAll(runtimeRules);
        }

        return Collections.unmodifiableList(rules);
    }

    // Get VPN inclusion rules for a package (combines default + runtime)
    @NonNull
    public static List<VersionRule> getVpnInclusionRulesForPackage(String packageName) {
        List<VersionRule> rules = new ArrayList<>();

        // Add default included rules
        List<String> defaultRules = DEFAULT_INCLUDED_VPN_RULES.get(packageName);
        if (defaultRules != null) {
            rules.addAll(VersionRule.parseVersionRules(defaultRules));
        }

        // Add runtime included rules
        List<VersionRule> runtimeRules = runtimeIncludedVpnRules.get(packageName);
        if (runtimeRules != null) {
            rules.addAll(runtimeRules);
        }

        return Collections.unmodifiableList(rules);
    }

    // Check if a package matches an always-exclude rule based on version
    public static boolean matchesAlwaysExcludeRule(String packageName, int versionCode) {
        List<VersionRule> exclusionRules = getVpnExclusionRulesForPackage(packageName);
        for (VersionRule rule : exclusionRules) {
            if (rule.matches(versionCode)) {
                return true;
            }
        }
        return false;
    }

    // Check if a package matches an always-include rule based on version
    public static boolean matchesAlwaysIncludeRule(String packageName, int versionCode) {
        List<VersionRule> inclusionRules = getVpnInclusionRulesForPackage(packageName);
        for (VersionRule rule : inclusionRules) {
            if (rule.matches(versionCode)) {
                return true;
            }
        }
        return false;
    }

    // Check if a package matches ANY VPN rule (exclude OR include) for its version
    public static boolean matchesAnyVpnRule(String packageName, int versionCode) {
        return matchesAlwaysExcludeRule(packageName, versionCode) ||
                matchesAlwaysIncludeRule(packageName, versionCode);
    }

    // Get set of apps that actually match exclusion rules for their currently installed versions
    @NonNull
    public static Set<String> getVersionAwareManagedExclusionApps(android.content.pm.PackageManager pm) {
        Set<String> matchingApps = new HashSet<>();
        Set<String> allManagedApps = getAllManagedExclusionApps();

        for (String packageName : allManagedApps) {
            try {
                android.content.pm.PackageInfo packageInfo;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    packageInfo = pm.getPackageInfo(packageName,
                            android.content.pm.PackageManager.PackageInfoFlags.of(0));
                } else {
                    packageInfo = pm.getPackageInfo(packageName, 0);
                }
                int versionCode = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P ?
                        (int) packageInfo.getLongVersionCode() : packageInfo.versionCode;

                if (matchesAlwaysExcludeRule(packageName, versionCode)) {
                    matchingApps.add(packageName);
                }
            } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                // App not installed, skip
            }
        }
        return matchingApps;
    }

    // Get set of apps that actually match inclusion rules for their currently installed versions
    @NonNull
    public static Set<String> getVersionAwareManagedInclusionApps(android.content.pm.PackageManager pm) {
        Set<String> matchingApps = new HashSet<>();
        Set<String> allManagedApps = getAllManagedInclusionApps();

        for (String packageName : allManagedApps) {
            try {
                android.content.pm.PackageInfo packageInfo;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    packageInfo = pm.getPackageInfo(packageName,
                            android.content.pm.PackageManager.PackageInfoFlags.of(0));
                } else {
                    packageInfo = pm.getPackageInfo(packageName, 0);
                }
                int versionCode = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P ?
                        (int) packageInfo.getLongVersionCode() : packageInfo.versionCode;

                if (matchesAlwaysIncludeRule(packageName, versionCode)) {
                    matchingApps.add(packageName);
                }
            } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                // App not installed, skip
            }
        }
        return matchingApps;
    }
}
