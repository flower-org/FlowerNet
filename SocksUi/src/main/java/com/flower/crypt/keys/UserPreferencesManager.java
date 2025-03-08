package com.flower.crypt.keys;

import org.apache.commons.lang3.StringUtils;

import java.util.prefs.Preferences;

public class UserPreferencesManager {
    public static void updateUserPreference(Preferences userPreferences, String key, String newValue) {
        String oldValue = userPreferences.get(key, "");
        if (!newValue.equals(oldValue)) {
            userPreferences.put(key, StringUtils.defaultIfBlank(newValue, ""));
        }
    }

    public static String getUserPreference(String key) {
        Preferences userPreferences = Preferences.userRoot();
        return userPreferences.get(key, "");
    }
}
