package com.simple.carlauncher;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Spoken welcome: a few minutes after the launcher is opened it says a greeting, the current
 * weather, the oil reminder (when due), the seat-belt reminder and "drive carefully".
 * It speaks again only when at least {@link #MIN_INTERVAL_MS} have passed since the last greeting
 * (for example after the car was parked for hours).
 *
 * The phrases are pre-rendered clips (res/raw/v_*.mp3) because many head units have no
 * text-to-speech engine.
 */
public class GreeterService extends Service {

    private static final String TAG = "Greeter";

    /** How long after the launcher opens before it speaks (2 minutes). */
    private static final long DELAY_MS = 2 * 60 * 1000;
    /** Minimum time between two greetings (6 hours). */
    private static final long MIN_INTERVAL_MS = 6 * 60 * 60 * 1000;

    private final Handler handler = new Handler();
    private final List<Integer> queue = new ArrayList<>();
    private MediaPlayer player;
    private boolean pending;
    private AudioManager audio;

    @Override
    public void onCreate() {
        super.onCreate();
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    }

    /** Called every time the launcher comes to the front; decides whether a greeting is due. */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        long last = prefs().getLong("last_greet", 0);
        long now = System.currentTimeMillis();
        boolean due = last == 0 || now < last || now - last >= MIN_INTERVAL_MS;
        if (due && !pending) {
            pending = true;
            handler.postDelayed(greet, DELAY_MS);
            Weather.fetch(this, w -> { }); // refresh the cache before speaking
            Log.d(TAG, "greeting scheduled in " + DELAY_MS / 1000 + " s");
        }
        return START_STICKY;
    }

    private final Runnable greet = () -> {
        pending = false;
        prefs().edit().putLong("last_greet", System.currentTimeMillis()).apply();
        speak(buildMessage());
    };

    private List<String> buildMessage() {
        List<String> clips = new ArrayList<>();
        Calendar now = Calendar.getInstance();
        int day = now.get(Calendar.DAY_OF_WEEK);
        int h = now.get(Calendar.HOUR_OF_DAY);

        clips.add(h < 12 ? "v_greet_morning" : h < 19 ? "v_greet_afternoon" : "v_greet_night");

        Weather w = Weather.cached(this);
        if (w != null) {
            clips.add("v_cond_" + conditionClip(w.code));
            int t = Math.max(-5, Math.min(40, w.temp));
            clips.add(t < 0 ? "v_temp_m" + (-t) : "v_temp_" + t);
        }
        if (day == Calendar.MONDAY) clips.add("v_maintenance"); // oil, radiator, brake fluid
        if (Odometer.isConfigured(this)) {
            int left = Odometer.remainingKm(this);
            if (left < 0) clips.add("v_oil_due");
            else if (left <= Odometer.WARN_KM) clips.add("v_oil_soon");
        }
        clips.add("v_seatbelt");
        clips.add("v_careful");
        return clips;
    }

    private static String conditionClip(int code) {
        if (code == 0) return "clear";
        if (code == 1) return "mostly";
        if (code == 2) return "partly";
        if (code == 3) return "cloudy";
        if (code == 45 || code == 48) return "fog";
        if (code >= 51 && code <= 57) return "drizzle";
        if (code >= 61 && code <= 67) return "rain";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "snow";
        if (code >= 80 && code <= 82) return "showers";
        if (code >= 95) return "storm";
        return "cloudy";
    }

    /** Plays the clips one after another. */
    void speak(List<String> clips) {
        queue.clear();
        for (String name : clips) {
            int id = getResources().getIdentifier(name, "raw", getPackageName());
            if (id != 0) queue.add(id);
        }
        Log.d(TAG, "speaking " + clips);
        // Lower the music/radio while the voice talks.
        audio.requestAudioFocus(focus -> { }, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        playNext();
    }

    private void playNext() {
        if (player != null) {
            player.release();
            player = null;
        }
        if (queue.isEmpty()) {
            audio.abandonAudioFocus(focus -> { });
            return;
        }
        int id = queue.remove(0);
        player = new MediaPlayer();
        try {
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            player.setDataSource(this, android.net.Uri.parse(
                    "android.resource://" + getPackageName() + "/" + id));
            player.setOnCompletionListener(mp -> playNext());
            player.setOnErrorListener((mp, what, extra) -> {
                playNext();
                return true;
            });
            player.prepare();
            player.start();
        } catch (Exception e) {
            Log.w(TAG, "clip failed", e);
            playNext();
        }
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("greeter", MODE_PRIVATE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (player != null) player.release();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /** Starts the GPS odometer at boot even if the launcher is not shown yet. */
    public static class BootReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context c, Intent i) {
            c.startService(new Intent(c, Odometer.class));
        }
    }
}
