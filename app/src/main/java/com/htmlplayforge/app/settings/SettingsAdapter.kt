package com.htmlplayforge.app.settings

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.slider.Slider
import com.htmlplayforge.app.R
import com.htmlplayforge.app.utils.Ui

/** One line of the settings screen. */
sealed class Row {
    class Header(val title: String) : Row()
    class Toggle(val title: String, val subtitle: String, val get: () -> Boolean, val set: (Boolean) -> Unit) : Row()
    class Segmented(val title: String, val options: List<Pair<String, String>>, val get: () -> String, val set: (String) -> Unit) : Row()
    class SliderRow(val title: String, val min: Int, val max: Int, val get: () -> Int, val set: (Int) -> Unit) : Row()
    /** A row that opens a dialog. [value] is shown as the subtitle. */
    class Choice(val title: String, val value: () -> String, val onClick: () -> Unit) : Row()
    /** A row that opens a link or an action. Shows a chevron. */
    class Link(val title: String, val subtitle: String, val onClick: () -> Unit) : Row()
    class Info(val title: String, val subtitle: String) : Row()
}

class SettingsAdapter(private var rows: List<Row>, private var theme: AppTheme) : RecyclerView.Adapter<SettingsAdapter.VH>() {
    class VH(val box: LinearLayout) : RecyclerView.ViewHolder(box)

    fun update(newRows: List<Row>, newTheme: AppTheme) {
        rows = newRows
        theme = newTheme
        notifyDataSetChanged()
    }

    override fun getItemCount() = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val box = LinearLayout(parent.context)
        box.orientation = LinearLayout.VERTICAL
        box.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return VH(box)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val c = holder.box.context
        holder.box.removeAllViews()
        holder.box.setOnClickListener(null)
        holder.box.isClickable = false
        holder.box.background = null
        val row = rows[position]
        val pad = Ui.dp(c, 16)
        when (row) {
            is Row.Header -> {
                val t = Ui.label(c, row.title.uppercase(), 12f, theme.accent)
                t.typeface = Typeface.DEFAULT_BOLD
                t.setPadding(pad, pad * 3 / 2, pad, Ui.dp(c, 4))
                holder.box.addView(t)
            }
            is Row.Toggle -> {
                val line = textColumn(c, row.title, row.subtitle)
                val sw = SwitchCompat(c)
                Ui.styleSwitch(sw, theme)
                sw.isChecked = row.get()
                sw.setOnCheckedChangeListener { _, v -> row.set(v) }
                holder.box.addView(withRight(c, line, sw))
                holder.box.setOnClickListener { sw.toggle() }
                clickable(holder.box)
            }
            is Row.Segmented -> {
                val title = Ui.label(c, row.title, 16f, theme.text)
                title.setPadding(pad, pad / 2, pad, Ui.dp(c, 6))
                holder.box.addView(title)
                val seg = LinearLayout(c)
                seg.orientation = LinearLayout.HORIZONTAL
                seg.setPadding(pad, 0, pad, pad / 2)
                val buttons = ArrayList<Pair<TextView, String>>()
                fun paint() {
                    for ((tv, id) in buttons) {
                        val on = row.get() == id
                        tv.setTextColor(if (on) 0xFFFFFFFF.toInt() else theme.text)
                        tv.background = Ui.roundBackground(if (on) theme.accent else Ui.withAlpha(theme.text, 0x22), 10, c)
                    }
                }
                for ((label, id) in row.options) {
                    val tv = TextView(c)
                    tv.text = label
                    tv.gravity = Gravity.CENTER
                    tv.textSize = 15f
                    tv.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 10))
                    tv.setOnClickListener { row.set(id); paint() }
                    val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    lp.marginEnd = Ui.dp(c, 8)
                    seg.addView(tv, lp)
                    buttons.add(Pair(tv, id))
                }
                paint()
                holder.box.addView(seg)
            }
            is Row.SliderRow -> {
                val title = Ui.label(c, row.title + ": " + row.get(), 16f, theme.text)
                title.setPadding(pad, pad / 2, pad, 0)
                holder.box.addView(title)
                val s = Slider(c)
                s.valueFrom = row.min.toFloat()
                s.valueTo = row.max.toFloat()
                s.stepSize = 1f
                // lesson 2: the value must be a whole number inside the range, or Slider crashes
                s.value = row.get().coerceIn(row.min, row.max).toFloat()
                s.thumbTintList = ColorStateList.valueOf(theme.accent)
                s.trackActiveTintList = ColorStateList.valueOf(theme.accent)
                s.trackInactiveTintList = ColorStateList.valueOf(Ui.withAlpha(theme.text, 0x33))
                s.addOnChangeListener { _, v, fromUser ->
                    if (fromUser) {
                        val whole = Math.round(v)
                        row.set(whole)
                        title.text = row.title + ": " + whole
                    }
                }
                holder.box.addView(s, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).also {
                    it.setMargins(pad / 2, 0, pad / 2, 0)
                })
            }
            is Row.Choice -> {
                holder.box.addView(textColumn(c, row.title, row.value()))
                holder.box.setOnClickListener { row.onClick() }
                clickable(holder.box)
            }
            is Row.Link -> {
                holder.box.addView(withRight(c, textColumn(c, row.title, row.subtitle), chevron(c)))
                holder.box.setOnClickListener { row.onClick() }
                clickable(holder.box)
            }
            is Row.Info -> holder.box.addView(textColumn(c, row.title, row.subtitle))
        }
    }

    private fun clickable(v: View) {
        val out = android.util.TypedValue()
        v.context.theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
        v.setBackgroundResource(out.resourceId)
        v.isClickable = true
    }

    private fun textColumn(c: Context, title: String, subtitle: String): LinearLayout {
        val col = LinearLayout(c)
        col.orientation = LinearLayout.VERTICAL
        val pad = Ui.dp(c, 16)
        col.setPadding(pad, Ui.dp(c, 10), pad, Ui.dp(c, 10))
        col.addView(Ui.label(c, title, 16f, theme.text))
        if (subtitle.isNotEmpty()) col.addView(Ui.label(c, subtitle, 13f, theme.gutter))
        return col
    }

    private fun withRight(c: Context, left: View, right: View): LinearLayout {
        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.marginEnd = Ui.dp(c, 16)
        row.addView(right, lp)
        return row
    }

    private fun chevron(c: Context): ImageView {
        val i = ImageView(c)
        i.setImageResource(R.drawable.ic_chevron)
        i.imageTintList = ColorStateList.valueOf(theme.gutter)
        return i
    }
}
