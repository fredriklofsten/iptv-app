package se.fredriklofsten.iptv;

import android.app.UiModeManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.webkit.WebSettings;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(ExternalPlayerPlugin.class);
        registerPlugin(NativePlayerPlugin.class);
        super.onCreate(savedInstanceState);
        try { UpdateCheckWorker.schedule(this); } catch (Exception ignored) {}
        try { setupTv(); } catch (Exception ignored) {}
    }

    // TV (TCL, Sony, Philips, Xiaomi, Hisense, Fire TV m.fl.): tala om för webbdelen att det är en TV,
    // och låt sidan ritas i 1920 bredd och skalas ner så att den ser likadan ut på alla märken.
    private void setupTv() {
        if (!isTv()) return;
        WebSettings s = getBridge().getWebView().getSettings();
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        String ua = s.getUserAgentString();
        if (ua != null && !ua.contains("AndroidTV")) s.setUserAgentString(ua + " AndroidTV");
    }

    private boolean isTv() {
        UiModeManager ui = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        if (ui != null && ui.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) return true;
        PackageManager pm = getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
            || pm.hasSystemFeature("android.software.leanback_only")
            || pm.hasSystemFeature("amazon.hardware.fire_tv")
            || !pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN);
    }
}
