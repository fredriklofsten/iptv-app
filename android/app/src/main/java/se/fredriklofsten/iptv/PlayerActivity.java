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

        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("IPTV-spelare/1.0 (Linux; Android) ExoPlayer")
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000);
        sources = new DefaultMediaSourceFactory(new DefaultDataSource.Factory(this, http));
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

        player.setMediaItem(itemFor(url, title));
        if (startMs > 0) player.seekTo(startMs);
        player.setPlayWhenReady(true);
        player.prepare();

        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED) { ended = true; done(); }
            }
            @Override public void onPlayerError(PlaybackException e) {
                // Live: en kanal som inte fungerar ska inte stänga spelaren – visa felet och låt användaren byta
                if (urls != null && urls.size() > 1) { showLabel("Kanalen fungerar inte just nu – byt med ▲▼"); return; }
                error = e.getErrorCodeName();
                done();
            }
        });

        if (urls != null && urls.size() > 1) showLabel(title);
        else if (title != null && !title.isEmpty()) Toast.makeText(this, title, Toast.LENGTH_SHORT).show();
        view.requestFocus();
        if (skipIntro) ui.postDelayed(introCheck, 1000);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { done(); }
        });
    }

    private MediaItem itemFor(String url, String title) {
        MediaItem.Builder item = new MediaItem.Builder()
            .setUri(Uri.parse(url))
            .setMediaMetadata(new MediaMetadata.Builder().setTitle(title).build());
        if (url.toLowerCase().contains(".m3u8")) item.setMimeType(MimeTypes.APPLICATION_M3U8);
        return item.build();
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
        player.setMediaItem(itemFor(urls.get(index), titles.get(index)));
        player.prepare();
        player.setPlayWhenReady(true);
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
            player.setPlayWhenReady(!player.getPlayWhenReady());
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
        if (player != null) {
            long dur = player.getDuration();
            result.putExtra("positionMs", player.getCurrentPosition());
            result.putExtra("durationMs", dur == C.TIME_UNSET ? 0 : dur);
        }
        result.putExtra("ended", ended);
        result.putExtra("index", index);
        if (error != null) result.putExtra("error", error);
        setResult(RESULT_OK, result);
        finish();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null && !isFinishing()) player.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ui.removeCallbacksAndMessages(null);
        if (player != null) { player.release(); player = null; }
    }
}
