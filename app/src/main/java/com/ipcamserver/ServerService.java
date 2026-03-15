package com.ipcamserver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import java.io.IOException;

/**
 * Foreground service that keeps the web server alive when the app goes to the background.
 */
public class ServerService extends Service {

    private static final String CHANNEL_ID = "ipcam_server";
    private static final int NOTIF_ID = 1;

    private WebServer server;
    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        ServerService getService() {
            return ServerService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());

        if (server == null) {
            try {
                server = new WebServer(getApplicationContext());
                server.start();
            } catch (IOException e) {
                stopSelf();
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (server != null) {
            server.stop();
            server = null;
        }
        super.onDestroy();
    }

    public WebServer getServer() {
        return server;
    }

    // ---------------------------------------------------------------- helpers

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "IP Cam Server",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("IP Camera Server is running");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent activityIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, activityIntent,
                PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("IP Cam Server Running")
                .setContentText("Tap to open")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentIntent(pi)
                .build();
    }
}
