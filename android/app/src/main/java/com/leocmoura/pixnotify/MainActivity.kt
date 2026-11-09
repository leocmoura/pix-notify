package com.leocmoura.pixnotify

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat

class MainActivity : ComponentActivity() {

    private var permissaoAtiva by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Tela(permissaoAtiva) } }
    }

    override fun onResume() {
        super.onResume()
        permissaoAtiva = NotificationManagerCompat
            .getEnabledListenerPackages(this).contains(packageName)
    }
}

@Composable
fun Tela(permissaoAtiva: Boolean) {
    val context = LocalContext.current
    var url by remember { mutableStateOf(Config.url(context)) }
    var token by remember { mutableStateOf(Config.token(context)) }
    var aviso by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("pix-notify", style = MaterialTheme.typography.headlineMedium)

        Text(
            if (permissaoAtiva) "Leitura de notificações: ativa"
            else "Leitura de notificações: desativada"
        )
        Button(onClick = {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }) { Text("Permissão de notificações") }

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("URL do servidor") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Token") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Button(onClick = {
            Config.salvar(context, url, token)
            aviso = "Configuração salva"
        }) { Text("Salvar") }

        Button(onClick = {
            Config.salvar(context, url, token)
            Envio.enfileirar(context, "pix-notify", "Mensagem de teste do aplicativo")
            aviso = "Teste colocado na fila de envio"
        }) { Text("Enviar teste") }

        if (aviso.isNotEmpty()) Text(aviso)
    }
}