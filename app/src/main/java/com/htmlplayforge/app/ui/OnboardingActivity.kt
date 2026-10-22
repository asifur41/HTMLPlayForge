package com.htmlplayforge.app.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.htmlplayforge.app.R
import com.htmlplayforge.app.settings.AppTheme
import com.htmlplayforge.app.settings.ThemeManager
import com.htmlplayforge.app.utils.Prefs
import com.htmlplayforge.app.utils.Ui

/** Four swipe screens shown once. */
class OnboardingActivity : AppCompatActivity() {
    private class Page(val icon: Int, val text: String)

    private val pages = listOf(
        Page(R.drawable.ic_code, "Write HTML, CSS and JavaScript anywhere, 100% offline"),
        Page(R.drawable.ic_play, "Tap Run to see your page instantly"),
        Page(R.drawable.ic_tune, "Customize themes, fonts and layout in Settings"),
        Page(R.drawable.ic_save, "Auto-save protects your work")
    )

    private lateinit var theme0: AppTheme

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        theme0 = ThemeManager.current()
        ThemeManager.styleWindow(this)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(theme0.bg)
        Ui.applyInsets(root)

        val pager = ViewPager2(this)
        pager.adapter = PageAdapter()
        root.addView(pager, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        val p = Ui.dp(this, 16)
        bar.setPadding(p, p, p, p)

        val skip = TextView(this)
        skip.text = "Skip"
        skip.textSize = 16f
        skip.setTextColor(theme0.gutter)
        skip.setPadding(p, p / 2, p, p / 2)
        skip.setOnClickListener { finishTour() }
        bar.addView(skip)

        val dots = LinearLayout(this) // small round dots drawn as views, no text symbols
        dots.orientation = LinearLayout.HORIZONTAL
        dots.gravity = Gravity.CENTER
        bar.addView(dots, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val next = TextView(this)
        next.textSize = 16f
        next.setTextColor(0xFFFFFFFF.toInt())
        next.gravity = Gravity.CENTER
        next.background = Ui.roundBackground(theme0.accent, 24, this)
        next.setPadding(p * 2, p / 2 + 4, p * 2, p / 2 + 4)
        next.compoundDrawablePadding = Ui.dp(this, 6)
        bar.addView(next)
        root.addView(bar)
        setContentView(root)

        fun refresh(position: Int) {
            val last = position == pages.size - 1
            next.text = if (last) "Get Started" else "Next"
            skip.visibility = if (last) android.view.View.INVISIBLE else android.view.View.VISIBLE
            if (last) next.setCompoundDrawablesRelative(null, null, null, null)
            else {
                val d = androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_chevron)?.mutate()
                if (d != null) {
                    d.setTint(0xFFFFFFFF.toInt())
                    val s = Ui.dp(this, 20)
                    d.setBounds(0, 0, s, s)
                    next.setCompoundDrawablesRelative(null, null, d, null)
                }
            }
            dots.removeAllViews()
            for (i in pages.indices) dots.addView(Ui.dot(this, if (i == position) theme0.accent else Ui.withAlpha(theme0.text, 0x55)))
        }
        refresh(0)
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) { refresh(position) }
        })
        next.setOnClickListener {
            if (pager.currentItem >= pages.size - 1) finishTour() else pager.currentItem = pager.currentItem + 1
        }
    }

    private fun finishTour() {
        Prefs.onboardingDone = true
        finish()
    }

    private inner class PageAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = pages.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val box = LinearLayout(parent.context)
            box.orientation = LinearLayout.VERTICAL
            box.gravity = Gravity.CENTER
            box.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            val pad = Ui.dp(parent.context, 32)
            box.setPadding(pad, pad, pad, pad)
            val img = ImageView(parent.context)
            img.id = android.view.View.generateViewId()
            img.imageTintList = ColorStateList.valueOf(theme0.accent)
            box.addView(img, LinearLayout.LayoutParams(Ui.dp(parent.context, 120), Ui.dp(parent.context, 120)))
            val t = TextView(parent.context)
            t.textSize = 22f
            t.gravity = Gravity.CENTER
            t.setTextColor(theme0.text)
            t.setPadding(0, pad, 0, 0)
            box.addView(t)
            return object : RecyclerView.ViewHolder(box) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val box = holder.itemView as LinearLayout
            (box.getChildAt(0) as ImageView).setImageResource(pages[position].icon)
            (box.getChildAt(1) as TextView).text = pages[position].text
        }
    }
}
