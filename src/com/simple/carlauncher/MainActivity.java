package com.simple.carlauncher;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.Calendar;

public class MainActivity extends Activity implements NowPlaying.Listener {

    private static final String NAME = "Darlin";

    // The car lights picture turns on at 19:00 and off at 06:30 (minutes since midnight).
    private static final int LIGHTS_ON_MIN = 19 * 60;
    private static final int LIGHTS_OFF_MIN = 6 * 60 + 30;

    private static final long WEATHER_EVERY_MS = 30 * 60 * 1000;

    private final Handler handler = new Handler();
    private NowPlaying nowPlaying;

    private TextView greeting, weatherTemp, weatherDesc, weatherCity, weatherExtra;
    private ImageView weatherIcon;
    private GradientDrawable weatherBg;
    private ImageView car, carLights;
    private TextView oilInfo;
    private GradientDrawable oilBg;
    private long lastWeatherFetch;

    private TextView radioHint;
    private boolean lightsOn;
    private ImageView art, playBtn;
    private TextView songSource, songTitle, songArtist;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(4));
        root.setBackground(Ui.background(this));

        LinearLayout cards = new LinearLayout(this);
        cards.addView(buildWeatherCard(), weight(1.2f, 0));

        LinearLayout middle = new LinearLayout(this);
        middle.setOrientation(LinearLayout.VERTICAL);
        middle.addView(buildClockCard(), vweight(0.62f, 0));
        middle.addView(buildRadioCard(), vweight(1.38f, dp(12)));
        cards.addView(middle, weight(1f, dp(12)));

        cards.addView(buildMusicCard(), weight(1.05f, dp(12)));
        root.addView(cards, new LinearLayout.LayoutParams(-1, 0, 1f));

        root.addView(buildDock(), new LinearLayout.LayoutParams(-1, dp(84)));
        setContentView(root);

        nowPlaying = NowPlaying.get(this);
        startService(new Intent(this, Odometer.class));
    }

    // ---------- Greeting + weather ----------

    private View buildWeatherCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(26), dp(24), dp(26), dp(22));
        weatherBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0x993FA9FF, 0x667B4DFF});
        weatherBg.setCornerRadius(dp(18));
        weatherBg.setStroke(dp(1), 0x447FD8FF);
        card.setBackground(weatherBg);

        TextView hello = left(Ui.text(this, "Hola, " + NAME, 34, Color.WHITE));
        hello.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(hello);

        greeting = left(Ui.text(this, "", 17, 0xCCFFFFFF));
        greeting.setPadding(0, dp(4), 0, 0);
        card.addView(greeting);

        // The user's own car (photo with background removed); headlights glow when the car's lights are on.
        FrameLayout carBox = new FrameLayout(this);
        car = new ImageView(this);
        car.setImageResource(R.drawable.optra);
        car.setScaleType(ImageView.ScaleType.FIT_CENTER);
        carBox.addView(car, new FrameLayout.LayoutParams(-1, -1));
        carLights = new ImageView(this);
        carLights.setImageResource(R.drawable.optra_lights);
        carLights.setScaleType(ImageView.ScaleType.FIT_CENTER);
        carLights.setAlpha(0f);
        carBox.addView(carLights, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, 0, 1f);
        clp.setMargins(dp(30), dp(8), dp(30), dp(8));
        card.addView(carBox, clp);

        // Compact weather row: icon + temperature + description, leaving room for the car.
        LinearLayout now = new LinearLayout(this);
        now.setGravity(Gravity.CENTER_VERTICAL);
        weatherIcon = new ImageView(this);
        weatherIcon.setImageResource(R.drawable.ic_w_cloud);
        weatherIcon.setColorFilter(0xFFFFE07A);
        now.addView(weatherIcon, new LinearLayout.LayoutParams(dp(54), dp(54)));
        weatherTemp = left(Ui.text(this, "--°", 50, Color.WHITE));
        weatherTemp.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        weatherTemp.setIncludeFontPadding(false);
        weatherTemp.setPadding(dp(10), 0, dp(12), 0);
        now.addView(weatherTemp);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        weatherDesc = left(Ui.text(this, "Cargando el clima…", 16, Color.WHITE));
        weatherDesc.setSingleLine(false);
        weatherDesc.setMaxLines(2);
        texts.addView(weatherDesc);
        weatherCity = left(Ui.text(this, "", 13, 0xB3FFFFFF));
        weatherCity.setPadding(0, dp(2), 0, 0);
        texts.addView(weatherCity);
        now.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(now);

        weatherExtra = left(Ui.text(this, "", 14, 0xE6FFFFFF));
        weatherExtra.setPadding(dp(14), dp(8), dp(14), dp(8));
        GradientDrawable chip = new GradientDrawable();
        chip.setColor(0x26FFFFFF);
        chip.setCornerRadius(dp(20));
        weatherExtra.setBackground(chip);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = dp(10);
        card.addView(weatherExtra, lp);

        Ui.pressable(card, 0.98f);
        card.setOnClickListener(v -> fetchWeather(true));
        return card;
    }

    private void updateGreeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        greeting.setText((h < 12 ? "Buenos días" : h < 19 ? "Buenas tardes" : "Buenas noches")
                + " · Bienvenido");
    }

    private void showWeather(Weather w) {
        if (w == null) return;
        weatherIcon.setImageResource(w.icon());
        weatherIcon.setColorFilter(w.icon() == R.drawable.ic_w_sun ? 0xFFFFD25A
                : w.icon() == R.drawable.ic_w_moon ? 0xFFDDE4FF : Color.WHITE);
        weatherTemp.setText(w.temp + "°");
        weatherDesc.setText(w.description());
        weatherCity.setText(w.city.isEmpty() ? "Sensación " + w.feelsLike + "°"
                : w.city + " · Sensación " + w.feelsLike + "°");
        weatherExtra.setText("↑ " + w.max + "°   ↓ " + w.min + "°   💧 " + w.humidity + "%   " + w.wind + " km/h");
        weatherBg.setColors(w.isDay ? new int[]{0x993FA9FF, 0x667B4DFF} : new int[]{0x8C2A3A9E, 0x80261A6E});
    }

    private void fetchWeather(boolean force) {
        long now = SystemClock.elapsedRealtime();
        if (!force && lastWeatherFetch != 0 && now - lastWeatherFetch < WEATHER_EVERY_MS) return;
        lastWeatherFetch = now;
        Weather.fetch(this, this::showWeather);
    }

    private final Runnable periodic = new Runnable() {
        public void run() {
            updateGreeting();
            updateCarLights(false);
            fetchWeather(false);
            handler.postDelayed(this, 60 * 1000);
        }
    };

    // ---------- Car lights (by time of day) ----------

    /** Lights picture on from 19:00 until 06:30. */
    private boolean lightsShouldBeOn() {
        Calendar now = Calendar.getInstance();
        int minutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        return minutes >= LIGHTS_ON_MIN || minutes < LIGHTS_OFF_MIN;
    }

    private void updateCarLights(boolean instant) {
        boolean on = lightsShouldBeOn();
        if (instant) {
            carLights.setAlpha(on ? 1f : 0f);
            car.setAlpha(on ? 0f : 1f);
        } else if (on != lightsOn) {
            showCarLights(on);
        }
        lightsOn = on;
    }

    private void showCarLights(boolean on) {
        // Cross-fade between the "lights off" and "lights on" photos.
        carLights.animate().alpha(on ? 1f : 0f).setDuration(600).start();
        car.animate().alpha(on ? 0f : 1f).setDuration(600).start();
    }

    private void updateOil() {
        if (!Odometer.isConfigured(this)) {
            oilInfo.setText("Aceite: configurar");
            oilBg.setColor(0x26FFFFFF);
            return;
        }
        int left = Odometer.remainingKm(this);
        oilInfo.setText(left < 0 ? "Aceite: ¡ya toca el cambio!" : "Aceite: faltan " + OilActivity.fmt(left) + " km");
        oilBg.setColor(left < 0 ? 0xCCE53935 : left <= Odometer.WARN_KM ? 0xCCFF9800 : 0x26FFFFFF);
    }

    // ---------- Clock ----------

    private View buildClockCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackground(Ui.card(this));

        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("hh:mm");
        time.setTextSize(40);
        time.setTextColor(Color.WHITE);
        time.setTypeface(Typeface.DEFAULT_BOLD);
        time.setIncludeFontPadding(false);
        time.setGravity(Gravity.CENTER);
        card.addView(time);

        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE, d 'de' MMMM");
        date.setFormat12Hour("EEEE, d 'de' MMMM");
        date.setTextSize(14);
        date.setTextColor(0xCCFFFFFF);
        date.setPadding(0, dp(6), 0, 0);
        date.setGravity(Gravity.CENTER);
        card.addView(date);

        // Oil-change status pill; opens the oil screen.
        oilInfo = Ui.text(this, "", 13, Color.WHITE);
        oilInfo.setPadding(dp(14), dp(6), dp(14), dp(6));
        oilBg = new GradientDrawable();
        oilBg.setCornerRadius(dp(14));
        oilInfo.setBackground(oilBg);
        Ui.pressable(oilInfo, 0.92f);
        oilInfo.setOnClickListener(v -> startActivity(new Intent(this, OilActivity.class)));
        LinearLayout.LayoutParams olp = new LinearLayout.LayoutParams(-2, -2);
        olp.topMargin = dp(8);
        card.addView(oilInfo, olp);

        Ui.pressable(card, 0.97f);
        card.setOnClickListener(v -> openSettings());
        return card;
    }

    // ---------- Radio shortcut ----------

    private View buildRadioCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackground(Ui.card(this));

        ImageView radioIcon = new ImageView(this);
        radioIcon.setImageResource(R.drawable.ic_radio);
        radioIcon.setColorFilter(0xFFFF9F5A);
        card.addView(radioIcon, new LinearLayout.LayoutParams(dp(56), dp(56)));

        TextView title = Ui.text(this, "Radio", 30, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, dp(8), 0, 0);
        card.addView(title);

        radioHint = Ui.text(this, "", 15, 0x99FFFFFF);
        radioHint.setPadding(dp(12), dp(4), dp(12), 0);
        card.addView(radioHint);

        Ui.pressable(card, 0.97f);
        card.setOnClickListener(v -> AppLinks.open(this, AppLinks.RADIO));
        card.setOnLongClickListener(v -> {
            AppLinks.pick(this, AppLinks.RADIO);
            return true;
        });
        return card;
    }

    // ---------- Music (any source) ----------

    private View buildMusicCard() {
        FrameLayout card = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF6A3BFF, 0xFFC04DE8, 0xFF2B1A7A});
        bg.setCornerRadius(dp(18));
        card.setBackground(bg);
        card.setClipToOutline(true);

        art = new ImageView(this);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        card.addView(art, new FrameLayout.LayoutParams(-1, -1));

        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x33000000, 0x00000000, 0xCC100A30}));
        card.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        songSource = Ui.text(this, "MÚSICA", 12, Color.WHITE);
        songSource.setLetterSpacing(0.1f);
        songSource.setPadding(dp(12), dp(5), dp(12), dp(5));
        GradientDrawable chip = new GradientDrawable();
        chip.setColor(0x40000000);
        chip.setCornerRadius(dp(14));
        songSource.setBackground(chip);
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        slp.setMargins(dp(14), dp(14), 0, 0);
        card.addView(songSource, slp);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(16), 0, dp(16), dp(18));
        songTitle = Ui.text(this, "", 21, Color.WHITE);
        songTitle.setTypeface(Typeface.DEFAULT_BOLD);
        bottom.addView(songTitle);
        songArtist = Ui.text(this, "", 15, 0xCCFFFFFF);
        songArtist.setPadding(0, dp(2), 0, dp(14));
        bottom.addView(songArtist);

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.addView(Ui.iconButton(this, R.drawable.ic_prev, 50, false, () -> nowPlaying.skip(false)),
                new LinearLayout.LayoutParams(dp(50), dp(50)));
        playBtn = Ui.iconButton(this, R.drawable.ic_play, 66, true, () -> nowPlaying.playPause());
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(66), dp(66));
        plp.setMargins(dp(18), 0, dp(18), 0);
        controls.addView(playBtn, plp);
        controls.addView(Ui.iconButton(this, R.drawable.ic_next, 50, false, () -> nowPlaying.skip(true)),
                new LinearLayout.LayoutParams(dp(50), dp(50)));
        bottom.addView(controls);
        card.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        Ui.pressable(card, 0.98f);
        card.setOnClickListener(v -> nowPlaying.open(this));
        return card;
    }

    @Override
    public void onNowPlayingChanged() {
        NowPlaying.Source s = nowPlaying.current();
        NowPlaying.Track t = s == null ? null : nowPlaying.track(s);
        boolean empty = t == null || t.title.isEmpty();
        songSource.setText(s == null ? "MÚSICA" : nowPlaying.label(s).toUpperCase());
        songTitle.setText(empty ? "Nada sonando" : t.title);
        songArtist.setText(empty ? "Toca para abrir la música" : t.artist);
        playBtn.setImageResource(t != null && t.playing ? R.drawable.ic_pause : R.drawable.ic_play);

        if (t != null && t.art != null) {
            art.setPadding(0, 0, 0, 0);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.clearColorFilter();
            art.setAlpha(1f);
            art.setImageBitmap(t.art);
        } else {
            art.setPadding(dp(80), dp(56), dp(80), dp(170));
            art.setScaleType(ImageView.ScaleType.FIT_CENTER);
            art.setImageResource(s == NowPlaying.Source.BT ? R.drawable.ic_bluetooth : R.drawable.ic_music);
            art.setColorFilter(Color.WHITE);
            art.setAlpha(0.25f);
        }
    }

    // ---------- Dock ----------

    private View buildDock() {
        LinearLayout dock = new LinearLayout(this);
        dock.setGravity(Gravity.CENTER_VERTICAL);
        dock.addView(dockIcon(R.drawable.ic_compass, AppLinks.NAV), weight(1f, 0));
        dock.addView(dockIcon(R.drawable.ic_bluetooth, AppLinks.BT), weight(1f, 0));
        dock.addView(dockIcon(R.drawable.ic_radio, AppLinks.RADIO), weight(1f, 0));

        FrameLayout center = new FrameLayout(this);
        RingView ring = new RingView(this);
        center.addView(ring, new FrameLayout.LayoutParams(dp(64), dp(64), Gravity.CENTER));
        Ui.pressable(ring, 0.88f);
        ring.setOnClickListener(v -> startActivity(new Intent(this, AppsActivity.class)));
        dock.addView(center, weight(1f, 0));

        dock.addView(dockIcon(R.drawable.ic_music, AppLinks.MUSIC), weight(1f, 0));
        dock.addView(dockIcon(R.drawable.ic_settings, AppLinks.EQ), weight(1f, 0));
        dock.addView(dockIcon(R.drawable.ic_gear, this::openSettings, null), weight(1f, 0));
        return dock;
    }

    /** Button that opens the app the user chose for it; long press chooses another app. */
    private View dockIcon(int icon, String key) {
        return dockIcon(icon, () -> AppLinks.open(this, key), () -> AppLinks.pick(this, key));
    }

    private View dockIcon(int icon, Runnable action, Runnable longAction) {
        FrameLayout f = new FrameLayout(this);
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(0xE6FFFFFF);
        iv.setPadding(dp(12), dp(12), dp(12), dp(12));
        f.addView(iv, new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER));
        Ui.pressable(iv, 0.85f);
        iv.setOnClickListener(v -> action.run());
        if (longAction != null) {
            iv.setOnLongClickListener(v -> {
                longAction.run();
                return true;
            });
        }
        return f;
    }

    private void openSettings() {
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            android.widget.Toast.makeText(this, "No disponible", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- Lifecycle & helpers ----------

    @Override
    protected void onResume() {
        super.onResume();
        updateGreeting();
        updateOil();
        showWeather(Weather.cached(this));
        updateCarLights(true);
        radioHint.setText(AppLinks.label(this, AppLinks.RADIO));
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
        handler.post(periodic);
        nowPlaying.addListener(this);
        onNowPlayingChanged();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(periodic);
        nowPlaying.removeListener(this);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) fetchWeather(true);
    }

    private TextView left(TextView tv) {
        tv.setGravity(Gravity.START);
        return tv;
    }

    private LinearLayout.LayoutParams weight(float w, int left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, w);
        lp.leftMargin = left;
        return lp;
    }

    private LinearLayout.LayoutParams vweight(float w, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 0, w);
        lp.topMargin = top;
        return lp;
    }

    private int dp(int v) {
        return Ui.dp(this, v);
    }

    static void pressable(View v) {
        Ui.pressable(v, 0.94f);
    }

    @Override
    public void onBackPressed() {
        // Home screen: nothing to go back to.
    }
}
