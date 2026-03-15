# android-ipcam-server

一个将 Android 设备作为局域网 **IP 摄像头中继服务器** 的应用。

## 工作原理

```
浏览器（摄像设备）──WS──► Android 服务器 ──WS──► 浏览器（监控设备）
   getUserMedia()          端口 8080              <img> JPEG 帧
```

1. **摄像页面**（`/camera`）——在任意想用作摄像头的设备（手机、电脑等）上打开。
   浏览器通过 `getUserMedia` 采集摄像头画面，将其编码为 JPEG 帧并经 WebSocket 推送到 Android 服务器。

2. **监控页面**（`/monitor`）——在任意想观看直播的设备上打开。
   通过 WebSocket 接收 JPEG 帧并实时显示。

Android 应用本身只负责转发数据，不具备独立摄像功能。

## 快速开始

1. 将 APK 安装到已连接局域网 Wi-Fi 的 Android 设备上（直接下载见下方）。
2. 点击 **启动服务器**。
3. 应用将显示两个地址，例如 `http://192.168.1.100:8080/camera` 和 `.../monitor`。
4. 在摄像设备的浏览器中打开摄像地址，点击 **开始推流**。
5. 在任意其他设备上打开监控地址即可观看实时画面。

> **注意：** 大多数浏览器在非 localhost 来源下需要 HTTPS 才能使用 `getUserMedia`。
> 如需在局域网中不使用 HTTPS：
> * **Chrome / Edge：** 访问 `chrome://flags/#unsafely-treat-insecure-origin-as-secure`，
>   添加服务器地址（如 `http://192.168.1.100:8080`），启用后重启浏览器。
> * **Firefox：** 打开 `about:config` → 将 `media.devices.insecure.enabled` 设为 `true`。

## 下载安装

已构建好的 Release APK 可直接下载安装：

**[📥 下载 ipcamserver-release.apk](releases/ipcamserver-release.apk)**

> 由于使用的是自签名证书，安装时 Android 系统会提示"未知来源"，请在手机设置中允许安装来自未知来源的应用。

## 自行构建

环境要求：Android SDK（API 34）、JDK 17+。

```bash
./gradlew assembleRelease
```

生成的 APK 位于 `app/build/outputs/apk/release/app-release.apk`。

## 项目结构

```
app/src/main/
  java/com/ipcamserver/
    MainActivity.java    – UI：启动/停止服务器、显示地址
    ServerService.java   – 前台服务，保持服务器持续运行
    WebServer.java       – 基于 NanoHTTPD/NanoWSD 的 HTTP + WebSocket 中继服务器
  assets/
    camera.html          – 浏览器摄像推流页面
    monitor.html         – 浏览器监控查看页面
  res/
    layout/activity_main.xml
    values/strings.xml
```

## 依赖

* [NanoHTTPD](https://github.com/NanoHttpd/nanohttpd) `nanohttpd-websocket:2.3.1`（Maven Central）
