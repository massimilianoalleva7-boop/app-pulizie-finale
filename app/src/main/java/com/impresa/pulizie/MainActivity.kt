package com.impresa.pulizie

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private val listaClienti = ArrayList<String>()
    private val interventiOggi = ArrayList<String>()
    
    private var secondsElapsed = 0
    private var isRunning = false
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var runnable: Runnable

    private lateinit var adapterSpinner: ArrayAdapter<String>
    private lateinit var adapterInterventi: ArrayAdapter<String>
    private lateinit var txtDataOggi: TextView
    private lateinit var txtTimer: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pref = getSharedPreferences("pulizie_app_db", Context.MODE_PRIVATE)
        loadClienti(pref)

        val oggiStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        loadInterventiGiorno(pref, oggiStr)

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        txtDataOggi = TextView(this).apply {
            text = "📅 Data: $oggiStr"
            textSize = 18f
            setPadding(0, 0, 0, 16)
        }

        // --- SEZIONE SELEZIONE E GESTIONE CLIENTE ---
        val clienteLabel = TextView(this).apply { text = "Seleziona Cliente / Cantiere:" }
        val spinnerLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        
        val spinnerClienti = Spinner(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        adapterSpinner = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listaClienti)
        spinnerClienti.adapter = adapterSpinner

        val btnAddCliente = Button(this).apply { text = "+ Nuovo" }
        btnAddCliente.setOnClickListener {
            mostraDialogNuovoCliente(pref)
        }

        spinnerLayout.addView(spinnerClienti)
        spinnerLayout.addView(btnAddCliente)

        // --- SEZIONE NOTE E TIMER ---
        val inputNote = EditText(this).apply {
            hint = "Note / Mansioni svolte (opzionale)"
        }

        txtTimer = TextView(this).apply {
            text = "⏱️ Tempo: 00:00:00"
            textSize = 20f
            setPadding(0, 16, 0, 16)
        }

        val timerLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnStart = Button(this).apply { text = "▶ START" }
        val btnStop = Button(this).apply { text = "⏹ STOP" }
        timerLayout.addView(btnStart)
        timerLayout.addView(btnStop)

        val btnSalva = Button(this).apply { text = "💾 Salva Intervento" }
        val btnReportGiornaliero = Button(this).apply { 
            text = "📤 Invia Report Quotidiano" 
        }

        val listView = ListView(this)
        adapterInterventi = ArrayAdapter(this, android.R.layout.simple_list_item_1, interventiOggi)
        listView.adapter = adapterInterventi

        // --- LOGICA TIMER ---
        runnable = object : Runnable {
            override fun run() {
                if (isRunning) {
                    secondsElapsed++
                    val hrs = secondsElapsed / 3600
                    val mins = (secondsElapsed % 3600) / 60
                    val secs = secondsElapsed % 60
                    txtTimer.text = String.format("⏱️ Tempo: %02d:%02d:%02d", hrs, mins, secs)
                    handler.postDelayed(this, 1000)
                }
            }
        }

        btnStart.setOnClickListener {
            if (!isRunning) {
                isRunning = true
                handler.post(runnable)
            }
        }

        btnStop.setOnClickListener {
            isRunning = false
        }

        // --- SALVATAGGIO INTERVENTO ---
        btnSalva.setOnClickListener {
            val clienteSelezionato = spinnerClienti.selectedItem?.toString() ?: ""
            val note = inputNote.text.toString()
            val tempoStr = txtTimer.text.toString().replace("⏱️ Tempo: ", "")

            if (clienteSelezionato.isNotBlank()) {
                val oraCorrente = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                val riga = "[$oraCorrente] $clienteSelezionato | Durata: $tempoStr\nNote: ${if (note.isBlank()) "Nessuna" else note}"
                
                interventiOggi.add(0, riga)
                adapterInterventi.notifyDataSetChanged()
                saveInterventiGiorno(pref, oggiStr)

                inputNote.text.clear()
                isRunning = false
                secondsElapsed = 0
                txtTimer.text = "⏱️ Tempo: 00:00:00"
                Toast.makeText(this, "Intervento registrato!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Aggiungi prima un cliente dal tasto '+ Nuovo'", Toast.LENGTH_SHORT).show()
            }
        }

        // --- INVIO REPORT GIORNALIERO ---
        btnReportGiornaliero.setOnClickListener {
            if (interventiOggi.isNotEmpty()) {
                val testoReport = StringBuilder()
                testoReport.append("📋 REPORT PULIZIE DEL $oggiStr\n\n")
                for (item in interventiOggi) {
                    testoReport.append("• ").append(item).append("\n---\n")
                }

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, testoReport.toString())
                }
                startActivity(Intent.createChooser(intent, "Invia Report Quotidiano via"))
            } else {
                Toast.makeText(this, "Nessun intervento registrato per oggi!", Toast.LENGTH_SHORT).show()
            }
        }

        // Costruzione Layout
        mainLayout.addView(txtDataOggi)
        mainLayout.addView(clienteLabel)
        mainLayout.addView(spinnerLayout)
        mainLayout.addView(inputNote)
        mainLayout.addView(txtTimer)
        mainLayout.addView(timerLayout)
        mainLayout.addView(btnSalva)
        mainLayout.addView(btnReportGiornaliero)
        mainLayout.addView(listView)

        setContentView(mainLayout)
    }

    private fun mostraDialogNuovoCliente(pref: android.content.SharedPreferences) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Nuovo Cliente / Cantiere")

        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = "Es. Condominio Rossi / Bar Roma"
        }
        builder.setView(input)

        builder.setPositiveButton("Salva") { _, _ ->
            val nome = input.text.toString().trim()
            if (nome.isNotBlank() && !listaClienti.contains(nome)) {
                listaClienti.add(nome)
                adapterSpinner.notifyDataSetChanged()
                saveClienti(pref)
            }
        }
        builder.setNegativeButton("Annulla") { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    private fun saveClienti(pref: android.content.SharedPreferences) {
        pref.edit().putStringSet("clienti_db", HashSet(listaClienti)).apply()
    }

    private fun loadClienti(pref: android.content.SharedPreferences) {
        val set = pref.getStringSet("clienti_db", null)
        listaClienti.clear()
        if (set != null) {
            listaClienti.addAll(set)
        } else {
            listaClienti.addAll(listOf("Cliente Esempio A", "Cliente Esempio B"))
        }
    }

    private fun saveInterventiGiorno(pref: android.content.SharedPreferences, data: String) {
        pref.edit().putStringSet("interventi_$data", HashSet(interventiOggi)).apply()
    }

    private fun loadInterventiGiorno(pref: android.content.SharedPreferences, data: String) {
        val set = pref.getStringSet("interventi_$data", null)
        interventiOggi.clear()
        if (set != null) {
            interventiOggi.addAll(set)
        }
    }
}
