package com.leocmoura.pixnotify

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class NotifListener : NotificationListenerService() {

    private var ultimaAssinatura = ""
    private var ultimoEnvio = 0L

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val nomeApp = Config.APPS[sbn.packageName] ?: return

        val notif = sbn.notification
        if (notif.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val extras = notif.extras
        val titulo = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val texto = listOf(
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
        ).firstOrNull { !it.isNullOrBlank() }.orEmpty()

        if (titulo.isBlank() && texto.isBlank()) return

        if (Config.PALAVRAS_CHAVE.isNotEmpty()) {
            val conteudo = "$titulo $texto".lowercase()
            if (Config.PALAVRAS_CHAVE.none { conteudo.contains(it.lowercase()) }) return
        }

        // Evita enviar a mesma notificação duas vezes em poucos segundos
        val assinatura = "${sbn.packageName}|$titulo|$texto"
        val agora = System.currentTimeMillis()
        if (assinatura == ultimaAssinatura && agora - ultimoEnvio < 5_000) return
        ultimaAssinatura = assinatura
        ultimoEnvio = agora

        Log.d(TAG, "Encaminhando notificação de ${sbn.packageName}")
        val tituloFinal = if (titulo.isBlank()) nomeApp else "$nomeApp: $titulo"
        Envio.enfileirar(applicationContext, tituloFinal, texto)
    }

    companion object {
        private const val TAG = "PIXNOTIFY"
    }
}