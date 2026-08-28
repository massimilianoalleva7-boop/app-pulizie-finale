package com.impresa.pulizie

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val interventi = ArrayList<String>()
    private var secondsElapsed = 0
    private var isRunning = false
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var runnable: Runnable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pref = getSharedPreferences("pulizie_prefs", Context.MODE_PRIVATE)
        loadSavedData(pref)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val inputCliente = EditText(this).apply { hint = "Cliente / Cantiere" }
        val inputNote = EditText(this).apply { hint = "Note Intervento" }

        val txtTimer = TextView(this).apply {
            text = "Tempo: 00:00:00"
            textSize = 18f
            setPadding(0, 16, 0, 16)
        }

        val timerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val btnStart = Button(this).apply { text = "▶ START" }
        val btnStop = Button(this).apply { text = "⏹ STOP" }

        timerLayout.addView(btnStart)
        timerLayout.addView(btnStop)

        val btnSalva = Button(this).apply { text = "Salva Intervento" }
        val listView = ListView(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, interventi)
        listView.adapter = adapter

        runnable = object : Runnable {
            override fun run() {
                if (isRunning) {
                    secondsElapsed++
                    val hrs = secondsElapsed / 3600
                    val mins = (secondsElapsed % 3600) / 60
                    val secs = secondsElapsed % 60
                    txtTimer.text = String.format("Tempo: %02d:%02d:%02d", hrs, mins, secs)
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

        btnSalva.setOnClickListener {
            val cliente = inputCliente.text.toString()
            val note = inputNote.text.toString()
            val tempoStr = txtTimer.text.toString().replace("Tempo: ", "")

            if (cliente.isNotBlank()) {
                val riga = "• $cliente | Durata: $tempoStr\nNote: $note"
                interventi.add(0, riga)
                adapter.notifyDataSetChanged()
                saveData(pref)

                inputCliente.text.clear()
                inputNote.text.clear()
                isRunning = false
                secondsElapsed = 0
                txtTimer.text = "Tempo: 00:00:00"
            } else {
                Toast.makeText(this, "Inserisci il nome del cliente", Toast.LENGTH_SHORT).show()
            }
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val item = interventi[position]
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Report Pulizie:\n$item")
            }
            startActivity(Intent.createChooser(intent, "Condividi via"))
        }

        layout.addView(inputCliente)
        layout.addView(inputNote)
        layout.addView(txtTimer)
        layout.addView(timerLayout)
        layout.addView(btnSalva)
        layout.addView(listView)

        setContentView(layout)
    }

    private fun saveData(pref: android.content.SharedPreferences) {
        val set = HashSet<String>(interventi)
        pref.edit().putStringSet("items", set).apply()
    }

    private fun loadSavedData(pref: android.content.SharedPreferences) {
        val set = pref.getStringSet("items", null)
        if (set != null) {
            interventi.clear()
            interventi.addAll(set)
        }
    }
}
