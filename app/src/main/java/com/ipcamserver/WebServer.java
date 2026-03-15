package com.ipcamserver;

import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicReference;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoWSD;
import fi.iki.elonen.NanoWSD.WebSocketFrame.CloseCode;

/**
 * HTTP + WebSocket relay server.
 *
 * HTTP routes:
 *   GET /           → simple index with links
 *   GET /camera     → camera.html  (browser streams its camera here)
 *   GET /monitor    → monitor.html (browser watches the stream here)
 *
 * WebSocket routes:
 *   WS /ws/camera   → one camera sender; its binary JPEG frames are relayed to all monitors
 *   WS /ws/monitor  → any number of monitor viewers; receive forwarded JPEG frames
 */
public class WebServer extends NanoWSD {

    static final int PORT = 8080;

    private final Context context;
    /** All currently connected monitor sockets. Thread-safe iteration on broadcast. */
    private final Set<WebSocket> monitors = new CopyOnWriteArraySet<>();
    /** The active camera socket (only one at a time; last one wins). */
    private final AtomicReference<WebSocket> cameraRef = new AtomicReference<>();

    public WebServer(Context context) throws IOException {
        super(PORT);
        this.context = context;
    }

    // ------------------------------------------------------------------ HTTP

    @Override
    protected Response serveHttp(IHTTPSession session) {
        String uri = session.getUri();
        switch (uri) {
            case "/":
                return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8",
                        "<html><body style='font-family:sans-serif;padding:20px'>"
                                + "<h2>IP Cam Server</h2>"
                                + "<p><a href='/camera'>Camera page</a> – open on the device you want to use as a camera</p>"
                                + "<p><a href='/monitor'>Monitor page</a> – open on the device you want to watch the stream</p>"
                                + "</body></html>");
            case "/camera":
            case "/camera.html":
                return serveAsset("camera.html", "text/html; charset=utf-8");
            case "/monitor":
            case "/monitor.html":
                return serveAsset("monitor.html", "text/html; charset=utf-8");
            default:
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found");
        }
    }

    private Response serveAsset(String filename, String mimeType) {
        try {
            InputStream is = context.getAssets().open(filename);
            return newChunkedResponse(Response.Status.OK, mimeType, is);
        } catch (IOException e) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.getMessage());
        }
    }

    // --------------------------------------------------------------- WebSocket

    @Override
    protected WebSocket openWebSocket(IHTTPSession handshake) {
        String uri = handshake.getUri();
        if ("/ws/camera".equals(uri)) {
            return new CameraSocket(handshake);
        } else if ("/ws/monitor".equals(uri)) {
            return new MonitorSocket(handshake);
        }
        return new RejectSocket(handshake);
    }

    // ---------------------------------------------------------------- helpers

    /** Number of currently connected monitors (for UI display). */
    public int getMonitorCount() {
        return monitors.size();
    }

    /** Whether a camera is currently connected. */
    public boolean isCameraConnected() {
        return cameraRef.get() != null;
    }

    // ---------------------------------------------------------- inner classes

    /** Receives JPEG frames from the camera browser and broadcasts to all monitors. */
    private class CameraSocket extends WebSocket {
        CameraSocket(IHTTPSession handshake) {
            super(handshake);
        }

        @Override
        protected void onOpen() {
            cameraRef.set(this);
        }

        @Override
        protected void onClose(CloseCode code, String reason, boolean initiatedByRemote) {
            cameraRef.compareAndSet(this, null);
        }

        @Override
        protected void onMessage(WebSocketFrame message) {
            byte[] payload = message.getBinaryPayload();
            if (payload == null || payload.length == 0) return;
            for (WebSocket monitor : monitors) {
                try {
                    monitor.send(payload);
                } catch (IOException e) {
                    monitors.remove(monitor);
                }
            }
        }

        @Override protected void onPong(WebSocketFrame pong) {}
        @Override protected void onException(IOException exception) {
            cameraRef.compareAndSet(this, null);
        }
    }

    /** Receives forwarded JPEG frames and delivers them to the monitor browser. */
    private class MonitorSocket extends WebSocket {
        MonitorSocket(IHTTPSession handshake) {
            super(handshake);
        }

        @Override
        protected void onOpen() {
            monitors.add(this);
        }

        @Override
        protected void onClose(CloseCode code, String reason, boolean initiatedByRemote) {
            monitors.remove(this);
        }

        @Override protected void onMessage(WebSocketFrame message) {}
        @Override protected void onPong(WebSocketFrame pong) {}
        @Override protected void onException(IOException exception) {
            monitors.remove(this);
        }
    }

    /** Immediately closes WebSocket connections to unknown paths. */
    private static class RejectSocket extends WebSocket {
        RejectSocket(IHTTPSession handshake) {
            super(handshake);
        }

        @Override
        protected void onOpen() {
            try {
                close(CloseCode.InvalidFramePayloadData, "Unknown path", false);
            } catch (IOException e) { /* ignore */ }
        }

        @Override protected void onClose(CloseCode code, String reason, boolean initiatedByRemote) {}
        @Override protected void onMessage(WebSocketFrame message) {}
        @Override protected void onPong(WebSocketFrame pong) {}
        @Override protected void onException(IOException exception) {}
    }
}
