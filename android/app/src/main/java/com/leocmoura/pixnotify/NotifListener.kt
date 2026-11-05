package com.leocmoura.pixnotify

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotifListener : NotificationListenerService() {

    override fun onListenerConnected() {
        Log.d(TAG, "Listener conectado")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return

        val extras = sbn.notification.extras
        val titulo = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val texto = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val textoLongo = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()

        Log.d(TAG, "pacote=${sbn.packageName} | titulo=$titulo | texto=$texto | longo=$textoLongo")
    }

    companion object {
        private const val TAG = "PIXNOTIFY"
    }
}