package com.simple.carlauncher;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

/**
 * GPS odometer for the oil-change reminder (the car's CAN box does not report mileage).
 *
 * The user sets the dashboard reading once; from then on distance between good GPS fixes
 * is added. Fixes while parked or with poor accuracy are ignored so drift does not count.
 */
public class Odometer extends Service implements LocationListener {

    private static final String TAG = "Odometer";
    static final int DEFAULT_INTERVAL_KM = 5000;
    static final int WARN_KM = 500;

    private static final String CHANNEL = "odometer";
    private static final int NOTIF_ID = 42;

    private LocationManager lm;
    private Location last;
    private float pendingMeters;
    private boolean listening;

    @Override
    public void onCreate() {
        super.onCreate();
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        goForeground();
        startListening();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Every start (e.g. after the user grants the location permission) retries the GPS
        // registration. Before, it was only tried once in onCreate(), so when the permission was
        // granted after the service had started the odometer never received a single fix.
        goForeground();
        startListening();
        return START_STICKY;
    }

    /**
     * Runs as a foreground service so Android does not stop it (or throttle the GPS) as soon as
     * another app, such as the navigation app, is in front of the launcher.
     */
    private void goForeground() {
        try {
            Notification.Builder b;
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Odómetro GPS", NotificationManager.IMPORTANCE_MIN));
                b = new Notification.Builder(this, CHANNEL);
            } else {
                b = new Notification.Builder(this);
            }
            b.setSmallIcon(R.drawable.ic_apps).setContentTitle("Odómetro GPS activo").setOngoing(true);
            startForeground(NOTIF_ID, b.build());
        } catch (Exception e) {
            Log.w(TAG, "no foreground", e);
        }
    }

    private void startListening() {
        if (listening) return;
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 10, this);
            listening = true;
        } catch (Exception e) {
            Log.w(TAG, "no GPS", e);
        }
    }

    @Override
    public void onLocationChanged(Location l) {
        if (!l.hasAccuracy() || l.getAccuracy() > 25) return;
        if (last != null) {
            float d = last.distanceTo(l);
            float secs = Math.max(1, (l.getTime() - last.getTime()) / 1000f);
            float kmh = d / secs * 3.6f;
            boolean moving = (l.hasSpeed() ? l.getSpeed() * 3.6f : kmh) > 4;
            // Count only real driving; drop parked drift and impossible jumps.
            if (moving && kmh < 200) pendingMeters += d;
        }
        last = l;
        if (pendingMeters >= 100) { // persist every 100 m
            SharedPreferences p = prefs(this);
            p.edit().putFloat("gps_km", p.getFloat("gps_km", 0) + pendingMeters / 1000f).apply();
            pendingMeters = 0;
        }
    }

    public void onStatusChanged(String p, int s, Bundle b) { }
    public void onProviderEnabled(String p) { }
    public void onProviderDisabled(String p) { }

    @Override
    public void onDestroy() {
        super.onDestroy();
        lm.removeUpdates(this);
        listening = false;
        if (pendingMeters > 0) { // do not lose the last stretch driven
            SharedPreferences p = prefs(this);
            p.edit().putFloat("gps_km", p.getFloat("gps_km", 0) + pendingMeters / 1000f).apply();
            pendingMeters = 0;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---------- Shared state ----------

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("odometer", Context.MODE_PRIVATE);
    }

    static boolean isConfigured(Context c) {
        return prefs(c).contains("base_km");
    }

    /** Estimated dashboard reading: the value the user entered plus GPS distance since. */
    static int currentKm(Context c) {
        SharedPreferences p = prefs(c);
        return Math.round(p.getInt("base_km", 0) + p.getFloat("gps_km", 0));
    }

    static void setCurrentKm(Context c, int km) {
        prefs(c).edit().putInt("base_km", km).putFloat("gps_km", 0).apply();
    }

    static int lastChangeKm(Context c) {
        return prefs(c).getInt("last_change_km", currentKm(c));
    }

    static void setLastChangeKm(Context c, int km) {
        prefs(c).edit().putInt("last_change_km", km).apply();
    }

    static int intervalKm(Context c) {
        return prefs(c).getInt("interval_km", DEFAULT_INTERVAL_KM);
    }

    static void setIntervalKm(Context c, int km) {
        prefs(c).edit().putInt("interval_km", km).apply();
    }

    /** Km left until the next oil change (negative when overdue). */
    static int remainingKm(Context c) {
        return lastChangeKm(c) + intervalKm(c) - currentKm(c);
    }
}
