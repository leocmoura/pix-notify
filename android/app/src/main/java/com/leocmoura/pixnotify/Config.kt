package com.leocmoura.pixnotify

import android.content.Context

object Config {
    // Apps monitorados: pacote -> nome exibido no grupo
    val APPS = mapOf(
        "br.com.bb.android" to "Banco do Brasil",
        "com.mercadopago.wallet" to "Mercado Pago",
    )

    // Vazio = encaminha tudo dos apps acima. Para filtrar, ex.: listOf("pix", "recebeu")
    val PALAVRAS_CHAVE: List<String> = emptyList()

    private fun prefs(c: Context) =
        c.getSharedPreferences("pixnotify", Context.MODE_PRIVATE)

    fun url(c: Context): String = prefs(c).getString("url", "").orEmpty()
    fun token(c: Context): String = prefs(c).getString("token", "").orEmpty()

    fun salvar(c: Context, url: String, token: String) {
        prefs(c).edit()
            .putString("url", url.trim().trimEnd('/'))
            .putString("token", token.trim())
            .apply()
    }
}