package com.simple.carlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Self-update from the GitHub release "latest" (published by the "Compilar APK" workflow).
 *
 * The release holds two files: CarLauncher.apk and version.txt ("versionCode=N / versionName=X").
 * When version.txt has a higher versionCode than the installed app, the user is asked and the APK
 * is downloaded and handed to the system installer (PackageInstaller). Android only accepts the
 * update when it is signed with the same key as the installed app, which the workflow guarantees.
 */
public class Updater {

    private static final String TAG = "Updater";
    static final String BASE = "https://github.com/dallinacv2/CleanLauncher/releases/download/latest/";
    /** Automatic checks happen at most this often (12 h). */
    private static final long CHECK_EVERY_MS = 12L * 60 * 60 * 1000;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile boolean busy;

    private static class Remote {
        int code;
        String name;
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("updater", Context.MODE_PRIVATE);
    }

    @SuppressWarnings("deprecation")
    static int installedCode(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return pi.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    static String installedName(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    /** Called when the launcher comes to the front: looks for an update at most every 12 h. */
    static void checkAuto(Activity a) {
        if (System.currentTimeMillis() - prefs(a).getLong("last_check", 0) < CHECK_EVERY_MS) return;
        check(a, false);
    }

    /** Looks for an update now. {@code manual} also reports "up to date" and errors. */
    static void check(Activity a, boolean manual) {
        if (busy) {
            if (manual) toast(a, "Ya se está actualizando…");
            return;
        }
        busy = true;
        if (manual) toast(a, "Buscando actualizaciones…");
        new Thread(() -> {
            Remote r = null;
            try {
                r = fetchVersion();
            } catch (Exception e) {
                Log.w(TAG, "check failed", e);
            }
            final Remote result = r;
            MAIN.post(() -> {
                busy = false;
                onChecked(a, result, manual);
            });
        }).start();
    }

    private static void onChecked(Activity a, Remote r, boolean manual) {
        if (r == null) {
            if (manual) toast(a, "No se pudo buscar. Revisa que el radio tenga internet.");
            return;
        }
        prefs(a).edit().putLong("last_check", System.currentTimeMillis()).apply();
        if (r.code <= installedCode(a)) {
            if (manual) toast(a, "Ya tienes la última versión (" + installedName(a) + ")");
            return;
        }
        if (a.isFinishing() || a.isDestroyed()) return;
        new AlertDialog.Builder(a)
                .setTitle("Actualización disponible")
                .setMessage("Hay una versión nueva (" + r.name + "). ¿Descargarla e instalarla ahora?")
                .setPositiveButton("Actualizar", (d, w) -> download(a.getApplicationContext()))
                .setNegativeButton("Después", null)
                .show();
    }

    private static Remote fetchVersion() throws Exception {
        HttpURLConnection con = open(BASE + "version.txt");
        try {
            if (con.getResponseCode() != 200) throw new Exception("HTTP " + con.getResponseCode());
            StringBuilder sb = new StringBuilder();
            byte[] buf = new byte[512];
            try (InputStream in = con.getInputStream()) {
                int n;
                while ((n = in.read(buf)) > 0 && sb.length() < 2048) sb.append(new String(buf, 0, n, "UTF-8"));
            }
            Matcher code = Pattern.compile("versionCode=(\\d+)").matcher(sb);
            Matcher name = Pattern.compile("versionName=(\\S+)").matcher(sb);
            if (!code.find()) throw new Exception("version.txt sin versionCode");
            Remote r = new Remote();
            r.code = Integer.parseInt(code.group(1));
            r.name = name.find() ? name.group(1) : String.valueOf(r.code);
            return r;
        } finally {
            con.disconnect();
        }
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(15000);
        con.setReadTimeout(30000);
        con.setInstanceFollowRedirects(true); // GitHub redirects release files to another host
        return con;
    }

    /** Downloads the APK, checks it is really this app, and hands it to the system installer. */
    private static void download(Context app) {
        if (busy) return;
        busy = true;
        toast(app, "Descargando la actualización…");
        new Thread(() -> {
            File apk = new File(app.getCacheDir(), "update.apk");
            String error = null;
            try {
                HttpURLConnection con = open(BASE + "CarLauncher.apk");
                try {
                    if (con.getResponseCode() != 200) throw new Exception("HTTP " + con.getResponseCode());
                    try (InputStream in = con.getInputStream(); OutputStream out = new FileOutputStream(apk)) {
                        byte[] buf = new byte[16384];
                        int n;
                        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    }
                } finally {
                    con.disconnect();
                }
                PackageInfo pi = app.getPackageManager().getPackageArchiveInfo(apk.getPath(), 0);
                if (pi == null || !app.getPackageName().equals(pi.packageName)) {
                    throw new Exception("el archivo bajado no es esta app");
                }
                install(app, apk);
            } catch (Exception e) {
                Log.w(TAG, "update failed", e);
                error = "No se pudo descargar la actualización.";
            }
            final String msg = error;
            MAIN.post(() -> {
                busy = false;
                if (msg != null) toast(app, msg);
            });
        }).start();
    }

    private static void install(Context app, File apk) throws Exception {
        PackageInstaller pi = app.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(app.getPackageName());
        int id = pi.createSession(params);
        try (PackageInstaller.Session s = pi.openSession(id)) {
            try (InputStream in = new FileInputStream(apk);
                 OutputStream out = s.openWrite("update", 0, apk.length())) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                s.fsync(out);
            }
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE; // the installer adds extras
            PendingIntent done = PendingIntent.getBroadcast(app, id,
                    new Intent(app, InstallReceiver.class), flags);
            s.commit(done.getIntentSender());
        }
    }

    static void toast(Context c, String msg) {
        MAIN.post(() -> Toast.makeText(c, msg, Toast.LENGTH_LONG).show());
    }
}
