package com.says.hackomonia.myapplication;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;


public class Utils {
    // the global button at home page to mute or unmute sound for the entire application
    public static void setIfSoundIsMuted(Context context, boolean value) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("Global_Sound_Mute", value);
        editor.apply();
    }

    public static boolean getIfSoundIsMuted(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getBoolean("Global_Sound_Mute", true);
    }

    public static void setIfJustSignedIn(Context context, boolean value) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("just_signed_in", value);
        editor.apply();
    }

    public static boolean getIfJustSignedIn(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getBoolean("just_signed_in", false);
    }

    // used to set the name in settings screen
    public static void setPlayerName(Context context, String playerName) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("Global_User_Name", playerName);
        editor.apply();
    }

    public static String getPlayerName(Context context) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        return preferences.getString("Global_User_Name", "Player1");

    }
}
