package se.fredriklofsten.iptv;

import android.content.Intent;
import com.getcapacitor.JSArray;
import java.util.ArrayList;
import org.json.JSONException;
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
        intent.putExtra("skipIntro", Boolean.TRUE.equals(call.getBoolean("skipIntro", false)));
        intent.putExtra("castOnStart", Boolean.TRUE.equals(call.getBoolean("castOnStart", false)));
        intent.putExtra("live", Boolean.TRUE.equals(call.getBoolean("live", false)));
        // Valfri kanallista för live-TV
        JSArray list = call.getArray("channels");
        if (list != null && list.length() > 1) {
            ArrayList<String> urls = new ArrayList<>(), titles = new ArrayList<>();
            try {
                for (int i = 0; i < list.length(); i++) {
                    org.json.JSONObject o = list.getJSONObject(i);
                    urls.add(o.getString("url"));
                    titles.add(o.optString("name", ""));
                }
            } catch (JSONException e) { urls.clear(); titles.clear(); }
            if (!urls.isEmpty()) {
                intent.putStringArrayListExtra("urls", urls);
                intent.putStringArrayListExtra("titles", titles);
                intent.putExtra("index", call.getInt("index", 0));
            }
        }
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
            out.put("index", data.getIntExtra("index", -1));
            String err = data.getStringExtra("error");
            if (err != null) out.put("error", err);
            String det = data.getStringExtra("errorDetail");
            if (det != null) out.put("errorDetail", det);
        }
        call.resolve(out);
    }
}
