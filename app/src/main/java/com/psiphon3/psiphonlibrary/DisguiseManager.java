/*
 * Copyright (c) 2026, Shir o Khorshid contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.psiphon3.psiphonlibrary;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

import net.grandcentrix.tray.AppPreferences;

import com.psiphon3.R;

/**
 * Manages app disguise (stealth mode). Switches the launcher icon and name
 * by enabling/disabling activity-alias components, and provides the matching
 * notification icon and text resources for each disguise identity.
 *
 * Disguise identities:
 *   "default"    — Shir o Khorshid (real identity)
 *   "calculator" — Calculator
 *   "weather"    — Weather
 *   "notes"      — Notes
 *   "clock"      — Clock
 */
public class DisguiseManager {

    public static final String PREF_DISGUISE_IDENTITY = "disguiseIdentity";
    public static final String PREF_STEALTH_NOTIFICATIONS = "stealthNotifications";

    public static final String IDENTITY_DEFAULT = "default";
    public static final String IDENTITY_CALCULATOR = "calculator";
    public static final String IDENTITY_WEATHER = "weather";
    public static final String IDENTITY_NOTES = "notes";
    public static final String IDENTITY_CLOCK = "clock";

    // Activity-alias names (must match AndroidManifest.xml)
    private static final String ALIAS_DEFAULT = "com.psiphon3.LauncherDefault";
    private static final String ALIAS_CALCULATOR = "com.psiphon3.LauncherCalculator";
    private static final String ALIAS_WEATHER = "com.psiphon3.LauncherWeather";
    private static final String ALIAS_NOTES = "com.psiphon3.LauncherNotes";
    private static final String ALIAS_CLOCK = "com.psiphon3.LauncherClock";

    private static final String[] ALL_ALIASES = {
            ALIAS_DEFAULT, ALIAS_CALCULATOR, ALIAS_WEATHER, ALIAS_NOTES, ALIAS_CLOCK
    };

    private static final String[] ALL_IDENTITIES = {
            IDENTITY_DEFAULT, IDENTITY_CALCULATOR, IDENTITY_WEATHER, IDENTITY_NOTES, IDENTITY_CLOCK
    };

    /**
     * Returns the currently active disguise identity.
     */
    public static String getCurrentIdentity(Context context) {
        AppPreferences prefs = new AppPreferences(context);
        String identity = prefs.getString(PREF_DISGUISE_IDENTITY, IDENTITY_DEFAULT);
        return identity != null ? identity : IDENTITY_DEFAULT;
    }

    /**
     * Returns whether stealth notifications are enabled.
     */
    public static boolean isStealthNotificationsEnabled(Context context) {
        AppPreferences prefs = new AppPreferences(context);
        return prefs.getBoolean(PREF_STEALTH_NOTIFICATIONS, false);
    }

    /**
     * Switches the app's launcher identity. Enables the chosen activity-alias
     * and disables all others. The change takes effect after the launcher
     * refreshes (may take a few seconds).
     *
     * @param identity One of IDENTITY_* constants
     */
    public static void switchIdentity(Context context, String identity) {
        PackageManager pm = context.getPackageManager();
        String targetAlias = getAliasForIdentity(identity);

        for (String alias : ALL_ALIASES) {
            ComponentName cn = new ComponentName(context, alias);
            int newState = alias.equals(targetAlias)
                    ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            pm.setComponentEnabledSetting(cn, newState, PackageManager.DONT_KILL_APP);
        }

        // Persist the choice using cross-process preferences
        new AppPreferences(context).put(PREF_DISGUISE_IDENTITY, identity);
    }

    /**
     * Returns the notification icon drawable resource for the current identity.
     * In stealth mode, returns a disguise-appropriate icon.
     * Otherwise, returns the default notification icon.
     */
    public static int getNotificationIcon(Context context, boolean isConnected) {
        if (!isStealthNotificationsEnabled(context)) {
            // Return 0 to signal "use default behavior"
            return 0;
        }

        String identity = getCurrentIdentity(context);
        switch (identity) {
            case IDENTITY_CALCULATOR:
                return R.drawable.ic_notif_calculator;
            case IDENTITY_WEATHER:
                return R.drawable.ic_notif_weather;
            case IDENTITY_NOTES:
                return R.drawable.ic_notif_notes;
            case IDENTITY_CLOCK:
                return R.drawable.ic_notif_clock;
            default:
                return 0; // Use default notification icon
        }
    }

    /**
     * Returns the notification title for the current disguise identity.
     * In stealth mode, returns a mundane title matching the disguise.
     * Returns null to signal "use default title".
     */
    public static String getNotificationTitle(Context context) {
        if (!isStealthNotificationsEnabled(context)) {
            return null;
        }

        String identity = getCurrentIdentity(context);
        switch (identity) {
            case IDENTITY_CALCULATOR:
                return context.getString(R.string.disguise_name_calculator);
            case IDENTITY_WEATHER:
                return context.getString(R.string.disguise_name_weather);
            case IDENTITY_NOTES:
                return context.getString(R.string.disguise_name_notes);
            case IDENTITY_CLOCK:
                return context.getString(R.string.disguise_name_clock);
            default:
                return null;
        }
    }

    /**
     * Returns the notification content text for the current disguise identity.
     * In stealth mode, returns mundane text matching the disguise and state.
     * Returns null to signal "use default text".
     *
     * @param state "connected", "connecting", or "waiting"
     */
    public static String getNotificationText(Context context, String state) {
        if (!isStealthNotificationsEnabled(context)) {
            return null;
        }

        String identity = getCurrentIdentity(context);
        switch (identity) {
            case IDENTITY_CALCULATOR:
                return "connected".equals(state)
                        ? context.getString(R.string.disguise_notif_calculator_connected)
                        : context.getString(R.string.disguise_notif_calculator_connecting);
            case IDENTITY_WEATHER:
                return "connected".equals(state)
                        ? context.getString(R.string.disguise_notif_weather_connected)
                        : context.getString(R.string.disguise_notif_weather_connecting);
            case IDENTITY_NOTES:
                return "connected".equals(state)
                        ? context.getString(R.string.disguise_notif_notes_connected)
                        : context.getString(R.string.disguise_notif_notes_connecting);
            case IDENTITY_CLOCK:
                return "connected".equals(state)
                        ? context.getString(R.string.disguise_notif_clock_connected)
                        : context.getString(R.string.disguise_notif_clock_connecting);
            default:
                return null;
        }
    }

    /**
     * Returns the notification channel name for stealth mode.
     * Returns null if not in stealth mode.
     */
    public static String getStealthChannelName(Context context) {
        if (!isStealthNotificationsEnabled(context)) {
            return null;
        }

        String identity = getCurrentIdentity(context);
        switch (identity) {
            case IDENTITY_CALCULATOR:
                return context.getString(R.string.disguise_name_calculator);
            case IDENTITY_WEATHER:
                return context.getString(R.string.disguise_name_weather);
            case IDENTITY_NOTES:
                return context.getString(R.string.disguise_name_notes);
            case IDENTITY_CLOCK:
                return context.getString(R.string.disguise_name_clock);
            default:
                return null;
        }
    }

    /**
     * Returns all available identity keys.
     */
    public static String[] getAllIdentities() {
        return ALL_IDENTITIES;
    }

    /**
     * Returns the human-readable name for a given identity.
     */
    public static String getIdentityDisplayName(Context context, String identity) {
        switch (identity) {
            case IDENTITY_CALCULATOR:
                return context.getString(R.string.disguise_name_calculator);
            case IDENTITY_WEATHER:
                return context.getString(R.string.disguise_name_weather);
            case IDENTITY_NOTES:
                return context.getString(R.string.disguise_name_notes);
            case IDENTITY_CLOCK:
                return context.getString(R.string.disguise_name_clock);
            default:
                return context.getString(R.string.app_name);
        }
    }

    private static String getAliasForIdentity(String identity) {
        switch (identity) {
            case IDENTITY_CALCULATOR:
                return ALIAS_CALCULATOR;
            case IDENTITY_WEATHER:
                return ALIAS_WEATHER;
            case IDENTITY_NOTES:
                return ALIAS_NOTES;
            case IDENTITY_CLOCK:
                return ALIAS_CLOCK;
            default:
                return ALIAS_DEFAULT;
        }
    }
}
