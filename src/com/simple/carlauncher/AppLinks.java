package com.simple.carlauncher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.widget.Toast;

/**
 * Shortcuts to the head unit's own apps (radio, Bluetooth, music...). The user picks the app for
 * each button once (long press the button); the choice is stored here and works on any radio.
 */
final class AppLinks {

    static final String NAV = "nav", BT = "bt", RADIO = "radio", MUSIC = "music", EQ = "eq";

    private AppLinks() { }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("applinks", Context.MODE_PRIVATE);
    }

    static String title(String key) {
        switch (key) {
            case NAV: return "Navegación";
            case BT: return "Bluetooth";
            case RADIO: return "Radio";
            case MUSIC: return "Música";
            default: return "Ecualizador";
        }
    }

    /** Package chosen by the user, or null. Navigation falls back to Google Maps if installed. */
    static String get(Context c, String key) {
        String pkg = prefs(c).getString(key, null);
        if (pkg == null && NAV.equals(key) && launchIntent(c, "com.google.android.apps.maps") != null) {
            pkg = "com.google.android.apps.maps";
        }
        return pkg;
    }

    static void set(Context c, String key, String pkg) {
        prefs(c).edit().putString(key, pkg).apply();
    }

    private static Intent launchIntent(Context c, String pkg) {
        return pkg == null ? null : c.getPackageManager().getLaunchIntentForPackage(pkg);
    }

    /** Name of the chosen app, or a hint when none is chosen yet. */
    static String label(Context c, String key) {
        String pkg = get(c, key);
        if (pkg != null) {
            try {
                PackageManager pm = c.getPackageManager();
                return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
            } catch (PackageManager.NameNotFoundException ignored) { }
        }
        return "Toca para elegir la app";
    }

    /** Opens the chosen app; if there is none (or it was uninstalled) lets the user pick one. */
    static void open(Context c, String key) {
        Intent i = launchIntent(c, get(c, key));
        if (i == null) {
            Toast.makeText(c, "Elige la app de " + title(key), Toast.LENGTH_SHORT).show();
            pick(c, key);
            return;
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            c.startActivity(i);
        } catch (Exception e) {
            Toast.makeText(c, "No disponible", Toast.LENGTH_SHORT).show();
        }
    }

    /** Shows the list of installed apps so the user can choose the one for this button. */
    static void pick(Context c, String key) {
        Intent i = new Intent(c, AppsActivity.class);
        i.putExtra(AppsActivity.EXTRA_PICK, key);
        if (!(c instanceof Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }
}
