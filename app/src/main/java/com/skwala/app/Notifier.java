package com.skwala.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

/** notifications: "New mock test", "Announcement", "New message from Shreenath Kulal" */
final class Notifier {
    static final String CHANNEL = "sk_updates";
    static final String EXTRA_OPEN = "open";

    private Notifier() { }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("skwala", Context.MODE_PRIVATE);
    }

    /** remembers the logged-in student; returns true if it changed */
    static boolean saveUser(Context c, String sid, String token) {
        SharedPreferences p = prefs(c);
        boolean changed = !sid.equals(p.getString("sid", "")) || !token.equals(p.getString("token", ""));
        if (changed) {
            p.edit().putString("sid", sid).putString("token", token)
                    .remove("lastMsgAt").remove("knownPapers").apply();
        }
        return changed;
    }

    static void createChannel(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, c.getString(R.string.channel_name), NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription(c.getString(R.string.channel_desc));
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    @SuppressWarnings("deprecation")
    static void show(Context c, int id, String title, String text, String open) {
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        createChannel(c);
        Intent i = new Intent(c, MainActivity.class)
                .putExtra(EXTRA_OPEN, open)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, id, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setColor(0xFFE5640F)
                .setAutoCancel(true)
                .setContentIntent(pi);
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_DEFAULT);
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(id, b.build());
    }
}
