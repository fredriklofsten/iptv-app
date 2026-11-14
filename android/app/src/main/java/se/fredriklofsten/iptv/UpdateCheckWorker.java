package se.fredriklofsten.iptv;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Kollar i bakgrunden (även när appen är stängd) om en ny version finns och visar en notis.
public class UpdateCheckWorker extends Worker {
    private static final String VERSION_URL = "https://fredriklofsten.github.io/iptv-app/version.js";
    private static final String DOWNLOAD_URL = "https://github.com/fredriklofsten/iptv-app/releases/latest/download/IPTV-spelare.apk";
    private static final String CHANNEL = "uppdateringar";

    public UpdateCheckWorker(@NonNull Context ctx, @NonNull WorkerParameters params) { super(ctx, params); }

    public static void schedule(Context ctx) {
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(UpdateCheckWorker.class, 6, TimeUnit.HOURS).build();
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("uppdateringskontroll", ExistingPeriodicWorkPolicy.KEEP, req);
    }

    private static int parseBuild(String text) {
        Matcher m = Pattern.compile("APP_BUILD\\s*=\\s*(\\d+)").matcher(text == null ? "" : text);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private static String read(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in))) { String l; while ((l = r.readLine()) != null) sb.append(l); }
        return sb.toString();
    }

    @NonNull @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        try {
            int installed = parseBuild(read(ctx.getAssets().open("public/version.js")));
            HttpURLConnection c = (HttpURLConnection) new URL(VERSION_URL + "?t=" + System.currentTimeMillis()).openConnection();
            c.setConnectTimeout(15000); c.setReadTimeout(15000);
            int latest = parseBuild(read(c.getInputStream()));
            c.disconnect();
            SharedPreferences prefs = ctx.getSharedPreferences("uppdatering", Context.MODE_PRIVATE);
            if (installed > 0 && latest > installed && prefs.getInt("notified", 0) != latest) {
                if (notify(ctx, latest)) prefs.edit().putInt("notified", latest).apply();
            }
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        }
    }

    private boolean notify(Context ctx, int latest) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Uppdateringar", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Besked när en ny version av appen finns");
            ctx.getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
        // Tryck på notisen: ladda ner den nya versionen direkt
        Intent dl = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(DOWNLOAD_URL));
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, dl, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("⬆ Ny version av IPTV-spelare")
            .setContentText("Version " + latest + " finns. Tryck för att ladda ner och installera.")
            .setContentIntent(pi)
            .setAutoCancel(true);
        try { NotificationManagerCompat.from(ctx).notify(4242, b.build()); return true; } catch (SecurityException e) { return false; }
    }
}
