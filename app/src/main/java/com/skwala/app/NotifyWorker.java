package com.skwala.app;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** checks SK WALA about once an hour for new mock tests, announcements and messages */
public class NotifyWorker extends Worker {
    private static final String WORK = "sk_notify";

    public NotifyWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    static void schedule(Context c) {
        Constraints cons = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest r = new PeriodicWorkRequest.Builder(NotifyWorker.class, 1, TimeUnit.HOURS)
                .setConstraints(cons).build();
        WorkManager.getInstance(c).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, r);
    }

    static void cancel(Context c) {
        WorkManager.getInstance(c).cancelUniqueWork(WORK);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context c = getApplicationContext();
        SharedPreferences p = Notifier.prefs(c);
        String sid = p.getString("sid", ""), token = p.getString("token", "");
        if (sid.isEmpty() || token.isEmpty()) return Result.success();
        try {
            String url = c.getString(R.string.site_url) + "?api=notify&sid=" + URLEncoder.encode(sid, "UTF-8")
                    + "&token=" + URLEncoder.encode(token, "UTF-8");
            JSONObject j = new JSONObject(fetch(url));
            if (!j.optBoolean("ok")) return Result.success();
            SharedPreferences.Editor ed = p.edit();

            // ---- messages / announcements
            JSONArray msgs = j.optJSONArray("messages");
            if (msgs != null && msgs.length() > 0) {
                String last = p.getString("lastMsgAt", null);
                String max = last;
                int shown = 0;
                for (int i = msgs.length() - 1; i >= 0; i--) {   // oldest first
                    JSONObject m = msgs.getJSONObject(i);
                    String at = m.optString("at", "");
                    if (max == null || at.compareTo(max) > 0) max = at;
                    if (last != null && at.compareTo(last) > 0 && shown < 3) {
                        String kind = m.optString("kind", "MESSAGE");
                        String label = "ANNOUNCEMENT".equals(kind) ? "📢 Announcement" : "INFO".equals(kind) ? "ℹ️ Information" : "✉️ New message";
                        String title = m.optString("title", "");
                        String text = m.optString("text", "");
                        if (text.isEmpty()) text = m.optInt("files", 0) > 0 ? "A file has been shared with you. Tap to open." : "Tap to read.";
                        Notifier.show(c, ("m" + m.optString("id")).hashCode(),
                                title.isEmpty() ? label + " – SK WALA" : label + ": " + title, text, "messages");
                        shown++;
                    }
                }
                if (max != null) ed.putString("lastMsgAt", max);
            }

            // ---- new mock tests
            JSONArray papers = j.optJSONArray("papers");
            if (papers != null) {
                String knownStr = p.getString("knownPapers", null);
                Set<String> known = knownStr == null ? null : new HashSet<>(Arrays.asList(knownStr.split(",")));
                StringBuilder all = new StringBuilder();
                int shown = 0;
                for (int i = 0; i < papers.length(); i++) {
                    JSONObject t = papers.getJSONObject(i);
                    String id = t.optString("id");
                    if (all.length() > 0) all.append(',');
                    all.append(id);
                    if (known != null && !known.contains(id) && shown < 3) {
                        Notifier.show(c, ("p" + id).hashCode(), "📝 New mock test: " + t.optString("title"),
                                t.optString("folder") + " · " + t.optString("series") + ". Tap to start.", "home");
                        shown++;
                    }
                }
                ed.putString("knownPapers", all.toString());
            }
            ed.apply();
        } catch (Exception e) {
            // no internet / site busy – try again next hour
        }
        return Result.success();
    }

    private static String fetch(String address) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(address).openConnection();
        con.setInstanceFollowRedirects(true);
        con.setConnectTimeout(20000);
        con.setReadTimeout(30000);
        con.setRequestProperty("User-Agent", "SKWALAApp");
        try (BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally {
            con.disconnect();
        }
    }
}
