package com.example.tamilalarm

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Lets the user paste the verse reference + text for each of the 24 hours. */
class VersesActivity : Activity() {

    private val refFields = mutableListOf<EditText>()
    private val textFields = mutableListOf<EditText>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            setText(R.string.verses_note)
            textSize = 14f
        })

        val saved = Verses.all(this)
        for (h in 0..23) {
            root.addView(TextView(this).apply {
                text = "%02d:00  (%s)".format(h, TamilTime.period(h))
                textSize = 18f
                setPadding(0, pad, 0, 0)
            })
            val ref = EditText(this).apply {
                hint = "வசன முகவரி (எ.கா. யோவான் 3:16)"
                setText(saved[h].ref)
                setSingleLine()
            }
            val text = EditText(this).apply {
                hint = "வசன வாசகம்"
                setText(saved[h].text)
                minLines = 2
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            }
            refFields.add(ref)
            textFields.add(text)
            root.addView(ref)
            root.addView(text)
        }

        root.addView(Button(this).apply {
            setText(R.string.save)
            setOnClickListener {
                val list = (0..23).map { i ->
                    Verses.Verse(refFields[i].text.toString().trim(), textFields[i].text.toString().trim())
                }
                Verses.save(this@VersesActivity, list)
                Toast.makeText(this@VersesActivity, R.string.saved, Toast.LENGTH_SHORT).show()
                finish()
            }
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }
}
