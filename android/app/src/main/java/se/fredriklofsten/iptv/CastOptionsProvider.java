package se.fredriklofsten.iptv;

import android.content.Context;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.framework.CastOptions;
import com.google.android.gms.cast.framework.OptionsProvider;
import com.google.android.gms.cast.framework.SessionProvider;
import com.google.android.gms.cast.framework.media.CastMediaOptions;
import com.google.android.gms.cast.framework.media.NotificationOptions;
import java.util.List;

// Google Cast: använder Googles standardmottagare på Chromecast/TV
public class CastOptionsProvider implements OptionsProvider {
    @Override
    public CastOptions getCastOptions(Context context) {
        return new CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .setResumeSavedSession(true)
            // Kontroller i notisfältet och på låsskärmen medan TV:n spelar, även när appen är stängd
            .setCastMediaOptions(new CastMediaOptions.Builder()
                .setNotificationOptions(new NotificationOptions.Builder()
                    .setTargetActivityClassName(MainActivity.class.getName()).build())
                .build())
            .build();
    }

    @Override
    public List<SessionProvider> getAdditionalSessionProviders(Context context) {
        return null;
    }
}
