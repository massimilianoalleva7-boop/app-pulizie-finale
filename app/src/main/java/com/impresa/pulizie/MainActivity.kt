package com.impresa.pulizie

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private val listaClienti = ArrayList<String>()
    private val interventiOggi = ArrayList<String>()
    
    private var startTimeMillis: Long = 0
    private var elapsedTimeBeforePause: Long = 0
    private var isRunning = false
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var runnable: Runnable

    private lateinit var adapterSpinner: ArrayAdapter<String>
    private lateinit var adapterInterventi: ArrayAdapter<String>
    private lateinit var txtTimer: TextView
    private lateinit var txtOreUomo: TextView
    private lateinit var inputOperatori: EditText
    private lateinit var spinnerClienti: Spinner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pref = getSharedPreferences("pulizie_app_db", Context.MODE_PRIVATE)
        loadClienti(pref)

        val oggiStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        loadInterventiGiorno(pref, oggiStr)

        isRunning = pref.getBoolean("is_running", false)
        startTimeMillis = pref.getLong("start_time", 0L)
        elapsedTimeBeforePause = pref.getLong("elapsed_before_pause", 0L)

        // BLOCCO TASTO INDIETRO SE IL TIMER È IN CORSO
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isRunning) {
                    mostraDialogConfermaUscita()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })

        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val txtDataOggi = TextView(this).apply {
            text = "📅 Data: $oggiStr"
            textSize = 18f
            setPadding(0, 0, 0, 16)
        }

        // --- GESTIONE ANAGRAFICA CLIENTI ---
        val spinnerLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        spinnerClienti = Spinner(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        adapterSpinner = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listaClienti)
        spinnerClienti.adapter = adapterSpinner

        val btnAddCliente = Button(this).apply { text = "+ Nuovo" }
        btnAddCliente.setOnClickListener { mostraDialogNuovoCliente(pref) }

        val btnEditCliente = Button(this).apply { text = "✏️ Modifica" }
        btnEditCliente.setOnClickListener { mostraDialogModificaCliente(pref) }

        spinnerLayout.addView(spinnerClienti)
        spinnerLayout.addView(btnAddCliente)
        spinnerLayout.addView(btnEditCliente)

        inputOperatori = EditText(this).apply {
            hint = "Numero Operatori"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText("1")
        }

        val inputNote = EditText(this).apply { hint = "Note / Mansioni svolte" }

        txtTimer = TextView(this).apply {
            text = "⏱️ Tempo Intervento: 00:00:00"
            textSize = 18f
            setPadding(0, 8, 0, 4)
        }

        txtOreUomo = TextView(this).apply {
            text = "👥 Ore-Uomo Totali: 0,00 ore"
            textSize = 18f
            setPadding(0, 0, 0, 12)
        }

        val timerLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnStart = Button(this).apply { text = "▶ START" }
        val btnStop = Button(this).apply { text = "⏹ STOP" }
        timerLayout.addView(btnStart)
        timerLayout.addView(btnStop)

        val btnSalva = Button(this).apply { text = "💾 Salva Intervento" }
        val btnReportPdf = Button(this).apply { text = "📄 Genera e Invia PDF" }

        val listView = ListView(this)
        adapterInterventi = ArrayAdapter(this, android.R.layout.simple_list_item_1, interventiOggi)
        listView.adapter = adapterInterventi

        // MODIFICA INTERVENTO SALVATO TAPPANDO SULLA LISTA
        listView.setOnItemClickListener { _, _, position, _ ->
            mostraDialogModificaIntervento(position, pref, oggiStr)
        }

        runnable = object : Runnable {
            override fun run() {
                if (isRunning) {
                    aggiornaTimerEOreUomo()
                    handler.postDelayed(this, 1000)
                }
            }
        }

        inputOperatori.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                aggiornaTimerEOreUomo()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnStart.setOnClickListener {
            if (!isRunning) {
                isRunning = true
                startTimeMillis = System.currentTimeMillis()
                salvaStatoTimer(pref)
                
                try {
                    val serviceIntent = Intent(this, TimerService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                handler.post(runnable)
            }
        }

        btnStop.setOnClickListener {
            if (isRunning) {
                isRunning = false
                elapsedTimeBeforePause += System.currentTimeMillis() - startTimeMillis
                salvaStatoTimer(pref)

                try {
                    stopService(Intent(this, TimerService::class.java))
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                handler.removeCallbacks(runnable)
                aggiornaTimerEOreUomo()
            }
        }

        btnSalva.setOnClickListener {
            val cliente = spinnerClienti.selectedItem?.toString() ?: ""
            val note = inputNote.text.toString()
            val numOps = inputOperatori.text.toString().ifBlank { "1" }
            val tempoStr = txtTimer.text.toString().replace("⏱️ Tempo Intervento: ", "")
            val oreUomoStr = txtOreUomo.text.toString().replace("👥 Ore-Uomo Totali: ", "")

            if (cliente.isNotBlank()) {
                val ora = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                val riga = "[$ora] $cliente\nOperatori: $numOps | Durata: $tempoStr | Ore-Uomo: $oreUomoStr\nNote: ${if (note.isBlank()) "Nessuna" else note}"
                
                interventiOggi.add(0, riga)
                adapterInterventi.notifyDataSetChanged()
                saveInterventiGiorno(pref, oggiStr)

                // Reset
                inputNote.text.clear()
                isRunning = false
                try {
                    stopService(Intent(this, TimerService::class.java))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                handler.removeCallbacks(runnable)
                startTimeMillis = 0
                elapsedTimeBeforePause = 0
                salvaStatoTimer(pref)

                txtTimer.text = "⏱️ Tempo Intervento: 00:00:00"
                txtOreUomo.text = "👥 Ore-Uomo Totali: 0,00 ore"
                Toast.makeText(this, "Intervento salvato!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Seleziona o aggiungi un cliente", Toast.LENGTH_SHORT).show()
            }
        }

        btnReportPdf.setOnClickListener {
            if (interventiOggi.isNotEmpty()) {
                generaEInviaPDF(oggiStr)
            } else {
                Toast.makeText(this, "Nessun intervento registrato oggi!", Toast.LENGTH_SHORT).show()
            }
        }

        mainLayout.addView(txtDataOggi)
        mainLayout.addView(spinnerLayout)
        mainLayout.addView(inputOperatori)
        mainLayout.addView(inputNote)
        mainLayout.addView(txtTimer)
        mainLayout.addView(txtOreUomo)
        mainLayout.addView(timerLayout)
        mainLayout.addView(btnSalva)
        mainLayout.addView(btnReportPdf)
        mainLayout.addView(listView)

        setContentView(mainLayout)

        if (isRunning) {
            handler.post(runnable)
        }
    }

    private fun mostraDialogModificaCliente(pref: android.content.SharedPreferences) {
        val selectedIndex = spinnerClienti.selectedItemPosition
        if (selectedIndex >= 0 && selectedIndex < listaClienti.size) {
            val vecchioNome = listaClienti[selectedIndex]
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Modifica Anagrafica Cliente")

            val input = EditText(this).apply {
                setText(vecchioNome)
            }
            builder.setView(input)

            builder.setPositiveButton("Salva Modifica") { _, _ ->
                val nuovoNome = input.text.toString().trim()
                if (nuovoNome.isNotBlank()) {
                    listaClienti[selectedIndex] = nuovoNome
                    adapterSpinner.notifyDataSetChanged()
                    saveClienti(pref)
                    Toast.makeText(this, "Cliente aggiornato!", Toast.LENGTH_SHORT).show()
                }
            }
            builder.setNegativeButton("Annulla", null)
            builder.show()
        } else {
            Toast.makeText(this, "Nessun cliente selezionato da modificare", Toast.LENGTH_SHORT).show()
        }
    }

    private fun mostraDialogModificaIntervento(position: Int, pref: android.content.SharedPreferences, oggiStr: String) {
        val rigaCorrente = interventiOggi[position]
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 16)
        }

        val lblCliente = TextView(this).apply { text = "Cliente:" }
        val editSpinnerCliente = Spinner(this)
        val editAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listaClienti)
        editSpinnerCliente.adapter = editAdapter

        val lblOps = TextView(this).apply { text = "Numero Operatori:" }
        val editOps = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(estraiValore(rigaCorrente, "Operatori: ", " |"))
        }

        val lblDurata = TextView(this).apply { text = "Durata (HH:MM:SS):" }
        val editDurata = EditText(this).apply {
            setText(estraiValore(rigaCorrente, "Durata: ", " |"))
        }

        val lblNote = TextView(this).apply { text = "Note:" }
        val editNote = EditText(this).apply {
            val noteParte = if (rigaCorrente.contains("\nNote: ")) rigaCorrente.substringAfter("\nNote: ") else ""
            setText(noteParte)
        }

        layout.addView(lblCliente)
        layout.addView(editSpinnerCliente)
        layout.addView(lblOps)
        layout.addView(editOps)
        layout.addView(lblDurata)
        layout.addView(editDurata)
        layout.addView(lblNote)
        layout.addView(editNote)

        AlertDialog.Builder(this)
            .setTitle("✏️ Modifica Intervento")
            .setView(layout)
            .setPositiveButton("Salva Modifiche") { _, _ ->
                val clienteNuovo = editSpinnerCliente.selectedItem?.toString() ?: ""
                val numOpsNuovo = editOps.text.toString().ifBlank { "1" }
                val durataNuova = editDurata.text.toString().ifBlank { "00:00:00" }
                val noteNuove = editNote.text.toString()

                val oreUomoCalcolate = calcolaOreUomoDecimali(durataNuova, numOpsNuovo.toIntOrNull() ?: 1)
                
                val oraPart = if (rigaCorrente.contains("] ")) rigaCorrente.substringBefore("] ") + "]" else "[00:00]"
                val rigaAggiornata = "$oraPart $clienteNuovo\nOperatori: $numOpsNuovo | Durata: $durataNuova | Ore-Uomo: $oreUomoCalcolate ore\nNote: ${if (noteNuove.isBlank()) "Nessuna" else noteNuove}"

                interventiOggi[position] = rigaAggiornata
                adapterInterventi.notifyDataSetChanged()
                saveInterventiGiorno(pref, oggiStr)
                Toast.makeText(this, "Intervento modificato!", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("🗑️ Elimina") { _, _ ->
                interventiOggi.removeAt(position)
                adapterInterventi.notifyDataSetChanged()
                saveInterventiGiorno(pref, oggiStr)
                Toast.makeText(this, "Intervento eliminato", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun estraiValore(testo: String, prefisso: String, suffisso: String): String {
        if (!testo.contains(prefisso)) return ""
        val sub = testo.substringAfter(prefisso)
        return if (sub.contains(suffisso)) sub.substringBefore(suffisso) else sub.substringBefore("\n")
    }

    private fun calcolaOreUomoDecimali(tempoHHMMSS: String, numOperatori: Int): String {
        val parti = tempoHHMMSS.split(":")
        var secTotali = 0L
        if (parti.size == 3) {
            val h = parti[0].toLongOrNull() ?: 0L
            val m = parti[1].toLongOrNull() ?: 0L
            val s = parti[2].toLongOrNull() ?: 0L
            secTotali = h * 3600 + m * 60 + s
        }
        val oreDec = (secTotali.toDouble() / 3600.0) * numOperatori
        return String.format(Locale.ITALIAN, "%.2f", oreDec)
    }

    private fun mostraDialogConfermaUscita() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ Timer In Corso!")
            .setMessage("C'è un intervento attivo. Sei sicuro di voler arrestare forzatamente il timer e uscire dall'app?")
            .setPositiveButton("Sì, Ferma e Chiudi") { _, _ ->
                val pref = getSharedPreferences("pulizie_app_db", Context.MODE_PRIVATE)
                isRunning = false
                salvaStatoTimer(pref)
                try {
                    stopService(Intent(this, TimerService::class.java))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                finish()
            }
            .setNegativeButton("Annulla / Continua Timer", null)
            .show()
    }

    private fun salvaStatoTimer(pref: android.content.SharedPreferences) {
        pref.edit()
            .putBoolean("is_running", isRunning)
            .putLong("start_time", startTimeMillis)
            .putLong("elapsed_before_pause", elapsedTimeBeforePause)
            .apply()
    }

    private fun aggiornaTimerEOreUomo() {
        val totalElapsed = if (isRunning) {
            elapsedTimeBeforePause + (System.currentTimeMillis() - startTimeMillis)
        } else {
            elapsedTimeBeforePause
        }

        val seconds = totalElapsed / 1000
        val hrs = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        txtTimer.text = String.format("⏱️ Tempo Intervento: %02d:%02d:%02d", hrs, mins, secs)

        val numOps = inputOperatori.text.toString().toIntOrNull() ?: 1
        val oreDecimali = (seconds.toDouble() / 3600.0) * numOps
        txtOreUomo.text = String.format(Locale.ITALIAN, "👥 Ore-Uomo Totali: %.2f ore", oreDecimali)
    }

    override fun onResume() {
        super.onResume()
        if (isRunning) {
            handler.post(runnable)
        }
    }

    private fun generaEInviaPDF(dataStr: String) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        val paint = Paint()

        paint.textSize = 20f
        paint.isFakeBoldText = true
        canvas.drawText("FAST & CLEAN - Impresa di Pulizia", 40f, 50f, paint)

        paint.textSize = 14f
        paint.isFakeBoldText = false
        canvas.drawText("Report Interventi del: $dataStr", 40f, 80f, paint)
        canvas.drawLine(40f, 95f, 555f, 95f, paint)

        var y = 130f
        paint.textSize = 12f

        for (item in interventiOggi) {
            val lines = item.split("\n")
            for (line in lines) {
                canvas.drawText(line, 40f, y, paint)
                y += 20f
            }
            y += 10f
            canvas.drawLine(40f, y, 555f, y, paint)
            y += 20f
        }

        pdfDocument.finishPage(page)

        val file = File(cacheDir, "Report_Pulizie_$dataStr.pdf")
        try {
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()

            val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Condividi Report PDF"))
        } catch (e: Exception) {
            Toast.makeText(this, "Errore generazione PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun mostraDialogNuovoCliente(pref: android.content.SharedPreferences) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Nuovo Cliente")
        val input = EditText(this).apply { hint = "Es. Bar Roma" }
        builder.setView(input)
        builder.setPositiveButton("Salva") { _, _ ->
            val nome = input.text.toString().trim()
            if (nome.isNotBlank() && !listaClienti.contains(nome)) {
                listaClienti.add(nome)
                adapterSpinner.notifyDataSetChanged()
                saveClienti(pref)
            }
        }
        builder.setNegativeButton("Annulla") { d, _ -> d.cancel() }
        builder.show()
    }

    private fun saveClienti(pref: android.content.SharedPreferences) {
        pref.edit().putStringSet("clienti_db", HashSet(listaClienti)).apply()
    }

    private fun loadClienti(pref: android.content.SharedPreferences) {
        val set = pref.getStringSet("clienti_db", null)
        listaClienti.clear()
        if (set != null) listaClienti.addAll(set) else listaClienti.add("Cliente Esempio")
    }

    private fun saveInterventiGiorno(pref: android.content.SharedPreferences, data: String) {
        pref.edit().putStringSet("interventi_$data", HashSet(interventiOggi)).apply()
    }

    private fun loadInterventiGiorno(pref: android.content.SharedPreferences, data: String) {
        val set = pref.getStringSet("interventi_$data", null)
        interventiOggi.clear()
        if (set != null) interventiOggi.addAll(set)
    }
}
