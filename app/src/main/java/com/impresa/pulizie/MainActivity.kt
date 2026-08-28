package com.impresa.pulizie

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.room.*
import kotlinx.coroutines.launch

@Entity(tableName = "interventi")
data class Intervento(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val cliente: String,
    val note: String
)

@Dao
interface InterventoDao {
    @Query("SELECT * FROM interventi")
    suspend fun getAll(): List<Intervento>

    @Insert
    suspend fun insert(intervento: Intervento)

    @Delete
    suspend fun delete(intervento: Intervento)
}

@Database(entities = [Intervento::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun interventoDao(): InterventoDao
}

class MainActivity : ComponentActivity() {
    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "pulizie_db").allowMainThreadQueries().build()
        setContent {
            MaterialTheme {
                Surface {
                    AppContent(database)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContent(database: AppDatabase) {
    val scope = rememberCoroutineScope()
    var interventi by remember { mutableStateOf(emptyList<Intervento>(*) }
    var cliente by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        interventi = database.interventoDao().getAll()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("App Pulizie") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                if (cliente.isNotBlank()) {
                    scope.launch {
                        database.interventoDao().insert(Intervento(cliente = cliente, note = note))
                        interventi = database.interventoDao().getAll()
                        cliente = ""
                        note = ""
                    }
                }
            }) {
                Icon(Icons.Default.Add, contentDescription = "Aggiungi")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            OutlinedTextField(value = cliente, onValueChange = { cliente = it }, label = { Text("Cliente / Cantiere") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(interventi) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(item.cliente, style = MaterialTheme.typography.titleMedium)
                                Text(item.note, style = MaterialTheme.typography.bodyMedium)
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    database.interventoDao().delete(item)
                                    interventi = database.interventoDao().getAll()
                                }
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
