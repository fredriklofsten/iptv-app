package se.fredriklofsten.iptv;

import androidx.media3.cast.DefaultMediaItemConverter;
import androidx.media3.cast.MediaItemConverter;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;

import com.google.android.gms.cast.HlsSegmentFormat;
import com.google.android.gms.cast.HlsVideoSegmentFormat;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaQueueItem;

/**
 * Talar om för Chromecast att IPTV-strömmar (HLS) består av MPEG-TS-delar och om det är live.
 * Utan detta gissar Chromecasts standardspelare fel format och blir stående på den blå cast-skärmen.
 */
public class IptvCastConverter implements MediaItemConverter {
    private final DefaultMediaItemConverter base = new DefaultMediaItemConverter();
    public static volatile boolean live = false;

    @Override public MediaQueueItem toMediaQueueItem(MediaItem mediaItem) {
        MediaQueueItem q = base.toMediaQueueItem(mediaItem);
        MediaInfo in = q.getMedia();
        if (in == null) return q;
        String mime = in.getContentType();
        MediaInfo.Builder b = new MediaInfo.Builder(in.getContentId())
            .setContentType(mime)
            .setStreamType(live ? MediaInfo.STREAM_TYPE_LIVE : MediaInfo.STREAM_TYPE_BUFFERED)
            .setMetadata(in.getMetadata())
            .setCustomData(in.getCustomData());
        if (in.getContentUrl() != null) b.setContentUrl(in.getContentUrl());
        if (MimeTypes.APPLICATION_M3U8.equals(mime)) {
            b.setHlsSegmentFormat(HlsSegmentFormat.TS);
            b.setHlsVideoSegmentFormat(HlsVideoSegmentFormat.MPEG2_TS);
        }
        return new MediaQueueItem.Builder(b.build()).setAutoplay(true).build();
    }

    @Override public MediaItem toMediaItem(MediaQueueItem mediaQueueItem) {
        return base.toMediaItem(mediaQueueItem);
    }
}
