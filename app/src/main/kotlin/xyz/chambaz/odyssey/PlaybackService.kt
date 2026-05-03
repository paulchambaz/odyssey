package xyz.chambaz.odyssey

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder

class PlaybackService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notif = Notification.Builder(this, NOTIF_CHANNEL_PLAYBACK_ID)
            .setSmallIcon(R.drawable.ic_odyssey)
            .setContentTitle(intent?.getStringExtra("title") ?: "")
            .setOngoing(true)
            .build()
        startForeground(NOTIF_ID_PLAYBACK, notif)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
}
