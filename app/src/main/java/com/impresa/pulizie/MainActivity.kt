package com.impresa.pulizie

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val interventi = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val inputCliente = EditText(this).apply {
            hint = "Nome Cliente / Cantiere"
        }

        val inputNote = EditText(this).apply {
            hint = "Note Intervento"
        }

        val btnSalva = Button(this).apply {
            text = "Aggiungi Intervento"
        }

        val listView = ListView(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, interventi)
        listView.adapter = adapter

        btnSalva.setOnClickListener {
            val cliente = inputCliente.text.toString()
            val note = inputNote.text.toString()
            if (cliente.isNotBlank()) {
                interventi.add("• $cliente - $note")
                adapter.notifyDataSetChanged()
                inputCliente.text.clear()
                inputNote.text.clear()
            }
        }

        layout.addView(inputCliente)
        layout.addView(inputNote)
        layout.addView(btnSalva)
        layout.addView(listView)

        setContentView(layout)
    }
}
