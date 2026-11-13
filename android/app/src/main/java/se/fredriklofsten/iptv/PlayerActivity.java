package se.fredriklofsten.iptv;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
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
    private boolean ended = false, finished = false;
    private String error = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat bars = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        bars.hide(WindowInsetsCompat.Type.systemBars());
        bars.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        PlayerView view = new PlayerView(this);
        view.setBackgroundColor(Color.BLACK);
        view.setShowSubtitleButton(true);
        view.setKeepScreenOn(true);
        setContentView(view);

        Intent in = getIntent();
        String url = in.getStringExtra("url");
        String title = in.getStringExtra("title");
        long startMs = in.getLongExtra("startMs", 0);
        if (url == null) { error = "Länk saknas"; done(); return; }

        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("IPTV-spelare/1.0 (Linux; Android) ExoPlayer")
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000);
        DefaultMediaSourceFactory sources = new DefaultMediaSourceFactory(new DefaultDataSource.Factory(this, http));
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

        MediaItem.Builder item = new MediaItem.Builder()
            .setUri(Uri.parse(url))
            .setMediaMetadata(new MediaMetadata.Builder().setTitle(title).build());
        if (url.toLowerCase().contains(".m3u8")) item.setMimeType(MimeTypes.APPLICATION_M3U8);
        player.setMediaItem(item.build());
        if (startMs > 0) player.seekTo(startMs);
        player.setPlayWhenReady(true);
        player.prepare();

        player.addListener(new Player.Listener() {
            @Override public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED) { ended = true; done(); }
            }
            @Override public void onPlayerError(PlaybackException e) {
                error = e.getErrorCodeName();
                done();
            }
        });

        if (title != null && !title.isEmpty()) Toast.makeText(this, title, Toast.LENGTH_SHORT).show();
        view.requestFocus();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { done(); }
        });
    }

    // Fjärrkontrollens play/paus-knappar
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (player != null && (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_SPACE)) {
            player.setPlayWhenReady(!player.getPlayWhenReady());
            return true;
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
        if (player != null) { player.release(); player = null; }
    }
}
