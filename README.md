# android-ipcam-server

An Android app that acts as a local-network **IP camera relay server**.

## How it works

```
Browser (Camera device) ──WS──► Android server ──WS──► Browser (Monitor device)
        getUserMedia()           port 8080              <img> JPEG frames
```

1. **Camera page** (`/camera`) – open on any device (phone, laptop) you want to use as a camera.
   The browser captures the camera with `getUserMedia`, encodes JPEG frames, and streams them via WebSocket to the Android server.

2. **Monitor page** (`/monitor`) – open on any device you want to watch the live stream.
   Receives JPEG frames via WebSocket and displays them as a live feed.

The Android app itself just relays data – it has no camera of its own.

## Getting started

1. Install the app on an Android device connected to your local Wi-Fi.
2. Tap **Start Server**.
3. The app shows two URLs, e.g. `http://192.168.1.100:8080/camera` and `.../monitor`.
4. Open the camera URL in the browser on the camera device and tap **Start Streaming**.
5. Open the monitor URL on any other device to watch the feed.

> **Note:** Most browsers require HTTPS for `getUserMedia` on non-localhost origins.
> For LAN use without HTTPS:
> * **Chrome / Edge:** go to `chrome://flags/#unsafely-treat-insecure-origin-as-secure`,
>   add the server origin (e.g. `http://192.168.1.100:8080`), enable and relaunch.
> * **Firefox:** open `about:config` → set `media.devices.insecure.enabled` to `true`.

## Building

Requirements: Android SDK (API 34), JDK 17+.

```bash
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Project structure

```
app/src/main/
  java/com/ipcamserver/
    MainActivity.java    – UI: start/stop server, show URLs
    ServerService.java   – Foreground service keeping the server alive
    WebServer.java       – NanoHTTPD/NanoWSD HTTP + WebSocket relay server
  assets/
    camera.html          – Browser camera streaming page
    monitor.html         – Browser monitor viewing page
  res/
    layout/activity_main.xml
    values/strings.xml
```

## Dependencies

* [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd) `nanohttpd-websocket:2.3.1` (Maven Central)
