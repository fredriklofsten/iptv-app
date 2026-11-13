package se.fredriklofsten.iptv;

import android.content.Intent;
import androidx.activity.result.ActivityResult;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

// Startar den inbyggda spelaren och lämnar tillbaka var man slutade titta.
@CapacitorPlugin(name = "NativePlayer")
public class NativePlayerPlugin extends Plugin {
    @PluginMethod
    public void play(PluginCall call) {
        String url = call.getString("url");
        if (url == null || url.isEmpty()) { call.reject("Länk saknas"); return; }
        Double start = call.getDouble("startSec", 0.0);
        Intent intent = new Intent(getContext(), PlayerActivity.class);
        intent.putExtra("url", url);
        intent.putExtra("title", call.getString("title", ""));
        intent.putExtra("startMs", (long) ((start == null ? 0 : start) * 1000));
        startActivityForResult(call, intent, "onPlayerClosed");
    }

    @ActivityCallback
    private void onPlayerClosed(PluginCall call, ActivityResult result) {
        if (call == null) return;
        JSObject out = new JSObject();
        Intent data = result.getData();
        if (data != null) {
            out.put("position", data.getLongExtra("positionMs", 0) / 1000.0);
            out.put("duration", data.getLongExtra("durationMs", 0) / 1000.0);
            out.put("ended", data.getBooleanExtra("ended", false));
            String err = data.getStringExtra("error");
            if (err != null) out.put("error", err);
        }
        call.resolve(out);
    }
}
