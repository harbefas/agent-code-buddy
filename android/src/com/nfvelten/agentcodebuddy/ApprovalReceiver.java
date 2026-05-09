package com.nfvelten.agentcodebuddy;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class ApprovalReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Buddy.ACTION_DECIDE.equals(action)) {
            handleDecision(context, intent);
            return;
        }
        if (Buddy.ACTION_APPROVAL.equals(action)) {
            showApproval(context, intent);
        }
    }

    private void showApproval(Context context, Intent intent) {
        ensureChannel(context);
        String url = intent.getStringExtra(Buddy.EXTRA_URL);
        String requestId = intent.getStringExtra(Buddy.EXTRA_REQUEST_ID);
        String title = value(intent.getStringExtra(Buddy.EXTRA_TITLE), "Code Buddy needs approval");
        String content = value(intent.getStringExtra(Buddy.EXTRA_CONTENT), "Tap to review");

        StateStore store = new StateStore(context);
        store.saveServerUrl(url);
        store.addRequest(requestId, extractAgent(title), extractTool(title), title, content, "pending", null);

        Intent open = new Intent(context, MainActivity.class);
        open.setAction(Buddy.ACTION_OPEN);
        open.putExtra(Buddy.EXTRA_URL, url);
        open.putExtra(Buddy.EXTRA_REQUEST_ID, requestId);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, Buddy.CHANNEL_APPROVALS)
                : new Notification.Builder(context);

        builder.setSmallIcon(com.nfvelten.agentcodebuddy.R.drawable.ic_stat_buddy)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(new Notification.BigTextStyle().bigText(content))
                .setLargeIcon(makeLargeIcon())
                .setContentIntent(pendingActivity(context, open, 1))
                .setAutoCancel(true)
                .setOngoing(false)
                .setPriority(Notification.PRIORITY_HIGH)
                .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE);

        builder.addAction(0, "Allow", decisionIntent(context, url, requestId, "allow_once", 2));
        builder.addAction(0, "Trust", decisionIntent(context, url, requestId, "always_allow", 3));
        builder.addAction(0, "Deny", decisionIntent(context, url, requestId, "reject", 4));

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(Buddy.NOTIFICATION_ID, builder.build());
        context.startActivity(open);
    }

    private void handleDecision(Context context, Intent intent) {
        final PendingResult result = goAsync();
        final String url = intent.getStringExtra(Buddy.EXTRA_URL);
        final String requestId = intent.getStringExtra(Buddy.EXTRA_REQUEST_ID);
        final String decision = intent.getStringExtra(Buddy.EXTRA_DECISION);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    postDecision(url, requestId, decision);
                    StateStore store = new StateStore(context);
                    store.updateDecision(requestId, decision);
                    NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                    manager.cancel(Buddy.NOTIFICATION_ID);
                } catch (Exception ignored) {
                } finally {
                    result.finish();
                }
            }
        }).start();
    }

    private void postDecision(String baseUrl, String requestId, String decision) throws Exception {
        URL target = new URL(baseUrl + "/api/decide/" + requestId);
        HttpURLConnection connection = (HttpURLConnection) target.openConnection();
        connection.setConnectTimeout(4000);
        connection.setReadTimeout(4000);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setDoOutput(true);
        String body = "{\"decision\":\"" + decision + "\"}";
        try (OutputStream output = connection.getOutputStream()) {
            output.write(body.getBytes("UTF-8"));
        }
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IllegalStateException("bad response " + code);
        }
    }

    private PendingIntent decisionIntent(Context context, String url, String requestId, String decision, int code) {
        Intent intent = new Intent(Buddy.ACTION_DECIDE);
        intent.setClass(context, ApprovalReceiver.class);
        intent.putExtra(Buddy.EXTRA_URL, url);
        intent.putExtra(Buddy.EXTRA_REQUEST_ID, requestId);
        intent.putExtra(Buddy.EXTRA_DECISION, decision);
        return PendingIntent.getBroadcast(context, code, intent, flags());
    }

    private PendingIntent pendingActivity(Context context, Intent intent, int code) {
        return PendingIntent.getActivity(context, code, intent, flags());
    }

    private int flags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return flags;
    }

    private void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(
                Buddy.CHANNEL_APPROVALS,
                "Approvals",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Approval requests from coding agents");
        manager.createNotificationChannel(channel);
    }

    private Bitmap makeLargeIcon() {
        Bitmap bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.rgb(23, 23, 23));
        canvas.drawRoundRect(0, 0, 192, 192, 28, 28, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(6);
        paint.setColor(Color.rgb(159, 202, 120));
        canvas.drawRoundRect(18, 24, 174, 168, 18, 18, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(26);
        paint.setColor(Color.rgb(159, 202, 120));
        canvas.drawText(".[||].", 96, 72, paint);
        canvas.drawText("[ o  o ]", 96, 105, paint);
        canvas.drawText("[ == ]", 96, 138, paint);
        return bitmap;
    }

    private String value(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private String extractAgent(String title) {
        if (title == null) return "agent";
        int dot = title.indexOf('·');
        if (dot > 0) return title.substring(0, dot).trim();
        return "agent";
    }

    private String extractTool(String title) {
        if (title == null) return "tool";
        int dot = title.indexOf('·');
        if (dot > 0 && dot + 1 < title.length()) return title.substring(dot + 1).trim();
        return "tool";
    }
}
