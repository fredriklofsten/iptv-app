package se.fredriklofsten.iptv;

import android.content.Intent;
import android.net.Uri;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

// Öppnar en ström i en annan videospelare (t.ex. VLC eller MX Player),
// som klarar fler filformat, undertexter, ljudspår och casting.
@CapacitorPlugin(name = "ExternalPlayer")
public class ExternalPlayerPlugin extends Plugin {
    @PluginMethod
    public void open(PluginCall call) {
        String url = call.getString("url");
        String title = call.getString("title", "");
        if (url == null || url.isEmpty()) { call.reject("Länk saknas"); return; }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.parse(url), "video/*");
        intent.putExtra("title", title);       // VLC
        intent.putExtra("forcename", title);   // VLC (äldre)
        intent.putExtra("headers", new String[0]);
        Intent chooser = Intent.createChooser(intent, "Öppna med");
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            getActivity().startActivity(chooser);
            call.resolve();
        } catch (Exception e) {
            call.reject("Ingen annan videospelare hittades. Installera till exempel VLC.");
        }
    }
}
