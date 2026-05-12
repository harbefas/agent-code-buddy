package com.nfvelten.agentcodebuddy;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.AlphaAnimation;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements SensorEventListener {

    // Dark: Yerba Mate
    static final int D_BG     = 0xff282d1c;
    static final int D_BG_2   = 0xff363c26;
    static final int D_UI     = 0xff4f5b4a;
    static final int D_TX     = 0xffdce0d9;
    static final int D_TX_2   = 0xffa8b09f;
    static final int D_TX_3   = 0xff7a8573;
    static final int D_RED    = 0xffc25d44;
    static final int D_GREEN  = 0xff7a9e38;
    static final int D_BLUE   = 0xff7eb2d1;
    static final int D_ORANGE = 0xffc09060;
    static final int D_CYAN   = 0xff5ea89a;
    static final int D_YELLOW = 0xffa67c52;
    static final int D_PURPLE = 0xffb07878;

    // Light: Terere
    static final int L_BG     = 0xfffbf1c7;
    static final int L_BG_2   = 0xffebdfb0;
    static final int L_UI     = 0xffddd2a0;
    static final int L_TX     = 0xff3c3836;
    static final int L_TX_2   = 0xff504945;
    static final int L_TX_3   = 0xff665c54;
    static final int L_RED    = 0xff9d0006;
    static final int L_GREEN  = 0xff79740e;
    static final int L_BLUE   = 0xff076678;
    static final int L_ORANGE = 0xffc88010;
    static final int L_CYAN   = 0xff427b58;
    static final int L_YELLOW = 0xffb57614;
    static final int L_PURPLE = 0xff8f3f71;

    int BG, BG_2, UI, TX, TX_2, TX_3, RED, GREEN, BLUE, ORANGE, CYAN, YELLOW, PURPLE;

    private Handler handler;
    private Runnable animator;
    private int frameIndex;
    private TextView buddyView;
    private TextView statusView;
    private TextView statsView;
    private LinearLayout scrollContent;
    private StateStore store;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private long lastShakeTime;
    private float lastX, lastY, lastZ;
    private boolean isShaking;
    private boolean isFaceDown;
    private String currentState;
    private String prevState;
    private String overrideState;
    private long overrideUntil;
    private long lastEasterEgg;
    private int prevPendingCount;
    private boolean isProcessing;
    private boolean menuOpen;
    private boolean isDark;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handler = new Handler(Looper.getMainLooper());
        store = new StateStore(this);
        UiModeManager uiModeManager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        boolean systemDark = true;
        if (uiModeManager != null) {
            systemDark = uiModeManager.getNightMode() == UiModeManager.MODE_NIGHT_YES;
        }
        isDark = store.isDarkTheme(systemDark);
        applyThemeColors();
        currentState = "idle";
        prevState = "idle";
        lastEasterEgg = System.currentTimeMillis();

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        }

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        buildUi();
    }

    void applyThemeColors() {
        if (isDark) {
            BG = D_BG; BG_2 = D_BG_2; UI = D_UI;
            TX = D_TX; TX_2 = D_TX_2; TX_3 = D_TX_3;
            RED = D_RED; GREEN = D_GREEN; BLUE = D_BLUE;
            ORANGE = D_ORANGE; CYAN = D_CYAN; YELLOW = D_YELLOW; PURPLE = D_PURPLE;
        } else {
            BG = L_BG; BG_2 = L_BG_2; UI = L_UI;
            TX = L_TX; TX_2 = L_TX_2; TX_3 = L_TX_3;
            RED = L_RED; GREEN = L_GREEN; BLUE = L_BLUE;
            ORANGE = L_ORANGE; CYAN = L_CYAN; YELLOW = L_YELLOW; PURPLE = L_PURPLE;
        }
    }

    @Override protected void onResume() {
        super.onResume();
        refreshUi();
        startAnimation();
        if (accelerometer != null) sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override protected void onPause() {
        super.onPause();
        if (animator != null) handler.removeCallbacks(animator);
        sensorManager.unregisterListener(this);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        refreshUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(16), dp(20), dp(12));

        buddyView = new TextView(this);
        buddyView.setTextColor(GREEN);
        buddyView.setGravity(Gravity.CENTER);
        buddyView.setTextSize(16);
        buddyView.setTypeface(android.graphics.Typeface.MONOSPACE);
        buddyView.setPadding(dp(16), dp(20), dp(16), dp(20));
        buddyView.setBackgroundColor(BG_2);
        buddyView.setClickable(true);
        buddyView.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showMenu(); }
        });
        header.addView(buddyView);

        statsView = new TextView(this);
        statsView.setTextColor(TX_2);
        statsView.setGravity(Gravity.CENTER);
        statsView.setTextSize(13);
        statsView.setPadding(0, dp(10), 0, dp(4));
        statsView.setTypeface(android.graphics.Typeface.MONOSPACE);
        header.addView(statsView);

        statusView = new TextView(this);
        statusView.setTextColor(TX_3);
        statusView.setGravity(Gravity.CENTER);
        statusView.setTextSize(11);
        statusView.setPadding(0, dp(2), 0, dp(6));
        header.addView(statusView);

        root.addView(header);

        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        divider.setBackgroundColor(UI);
        root.addView(divider);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);

        scrollContent = new LinearLayout(this);
        scrollContent.setOrientation(LinearLayout.VERTICAL);
        scrollContent.setPadding(dp(20), dp(12), dp(20), dp(40));

        scroll.addView(scrollContent);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        applySystemInsets(root);
    }

    private void refreshUi() {
        scrollContent.removeAllViews();

        List<StateStore.RequestItem> pending = store.getPending();
        List<StateStore.RequestItem> history = store.getHistory();
        List<String> entries = store.getEntries();
        boolean hasPending = !pending.isEmpty();

        String newState;
        if (isFaceDown) newState = "sleep";
        else if (isShaking) newState = "dizzy";
        else if (System.currentTimeMillis() < store.getHeartUntil()) newState = "heart";
        else if (isProcessing) newState = "busy";
        else if (hasPending) newState = "attention";
        else newState = "idle";

        if (!newState.equals(currentState)) {
            prevState = currentState;
            if ("sleep".equals(prevState) && "idle".equals(newState)) {
                overrideState = "sleep_to_idle"; overrideUntil = System.currentTimeMillis() + 1200;
            } else if ("idle".equals(prevState) && "busy".equals(newState)) {
                overrideState = "idle_to_busy"; overrideUntil = System.currentTimeMillis() + 1200;
            }
        }
        currentState = newState;

        if (prevPendingCount > 0 && pending.isEmpty()) {
            overrideState = "celebrate"; overrideUntil = System.currentTimeMillis() + 3000;
        }
        prevPendingCount = pending.size();

        int tokens = store.getTokens();
        int tokensToday = store.getTokensToday();
        int level = 1 + (tokensToday / 50000);
        statsView.setText(String.format(Locale.getDefault(), "lvl %-2d  |  %,d tok  |  %d pending", level, tokensToday, pending.size()));

        String displayState = currentState;
        if (overrideState != null && System.currentTimeMillis() < overrideUntil) {
            displayState = overrideState;
        } else {
            overrideState = null;
        }

        int statusColor;
        if ("attention".equals(displayState)) statusColor = ORANGE;
        else if ("heart".equals(displayState)) statusColor = PURPLE;
        else if ("dizzy".equals(displayState)) statusColor = CYAN;
        else if ("sleep".equals(displayState) || "sleep_to_idle".equals(displayState)) statusColor = TX_3;
        else if ("celebrate".equals(displayState) || "dance".equals(displayState)) statusColor = YELLOW;
        else if ("busy".equals(displayState) || "idle_to_busy".equals(displayState)) statusColor = BLUE;
        else if ("hiccup".equals(displayState) || "sneeze".equals(displayState) || "yawn".equals(displayState) || "peekaboo".equals(displayState)) statusColor = GREEN;
        else statusColor = GREEN;
        buddyView.setTextColor(statusColor);
        statusView.setText(RobotPet.getStatus(displayState));
        statusView.setTextColor(statusColor);

        if (hasPending) {
            addSection("Current Request");
            for (StateStore.RequestItem item : pending) scrollContent.addView(makeCard(item, true));
        }

        if (!history.isEmpty()) {
            addSection("Recent Decisions");
            int limit = Math.min(history.size(), 15);
            for (int i = 0; i < limit; i++) scrollContent.addView(makeCard(history.get(i), false));
        }

        if (!hasPending && history.isEmpty() && entries.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No requests yet\n\nApprove something from your agent\nto see it here.");
            empty.setTextColor(TX_3);
            empty.setGravity(Gravity.CENTER);
            empty.setTextSize(14);
            empty.setPadding(0, dp(48), 0, dp(48));
            scrollContent.addView(empty);
        }
    }

    private void startAnimation() {
        if (animator != null) handler.removeCallbacks(animator);
        frameIndex = 0;
        animator = new Runnable() {
            @Override
            public void run() {
                if (buddyView != null) {
                    String displayState = currentState;
                    if (overrideState != null && System.currentTimeMillis() < overrideUntil) {
                        displayState = overrideState;
                    } else {
                        overrideState = null;
                    }
                    if ("idle".equals(displayState)) {
                        long now = System.currentTimeMillis();
                        if (now - lastEasterEgg > 12000) {
                            int roll = (int)(Math.random() * 200);
                            if (roll < 1) { overrideState = "dance"; overrideUntil = now + 2000; lastEasterEgg = now; }
                            else if (roll < 3) { overrideState = "hiccup"; overrideUntil = now + 1000; lastEasterEgg = now; }
                            else if (roll < 5) { overrideState = "sneeze"; overrideUntil = now + 1200; lastEasterEgg = now; }
                            else if (roll < 7) { overrideState = "peekaboo"; overrideUntil = now + 1500; lastEasterEgg = now; }
                            else if (roll < 10) { overrideState = "yawn"; overrideUntil = now + 1500; lastEasterEgg = now; }
                            if (overrideState != null) displayState = overrideState;
                        }
                    }
                    String[][] frames = RobotPet.getFrames(displayState);
                    if (frames.length > 0) {
                        int idx;
                        if (RobotPet.isPingPong(displayState)) {
                            int cycle = frames.length * 2 - 2;
                            if (cycle < 1) cycle = 1;
                            int pos = frameIndex % cycle;
                            idx = pos < frames.length ? pos : cycle - pos;
                        } else {
                            idx = frameIndex % frames.length;
                        }
                        buddyView.setText(join(frames[idx]));
                    }
                    statusView.setText(RobotPet.getStatus(displayState));
                }
                frameIndex++;
                String displayState = overrideState != null && System.currentTimeMillis() < overrideUntil ? overrideState : currentState;
                int delay = RobotPet.getDelay(displayState);
                handler.postDelayed(this, delay);
            }
        };
        handler.post(animator);
    }

    private String join(String[] lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            sb.append(lines[i]);
            if (i < lines.length - 1) sb.append("\n");
        }
        return sb.toString();
    }

    private void showMenu() {
        if (menuOpen) return;
        menuOpen = true;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Settings");
        String themeLabel = isDark ? "Switch to Light Theme" : "Switch to Dark Theme";
        String[] options = {themeLabel, "Clear All Data", "Close"};
        builder.setItems(options, new android.content.DialogInterface.OnClickListener() {
            @Override public void onClick(android.content.DialogInterface dialog, int which) {
                if (which == 0) {
                    store.setDarkTheme(!isDark);
                    recreate();
                } else if (which == 1) {
                    store.clearAll();
                    refreshUi();
                }
                menuOpen = false;
            }
        });
        builder.setOnCancelListener(new android.content.DialogInterface.OnCancelListener() {
            @Override public void onCancel(android.content.DialogInterface dialog) { menuOpen = false; }
        });
        builder.show();
    }

    private void addSection(String text) {
        TextView tv = new TextView(this);
        tv.setText(text.toUpperCase());
        tv.setTextColor(TX_3);
        tv.setTextSize(10);
        tv.setPadding(0, dp(20), 0, dp(10));
        tv.setLetterSpacing(0.1f);
        scrollContent.addView(tv);
    }

    private View makeCard(StateStore.RequestItem item, boolean isPending) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackgroundColor(isPending ? BG_2 : UI);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.addView(chip(item.agent, YELLOW));
        chips.addView(chip(item.tool, BLUE));
        card.addView(chips);

        TextView title = new TextView(this);
        title.setText(item.title);
        title.setTextColor(TX);
        title.setTextSize(15);
        title.setPadding(0, dp(10), 0, dp(10));
        card.addView(title);

        if (item.detail != null && !item.detail.isEmpty()) {
            TextView detail = new TextView(this);
            detail.setText(item.detail);
            detail.setTextColor(TX_2);
            detail.setTextSize(12);
            detail.setPadding(dp(10), dp(10), dp(10), dp(10));
            detail.setBackgroundColor(BG);
            card.addView(detail);
        }

        if (isPending) {
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setPadding(0, dp(10), 0, 0);
            final String reqId = item.id;
            actions.addView(actionBtn("Allow", GREEN, new Runnable() {
                @Override public void run() { decide(reqId, "allow_once"); }
            }));
            actions.addView(actionBtn("Trust", BLUE, new Runnable() {
                @Override public void run() { decide(reqId, "always_allow"); }
            }));
            actions.addView(actionBtn("Deny", RED, new Runnable() {
                @Override public void run() { decide(reqId, "reject"); }
            }));
            card.addView(actions);
        } else {
            TextView badge = new TextView(this);
            badge.setText(item.getDecisionLabel());
            badge.setTextSize(11);
            badge.setPadding(0, dp(6), 0, 0);
            if ("reject".equals(item.decision) || "timeout".equals(item.decision)) badge.setTextColor(RED);
            else badge.setTextColor(GREEN);
            card.addView(badge);
        }

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, 0, dp(10));
        card.setLayoutParams(p);

        AlphaAnimation fadeIn = new AlphaAnimation(0f, 1f);
        fadeIn.setDuration(200);
        card.startAnimation(fadeIn);

        return card;
    }

    private TextView chip(String text, int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(10);
        tv.setPadding(dp(8), dp(3), dp(8), dp(3));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, dp(8), dp(6));
        tv.setLayoutParams(p);
        return tv;
    }

    private TextView actionBtn(String text, int color, final Runnable onClick) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(BG);
        tv.setBackgroundColor(color);
        tv.setTextSize(13);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(12), dp(10), dp(12), dp(10));
        tv.setClickable(true);
        tv.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                tv.setAlpha(0.6f);
                v.postDelayed(new Runnable() {
                    @Override public void run() { tv.setAlpha(1f); }
                }, 100);
                onClick.run();
            }
        });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        p.setMargins(0, 0, dp(6), 0);
        tv.setLayoutParams(p);
        return tv;
    }

    private void decide(final String requestId, final String decision) {
        isProcessing = true;
        refreshUi();
        startAnimation();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    String url = store.getServerUrl();
                    if (url != null) {
                        URL target = new URL(url + "/api/decide/" + requestId);
                        HttpURLConnection conn = (HttpURLConnection) target.openConnection();
                        conn.setConnectTimeout(4000);
                        conn.setReadTimeout(4000);
                        conn.setRequestMethod("POST");
                        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                        conn.setDoOutput(true);
                        String body = "{\"decision\":\"" + decision + "\"}";
                        try (OutputStream output = conn.getOutputStream()) {
                            output.write(body.getBytes("UTF-8"));
                        }
                        conn.getResponseCode();
                        conn.disconnect();
                    }
                } catch (Exception e) {}
                store.updateDecision(requestId, decision);
                isProcessing = false;
                handler.post(new Runnable() {
                    @Override public void run() { refreshUi(); startAnimation(); }
                });
            }
        }).start();
    }

    private int dp(int px) {
        return (int) (px * getResources().getDisplayMetrics().density);
    }

    private void applySystemInsets(View view) {
        if (Build.VERSION.SDK_INT < 20) return;
        view.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                int bottom = Math.max(insets.getSystemWindowInsetBottom(), 56);
                int top = Math.max(insets.getSystemWindowInsetTop(), 72);
                v.setPadding(
                    Math.max(insets.getSystemWindowInsetLeft(), dp(20)),
                    top + dp(16),
                    Math.max(insets.getSystemWindowInsetRight(), dp(20)),
                    bottom + dp(16));
                return insets;
            }
        });
        view.requestApplyInsets();
    }

    @Override public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
        float x = event.values[0], y = event.values[1], z = event.values[2];

        if (z < -8.0f) {
            if (!isFaceDown) { isFaceDown = true; refreshUi(); }
        } else if (z > -2.0f) {
            if (isFaceDown) { isFaceDown = false; refreshUi(); }
        }

        float delta = Math.abs(x - lastX) + Math.abs(y - lastY) + Math.abs(z - lastZ);
        if (delta > 25 && !isShaking && (System.currentTimeMillis() - lastShakeTime) > 2000) {
            isShaking = true;
            lastShakeTime = System.currentTimeMillis();
            refreshUi();
            handler.postDelayed(new Runnable() {
                @Override public void run() { isShaking = false; refreshUi(); }
            }, 2000);
        }
        lastX = x; lastY = y; lastZ = z;
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
