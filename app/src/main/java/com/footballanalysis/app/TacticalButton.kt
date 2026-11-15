package com.footballanalysis.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.TextView

class TacticalButton(
    context: Context,
    symbol: String,
    description: String,
    private val onAction: () -> Unit
) : TextView(context) {

    private val normalColor = Color.rgb(106, 172, 201)
    private val selectedColor = Color.rgb(138, 197, 222)
    private val backgroundColor = Color.rgb(37, 44, 54)

    var selectedTool = false
        set(value) {
            field = value
            updateAppearance()
        }

    init {
        text = symbol
        textSize = 18f
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        contentDescription = description
        isClickable = true
        isFocusable = true

        val density = resources.displayMetrics.density
        val size = (32 * density).toInt()

        minWidth = size
        minHeight = size

        setPadding(0, 0, 0, 0)

        setOnClickListener { onAction() }

        setOnLongClickListener {
            android.widget.Toast.makeText(
                context,
                description,
                android.widget.Toast.LENGTH_SHORT
            ).show()
            true
        }

        updateAppearance()
    }

    private fun updateAppearance() {
        setTextColor(
            if (selectedTool) selectedColor else normalColor
        )

        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 5f * resources.displayMetrics.density
            setColor(backgroundColor)
            setStroke(
                (resources.displayMetrics.density).toInt().coerceAtLeast(1),
                if (selectedTool) selectedColor
                else Color.rgb(52, 75, 92)
            )
        }
    }
}
