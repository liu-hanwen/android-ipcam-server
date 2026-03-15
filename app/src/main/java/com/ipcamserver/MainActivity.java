package com.ipcamserver;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import android.app.Activity;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public class MainActivity extends Activity {

    private TextView tvStatus;
    private TextView tvCameraUrl;
    private TextView tvMonitorUrl;
    private Button btnToggle;
    private Button btnCopyCam;
    private Button btnCopyMon;

    private ServerService serverService;
    private boolean bound = false;
    private boolean running = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable statusUpdater = new Runnable() {
        @Override
        public void run() {
            updateStatus();
            handler.postDelayed(this, 1000);
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            serverService = ((ServerService.LocalBinder) service).getService();
            bound = true;
            running = true;
            updateStatus();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
            running = false;
            serverService = null;
            updateStatus();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus    = findViewById(R.id.tvStatus);
        tvCameraUrl = findViewById(R.id.tvCameraUrl);
        tvMonitorUrl = findViewById(R.id.tvMonitorUrl);
        btnToggle   = findViewById(R.id.btnToggle);
        btnCopyCam  = findViewById(R.id.btnCopyCam);
        btnCopyMon  = findViewById(R.id.btnCopyMon);

        String base = "http://" + getLocalIp() + ":" + WebServer.PORT;
        String camUrl = base + "/camera";
        String monUrl = base + "/monitor";

        tvCameraUrl.setText(camUrl);
        tvMonitorUrl.setText(monUrl);

        btnToggle.setOnClickListener(v -> {
            if (running) {
                stopServer();
            } else {
                startServer();
            }
        });

        btnCopyCam.setOnClickListener(v -> copyToClipboard(camUrl));
        btnCopyMon.setOnClickListener(v -> copyToClipboard(monUrl));
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Re-bind if service is already running
        Intent intent = new Intent(this, ServerService.class);
        bindService(intent, connection, 0 /* don't auto-create */);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (bound) {
            unbindService(connection);
            bound = false;
        }
        handler.removeCallbacks(statusUpdater);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(statusUpdater);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(statusUpdater);
    }

    // ---------------------------------------------------------------- actions

    private void startServer() {
        Intent intent = new Intent(this, ServerService.class);
        startForegroundService(intent);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
        running = true;
        btnToggle.setText(R.string.stop_server);
        tvStatus.setText(R.string.status_running);
    }

    private void stopServer() {
        if (bound) {
            unbindService(connection);
            bound = false;
        }
        Intent intent = new Intent(this, ServerService.class);
        stopService(intent);
        running = false;
        serverService = null;
        btnToggle.setText(R.string.start_server);
        tvStatus.setText(R.string.status_stopped);
    }

    private void updateStatus() {
        if (!running || serverService == null) {
            tvStatus.setText(R.string.status_stopped);
            btnToggle.setText(R.string.start_server);
            return;
        }
        WebServer ws = serverService.getServer();
        if (ws == null) return;
        String cam = ws.isCameraConnected() ? getString(R.string.camera_connected)
                : getString(R.string.camera_not_connected);
        int monitors = ws.getMonitorCount();
        tvStatus.setText(getString(R.string.status_running_detail, cam, monitors));
        btnToggle.setText(R.string.stop_server);
    }

    // ---------------------------------------------------------------- helpers

    private void copyToClipboard(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("url", text));
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
    }

    String getLocalIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return "127.0.0.1";
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) { /* fall through */ }
        return "127.0.0.1";
    }
}
