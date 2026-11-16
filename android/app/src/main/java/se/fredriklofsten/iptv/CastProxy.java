package se.fredriklofsten.iptv;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Liten mellanserver på telefonen för Chromecast.
 * Chromecast hämtar strömmen via telefonen i stället för direkt från leverantören. Det löser tre vanliga fel:
 *  - leverantören saknar CORS-huvuden (krävs av Chromecasts spelare för HLS),
 *  - leverantören blockerar Chromecasts egen webbläsar-identitet (vi skickar en mediaspelar-identitet),
 *  - omdirigeringar och relativa adresser i HLS-listor (vi skriver om dem så att allt går via telefonen).
 */
public class CastProxy {
    private static CastProxy instance;
    private ServerSocket server;
    private String base;
    public volatile String lastError;
    public volatile int requests = 0;
    public volatile String lastInfo = "";
    public String base() { return base; }
    private static final String UA = "VLC/3.0.20 LibVLC/3.0.20";

    public static synchronized CastProxy get() {
        if (instance == null || instance.server == null || instance.server.isClosed()) {
            instance = new CastProxy();
            try { instance.start(); } catch (Exception e) { instance.base = null; }
        }
        return instance;
    }

    private void start() throws IOException {
        String ip = localIp();
        if (ip == null) throw new IOException("ingen lokal IP-adress");
        server = new ServerSocket(0);
        base = "http://" + ip + ":" + server.getLocalPort();
        Thread t = new Thread(() -> {
            while (!server.isClosed()) {
                try { Socket s = server.accept(); new Thread(() -> handle(s), "castproxy-req").start(); }
                catch (IOException e) { /* stängd */ }
            }
        }, "castproxy");
        t.setDaemon(true);
        t.start();
    }

    /** Adress som Chromecast ska använda, eller originalet om servern inte kunde startas. */
    public String wrap(String url) {
        if (base == null || url == null || !url.startsWith("http")) return url;
        String name = "media";
        String u = url.toLowerCase(); int q = u.indexOf('?'); if (q > 0) u = u.substring(0, q);
        if (u.contains(".m3u8")) name = "index.m3u8"; else if (u.endsWith(".mp4")) name = "video.mp4";
        else if (u.endsWith(".mkv")) name = "video.mkv"; else if (u.endsWith(".ts")) name = "video.ts";
        return base + "/" + name + "?u=" + enc(url);
    }

    private static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    private static String localIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                String n = ni.getName().toLowerCase();
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress() && (n.startsWith("wlan") || n.startsWith("eth") || n.startsWith("ap") || n.startsWith("swlan")))
                        return a.getHostAddress();
                }
            }
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces()))
                for (InetAddress a : Collections.list(ni.getInetAddresses()))
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) return a.getHostAddress();
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces()))
                if (ni.isUp() && !ni.isLoopback())
                    for (InetAddress a : Collections.list(ni.getInetAddresses()))
                        if (a instanceof Inet4Address) return a.getHostAddress();
        } catch (Exception ignored) { }
        return null;
    }

    private void handle(Socket s) {
        try (Socket sock = s) {
            sock.setSoTimeout(30000);
            requests++;
            InputStream in = sock.getInputStream();
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.ISO_8859_1));
            String line = r.readLine();
            if (line == null) return;
            String[] parts = line.split(" ");
            String method = parts[0], path = parts.length > 1 ? parts[1] : "/";
            Map<String, String> headers = new HashMap<>();
            String h;
            while ((h = r.readLine()) != null && !h.isEmpty()) {
                int c = h.indexOf(':'); if (c > 0) headers.put(h.substring(0, c).trim().toLowerCase(), h.substring(c + 1).trim());
            }
            OutputStream out = sock.getOutputStream();
            if ("OPTIONS".equals(method)) {
                write(out, "HTTP/1.1 204 No Content\r\n" + cors() + "Content-Length: 0\r\nConnection: close\r\n\r\n");
                return;
            }
            int qi = path.indexOf("u=");
            if (qi < 0) { write(out, "HTTP/1.1 404 Not Found\r\n" + cors() + "Content-Length: 0\r\nConnection: close\r\n\r\n"); return; }
            String target = URLDecoder.decode(path.substring(qi + 2).split("&")[0], "UTF-8");
            forward(out, method, target, headers.get("range"));
        } catch (Exception e) {
            lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    private void forward(OutputStream out, String method, String target, String range) throws IOException {
        HttpURLConnection c = null;
        String url = target;
        int status = 0;
        for (int hop = 0; hop < 6; hop++) {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(20000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "*/*");
            if (range != null) c.setRequestProperty("Range", range);
            if ("HEAD".equals(method)) c.setRequestMethod("HEAD");
            status = c.getResponseCode();
            if (status >= 300 && status < 400 && c.getHeaderField("Location") != null) {
                url = new URL(new URL(url), c.getHeaderField("Location")).toString();
                c.disconnect();
                continue;
            }
            break;
        }
        if (status >= 400) lastError = "leverantören svarade " + status;
        lastInfo = status + " " + c.getContentType();
        String type = c.getContentType();
        String lower = url.toLowerCase();
        boolean playlist = (type != null && type.toLowerCase().contains("mpegurl")) || lower.contains(".m3u8");
        StringBuilder head = new StringBuilder();
        head.append("HTTP/1.1 ").append(status).append(status == 206 ? " Partial Content" : status < 300 ? " OK" : " Error").append("\r\n").append(cors()).append("Connection: close\r\n");
        InputStream body = null;
        try { body = status >= 400 ? c.getErrorStream() : c.getInputStream(); } catch (IOException ignored) { }

        if (playlist && body != null && !"HEAD".equals(method)) {
            String text = readAll(body);
            byte[] bytes = rewrite(text, url).getBytes(StandardCharsets.UTF_8);
            head.append("Content-Type: application/vnd.apple.mpegurl\r\nCache-Control: no-cache\r\nContent-Length: ").append(bytes.length).append("\r\n\r\n");
            write(out, head.toString());
            out.write(bytes);
            out.flush();
            c.disconnect();
            return;
        }
        if (type == null || type.startsWith("application/octet-stream") || type.startsWith("text/")) type = guessType(lower);
        head.append("Content-Type: ").append(type).append("\r\n");
        String len = c.getHeaderField("Content-Length"); if (len != null) head.append("Content-Length: ").append(len).append("\r\n");
        String cr = c.getHeaderField("Content-Range"); if (cr != null) head.append("Content-Range: ").append(cr).append("\r\n");
        head.append("Accept-Ranges: ").append(c.getHeaderField("Accept-Ranges") != null ? c.getHeaderField("Accept-Ranges") : "bytes").append("\r\n\r\n");
        write(out, head.toString());
        if (body != null && !"HEAD".equals(method)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            try { while ((n = body.read(buf)) > 0) out.write(buf, 0, n); }
            catch (IOException e) { /* Chromecast stängde anslutningen (sökning/stopp) */ }
            finally { try { body.close(); } catch (IOException ignored) { } }
        }
        out.flush();
        c.disconnect();
    }

    private static String guessType(String u) {
        int q = u.indexOf('?'); if (q > 0) u = u.substring(0, q);
        if (u.endsWith(".ts")) return "video/mp2t";
        if (u.endsWith(".mkv")) return "video/x-matroska";
        if (u.endsWith(".aac")) return "audio/aac";
        if (u.endsWith(".vtt")) return "text/vtt";
        return "video/mp4";
    }

    private static final Pattern URI_ATTR = Pattern.compile("URI=\"([^\"]+)\"");

    /** Skriver om alla adresser i en HLS-lista så att de också går via telefonen. */
    private String rewrite(String text, String from) {
        StringBuilder sb = new StringBuilder();
        for (String raw : text.split("\r?\n")) {
            String l = raw.trim();
            if (l.isEmpty()) { sb.append('\n'); continue; }
            if (l.startsWith("#")) {
                Matcher m = URI_ATTR.matcher(l);
                StringBuffer b = new StringBuffer();
                while (m.find()) m.appendReplacement(b, Matcher.quoteReplacement("URI=\"" + wrap(abs(from, m.group(1))) + "\""));
                m.appendTail(b);
                sb.append(b).append('\n');
            } else sb.append(wrap(abs(from, l))).append('\n');
        }
        return sb.toString();
    }

    private static String abs(String base, String rel) {
        try { return new URL(new URL(base), rel).toString(); } catch (Exception e) { return rel; }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[16384]; int n;
        while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
        in.close();
        return b.toString("UTF-8");
    }

    private static String cors() {
        return "Access-Control-Allow-Origin: *\r\nAccess-Control-Allow-Headers: *\r\nAccess-Control-Allow-Methods: GET, HEAD, OPTIONS\r\nAccess-Control-Expose-Headers: Content-Length, Content-Range, Accept-Ranges\r\n";
    }

    private static void write(OutputStream out, String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.ISO_8859_1));
    }
}
