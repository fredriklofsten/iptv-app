package se.fredriklofsten.iptv;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import android.view.WindowManager;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.ui.PlayerView;
import androidx.media3.cast.CastPlayer;
import androidx.media3.cast.SessionAvailabilityListener;
import androidx.mediarouter.app.MediaRouteButton;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;

// Inbyggd videospelare (ExoPlayer): klarar HLS, MPEG-TS, MP4 och MKV,
// med val av ljudspår och undertexter via kugghjulet/CC-knappen.
public class PlayerActivity extends AppCompatActivity {
    private ExoPlayer player;
    private PlayerView view;
    private boolean ended = false, finished = false, skipIntro = false;
    private String error = null;
    // Kanallista för live-TV (byt med CH+/−, upp/ned eller knapparna)
    private ArrayList<String> urls, titles;
    private int index = 0;
    private TextView channelLabel;
    private Button skipBtn;
    private LinearLayout zapBox;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private DefaultMediaSourceFactory sources;
    // Många IPTV-leverantörer släpper bara igenom kända spelare. Vi provar dessa i tur och ordning.
    private static final String[] USER_AGENTS = {
        "VLC/3.0.20 LibVLC/3.0.20",
        "IPTVSmartersPlayer",
        "Lavf/60.16.100"
    };
    private int uaIndex = 0;
    private String errorDetail = null;
    private String castError = null;
    // Google Cast
    private CastPlayer castPlayer;
    private Player active;
    private MediaRouteButton castBtn;
    private String curUrl, curTitle;
    private boolean isLive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.hide(WindowInsetsCompat.Type.systemBars());
        bars.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        FrameLayout root = new FrameLayout(this);
        view = new PlayerView(this);
        view.setBackgroundColor(Color.BLACK);
        view.setShowSubtitleButton(true);
        view.setKeepScreenOn(true);
        root.addView(view, new FrameLayout.LayoutParams(-1, -1));

        channelLabel = new TextView(this);
        channelLabel.setTextColor(Color.WHITE);
        channelLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        channelLabel.setBackgroundColor(0xAA000000);
        channelLabel.setPadding(dp(16), dp(10), dp(16), dp(10));
        channelLabel.setVisibility(View.GONE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        lp.setMargins(dp(24), dp(24), 0, 0);
        root.addView(channelLabel, lp);

        skipBtn = new Button(this);
        skipBtn.setText("Hoppa över intro ⏭");
        skipBtn.setVisibility(View.GONE);
        skipBtn.setOnClickListener(v -> { if (player != null) player.seekTo(player.getCurrentPosition() + 85000); skipBtn.setVisibility(View.GONE); view.requestFocus(); });
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        sp.setMargins(0, 0, dp(32), dp(110));
        root.addView(skipBtn, sp);
        setContentView(root);

        Intent in = getIntent();
        String url = in.getStringExtra("url");
        String title = in.getStringExtra("title");
        long startMs = in.getLongExtra("startMs", 0);
        skipIntro = in.getBooleanExtra("skipIntro", false);
        urls = in.getStringArrayListExtra("urls");
        titles = in.getStringArrayListExtra("titles");
        index = in.getIntExtra("index", 0);
        if (urls != null && !urls.isEmpty() && index >= 0 && index < urls.size()) { url = urls.get(index); title = titles.get(index); }
        if (url == null) { error = "Länk saknas"; done(); return; }
        curUrl = url; curTitle = title;
        isLive = in.getBooleanExtra("live", false) || (urls != null && urls.size() > 1);

        if (urls != null && urls.size() > 1) {
            // Knappar för kanalbyte (för pekskärm)
            zapBox = new LinearLayout(this);
            zapBox.setOrientation(LinearLayout.VERTICAL);
            Button up = new Button(this); up.setText("▲"); up.setOnClickListener(v -> zap(-1));
            Button down = new Button(this); down.setText("▼"); down.setOnClickListener(v -> zap(1));
            zapBox.addView(up); zapBox.addView(down);
            FrameLayout.LayoutParams zp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL | Gravity.END);
            zp.setMargins(0, 0, dp(16), 0);
            root.addView(zapBox, zp);
            view.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) vis -> zapBox.setVisibility(vis));
        }

        sources = sourcesFor(USER_AGENTS[0]);
        DefaultRenderersFactory renderers = new DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER);
        DefaultTrackSelector tracks = new DefaultTrackSelector(this);
        tracks.setParameters(tracks.buildUponParameters()
            .setPreferredAudioLanguages("sv", "en")
            .setPreferredTextLanguages("sv"));

        player = new ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(sources)
            .setTrackSelector(tracks)
            .build();
        view.setPlayer(player);
        active = player;

        player.setMediaSource(sources.createMediaSource(itemFor(url, title)), Math.max(0, startMs));
        player.setPlayWhenReady(true);
        player.prepare();

        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED && active == player) { ended = true; done(); }
            }
            @Override public void onPlayerError(PlaybackException e) {
                // Nätverksfel: prova nästa spelaridentitet innan vi ger upp
                if (isNetworkError(e) && uaIndex < USER_AGENTS.length - 1) { retryWithNextAgent(); return; }
                // Live: en kanal som inte fungerar ska inte stänga spelaren – visa felet och låt användaren byta
                if (urls != null && urls.size() > 1) { showLabel("Kanalen fungerar inte just nu – byt med ▲▼"); uaIndex = 0; return; }
                error = e.getErrorCodeName();
                Throwable c = e.getCause();
                errorDetail = "försök=" + (uaIndex + 1) + " ua=" + USER_AGENTS[uaIndex] + (c != null ? " orsak=" + c.getClass().getSimpleName() + ": " + c.getMessage() : "");
                done();
            }
        });

        setupCast(root, in.getBooleanExtra("castOnStart", false));
        if (urls != null && urls.size() > 1) showLabel(title);
        else if (title != null && !title.isEmpty()) Toast.makeText(this, title, Toast.LENGTH_SHORT).show();
        view.requestFocus();
        if (skipIntro) ui.postDelayed(introCheck, 1000);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { done(); }
        });
    }

    private DefaultMediaSourceFactory sourcesFor(String userAgent) {
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent(userAgent)
            .setConnectTimeoutMs(30000)
            .setReadTimeoutMs(45000)
            .setKeepPostFor302Redirects(true);
        return new DefaultMediaSourceFactory(new DefaultDataSource.Factory(this, http));
    }

    private boolean isNetworkError(PlaybackException e) {
        int c = e.errorCode;
        return c == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
            || c == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
            || c == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
            || c == PlaybackException.ERROR_CODE_IO_UNSPECIFIED;
    }

    // Starta om samma video med nästa spelaridentitet och fortsätt där vi var
    private void retryWithNextAgent() {
        uaIndex++;
        long pos = isLive ? C.TIME_UNSET : Math.max(0, player.getCurrentPosition());
        sources = sourcesFor(USER_AGENTS[uaIndex]);
        player.setMediaSource(sources.createMediaSource(itemFor(curUrl, curTitle)), pos == C.TIME_UNSET ? 0 : pos);
        player.prepare();
        player.setPlayWhenReady(true);
        showLabel("Ansluter igen… (" + (uaIndex + 1) + "/" + USER_AGENTS.length + ")");
    }

    private String mimeFor(String url) {
        String u = url.toLowerCase();
        int q = u.indexOf('?'); if (q > 0) u = u.substring(0, q);
        if (u.contains(".m3u8")) return MimeTypes.APPLICATION_M3U8;
        if (u.endsWith(".mkv")) return MimeTypes.VIDEO_MATROSKA;
        if (u.endsWith(".ts")) return MimeTypes.VIDEO_MP2T;
        if (u.endsWith(".webm")) return MimeTypes.VIDEO_WEBM;
        return MimeTypes.VIDEO_MP4;
    }

    private MediaItem itemFor(String url, String title) {
        // Till Chromecast går strömmen via telefonen (CastProxy) – löser CORS, blockerad identitet och omdirigeringar
        String uri = (castPlayer != null && active == castPlayer) ? CastProxy.get().wrap(url) : url;
        MediaItem.Builder item = new MediaItem.Builder()
            .setUri(Uri.parse(uri))
            .setMediaMetadata(new MediaMetadata.Builder().setTitle(title).build());
        String mime = mimeFor(url);
        // Lokalt får ExoPlayer själv avgöra formatet utom för HLS; Chromecast behöver alltid typen
        if (MimeTypes.APPLICATION_M3U8.equals(mime) || active == castPlayer) item.setMimeType(mime);
        return item.build();
    }

    // ===== Google Cast (samma cast-ikon som i andra streamingappar) =====
    private void setupCast(FrameLayout root, boolean openChooser) {
        try {
            CastContext ctx = CastContext.getSharedInstance(this);
            castPlayer = new CastPlayer(ctx);
            castPlayer.addListener(new Player.Listener() {
                @Override public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_ENDED && active == castPlayer && !isLive) { ended = true; done(); }
                }
                @Override public void onPlayerError(PlaybackException e) {
                    String pe = CastProxy.get().lastError;
                    castError = e.getErrorCodeName() + (e.getMessage() != null ? " " + e.getMessage() : "") + (pe != null ? " | proxy: " + pe : "") + " | typ=" + mimeFor(curUrl);
                    Toast.makeText(PlayerActivity.this, "Chromecast kunde inte spela den här strömmen", Toast.LENGTH_LONG).show();
                    switchTo(player);
                }
            });
            castPlayer.setSessionAvailabilityListener(new SessionAvailabilityListener() {
                @Override public void onCastSessionAvailable() { switchTo(castPlayer); }
                @Override public void onCastSessionUnavailable() { switchTo(player); }
            });
            castBtn = new MediaRouteButton(this);
            CastButtonFactory.setUpMediaRouteButton(getApplicationContext(), castBtn);
            castBtn.setBackgroundColor(0x99000000);
            castBtn.setPadding(dp(8), dp(8), dp(8), dp(8));
            FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(dp(52), dp(52), Gravity.TOP | Gravity.END);
            cp.setMargins(0, dp(16), dp(16), 0);
            root.addView(castBtn, cp);
            view.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) vis -> {
                castBtn.setVisibility(active == castPlayer ? View.VISIBLE : vis);
                if (zapBox != null) zapBox.setVisibility(vis);
            });
            if (castPlayer.isCastSessionAvailable()) switchTo(castPlayer);
            else if (openChooser) ui.postDelayed(() -> { if (castBtn != null) castBtn.performClick(); }, 700);
        } catch (Exception e) {
            // Ingen Google Play-tjänst (t.ex. Fire TV): spela lokalt utan cast-ikon
            castPlayer = null;
            if (openChooser) Toast.makeText(this, "Chromecast stöds inte på den här enheten", Toast.LENGTH_LONG).show();
        }
    }

    // Flytta uppspelningen mellan telefonen och Chromecast och fortsätt på samma ställe
    private void switchTo(Player target) {
        if (target == null || target == active) return;
        Player prev = active;
        long pos = isLive ? C.TIME_UNSET : prev.getCurrentPosition();
        active = target;
        MediaItem item = itemFor(curUrl, curTitle);
        if (target == player) player.setMediaSource(sources.createMediaSource(item), pos != C.TIME_UNSET && pos > 0 ? pos : 0);
        else if (pos != C.TIME_UNSET && pos > 0) target.setMediaItem(item, pos); else target.setMediaItem(item);
        target.setPlayWhenReady(true);
        target.prepare();
        prev.stop();
        view.setPlayer(target);
        if (target == castPlayer) { showLabel("📺 Spelas på TV – " + curTitle); view.showController(); }
        else showLabel("Spelas på telefonen");
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void showLabel(String text) {
        if (text == null) return;
        String nr = urls != null && urls.size() > 1 ? (index + 1) + "  " : "";
        channelLabel.setText(nr + text);
        channelLabel.setVisibility(View.VISIBLE);
        ui.removeCallbacks(hideLabel);
        ui.postDelayed(hideLabel, 4000);
    }
    private final Runnable hideLabel = () -> channelLabel.setVisibility(View.GONE);

    // Byt kanal i live-listan
    private void zap(int d) {
        if (urls == null || urls.size() < 2 || player == null) return;
        index = (index + d + urls.size()) % urls.size();
        curUrl = urls.get(index); curTitle = titles.get(index);
        uaIndex = 0;
        if (active == player) { sources = sourcesFor(USER_AGENTS[0]); player.setMediaSource(sources.createMediaSource(itemFor(curUrl, curTitle))); }
        else active.setMediaItem(itemFor(curUrl, curTitle));
        active.prepare();
        active.setPlayWhenReady(true);
        showLabel(titles.get(index));
    }

    // Visa "Hoppa över intro" under avsnittets första minuter
    private final Runnable introCheck = new Runnable() {
        @Override public void run() {
            if (player == null) return;
            long p = player.getCurrentPosition();
            boolean show = p > 5000 && p < 240000;
            if (show && skipBtn.getVisibility() != View.VISIBLE) skipBtn.setVisibility(View.VISIBLE);
            if (!show && skipBtn.getVisibility() == View.VISIBLE) skipBtn.setVisibility(View.GONE);
            if (p < 240000) ui.postDelayed(this, 1000);
        }
    };

    // Fjärrkontroll: play/paus, CH+/−, och upp/ned när menyn är dold byter kanal
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (player == null) return super.onKeyDown(keyCode, event);
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_SPACE) {
            active.setPlayWhenReady(!active.getPlayWhenReady());
            return true;
        }
        boolean live = urls != null && urls.size() > 1;
        if (live && (keyCode == KeyEvent.KEYCODE_CHANNEL_UP || keyCode == KeyEvent.KEYCODE_PAGE_UP)) { zap(-1); return true; }
        if (live && (keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN || keyCode == KeyEvent.KEYCODE_PAGE_DOWN)) { zap(1); return true; }
        if (live && !view.isControllerFullyVisible()) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) { zap(-1); return true; }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) { zap(1); return true; }
        }
        if (skipBtn.getVisibility() == View.VISIBLE && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && !view.isControllerFullyVisible()) {
            skipBtn.requestFocus(); return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void done() {
        if (finished) return;
        finished = true;
        Intent result = new Intent();
        Player p = active != null ? active : player;
        if (p != null) {
            long dur = p.getDuration();
            result.putExtra("positionMs", p.getCurrentPosition());
            result.putExtra("durationMs", dur == C.TIME_UNSET ? 0 : dur);
        }
        result.putExtra("ended", ended);
        result.putExtra("index", index);
        if (error != null) result.putExtra("error", error);
        if (errorDetail != null) result.putExtra("errorDetail", errorDetail);
        if (castError != null) result.putExtra("castError", castError);
        setResult(RESULT_OK, result);
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null && !isFinishing() && active == player) player.pause(); // castning fortsätter i bakgrunden
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ui.removeCallbacksAndMessages(null);
        if (castPlayer != null) { castPlayer.setSessionAvailabilityListener(null); castPlayer.release(); castPlayer = null; }
        if (player != null) { player.release(); player = null; }
    }
}
