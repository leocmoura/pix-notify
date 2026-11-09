package com.leocmoura.pixnotify

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class EnviarWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = Config.url(applicationContext)
        val token = Config.token(applicationContext)
        if (url.isBlank() || token.isBlank()) {
            Log.w(TAG, "URL ou token não configurados")
            return@withContext Result.failure()
        }

        val json = JSONObject()
            .put("titulo", inputData.getString("titulo").orEmpty())
            .put("texto", inputData.getString("texto").orEmpty())
            .toString()

        val request = Request.Builder()
            .url("$url/enviar")
            .addHeader("Authorization", "Bearer $token")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            cliente.newCall(request).execute().use { resp ->
                Log.d(TAG, "Resposta do servidor: ${resp.code}")
                when {
                    resp.isSuccessful -> Result.success()
                    resp.code == 400 || resp.code == 401 -> Result.failure()
                    else -> tentarDeNovo()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha de rede: ${e.message}")
            tentarDeNovo()
        }
    }

    private fun tentarDeNovo() =
        if (runAttemptCount < MAX_TENTATIVAS) Result.retry() else Result.failure()

    companion object {
        private const val TAG = "PIXNOTIFY"
        private const val MAX_TENTATIVAS = 6

        // Timeouts longos: o Render gratuito leva ~20 s para acordar
        private val cliente = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build()
    }
}

object Envio {
    fun enfileirar(ctx: Context, titulo: String, texto: String) {
        val pedido = OneTimeWorkRequestBuilder<EnviarWorker>()
            .setInputData(workDataOf("titulo" to titulo, "texto" to texto))
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(ctx).enqueue(pedido)
    }
}