package com.impresa.pulizie

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppContent()
                }
            }
        }
    }
}

data class InterventoModel(
    val id: Long = System.currentTimeMillis(),
    val cliente: String,
    val note: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContent() {
    val context = LocalContext.current
    val pref = context.getSharedPreferences("pulizie_prefs", Context.MODE_PRIVATE)
    
    var cliente by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var interventi by remember { mutableStateOf(loadInterventi(pref)) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Gestione Pulizie") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = cliente,
                onValueChange = { cliente = it },
                label = { Text("Cliente / Cantiere") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note / Mansioni Svolte") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    if (cliente.isNotBlank()) {
                        val nuovo = InterventoModel(cliente = cliente, note = note)
                        val nuovaLista = listOf(nuovo) + interventi
                        interventi = nuovaLista
                        saveInterventi(pref, nuovaLista)
                        cliente = ""
                        note = ""
                    } else {
                        Toast.makeText(context, "Inserisci il nome del cliente", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Salva Intervento")
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Storico Interventi", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(interventi, key = { it.id }) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(item.cliente, style = MaterialTheme.typography.titleSmall)
                            if (item.note.isNotBlank()) {
                                Text(item.note, style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                IconButton(onClick = {
                                    val testo = "Report Pulizie - ${item.cliente}\nNote: ${item.note}"
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, testo)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Condividi via"))
                                }) {
                                    Icon(Icons.Default.Share, contentDescription = "Invia")
                                }
                                IconButton(onClick = {
                                    val nuovaLista = interventi.filter { it.id != item.id }
                                    interventi = nuovaLista
                                    saveInterventi(pref, nuovaLista)
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Elimina")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun saveInterventi(pref: android.content.SharedPreferences, list: List<InterventoModel>) {
    val raw = list.joinToString(";;;") { "${it.id}|||${it.cliente}|||${it.note}" }
    pref.edit().putString("data", raw).apply()
}

fun loadInterventi(pref: android.content.SharedPreferences): List<InterventoModel> {
    val raw = pref.getString("data", "") ?: ""
    if (raw.isBlank()) return emptyList()
    return raw.split(";;;").mapNotNull {
        val parts = it.split("|||")
        if (parts.size == 3) {
            InterventoModel(parts[0].toLongOrNull() ?: 0L, parts[1], parts[2])
        } else null
    }
}
