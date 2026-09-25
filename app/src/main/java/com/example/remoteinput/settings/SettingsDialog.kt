package com.example.remoteinput.settings

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*

class SettingsDialog(
    context: Context,
    private val settings: SettingsManager,
    private val isConnected: Boolean,
    private val isPortrait: Boolean,
    private val onConnect: () -> Unit,
    private val onDisconnect: () -> Unit,
    private val onThemeChanged: (AppTheme) -> Unit,
    private val onTrackpadPositionChanged: (Boolean) -> Unit,
    private val onOrientationChanged: (Boolean) -> Unit // true = portrait
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val theme = settings.currentTheme

        val outerBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(16).toFloat()
            setColor(theme.surface)
        }

        val scrollView = ScrollView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp(20), dp(16), dp(20), dp(16))
            background = outerBg
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Title
        container.addView(TextView(context).apply {
            text = "Settings"
            textSize = 20f
            setTextColor(theme.keyText)
            setPadding(0, 0, 0, dp(12))
            gravity = Gravity.CENTER
        })

        // --- Connection section ---
        container.addView(makeSectionLabel("CONNECTION", theme))

        if (isConnected) {
            container.addView(makeButton("Disconnect", theme.accent, theme) {
                onDisconnect()
                dismiss()
            })
        } else {
            container.addView(makeButton("Connect", theme.accent, theme) {
                onConnect()
                dismiss()
            })
        }

        container.addView(makeDivider(theme))

        // --- Theme section ---
        container.addView(makeSectionLabel("THEME", theme))

        // Horizontal scroll for theme swatches
        val themeScroll = HorizontalScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isHorizontalScrollBarEnabled = false
            setPadding(0, dp(4), 0, dp(4))
        }

        val themeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        for (t in AppTheme.ALL_THEMES) {
            val isSelected = t.name == settings.themeName

            val item = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = dp(12)
                }
            }

            val swatch = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))

                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(8).toFloat()
                    setColor(t.background)
                    if (isSelected) {
                        setStroke(dp(3), t.accent)
                    } else {
                        setStroke(dp(1), t.trackpadBorder)
                    }
                }
                background = bg

                addView(FrameLayout(context).apply {
                    layoutParams = FrameLayout.LayoutParams(dp(14), dp(14)).apply {
                        gravity = Gravity.CENTER
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(t.accent)
                    }
                })

                setOnClickListener {
                    settings.themeName = t.name
                    onThemeChanged(t)
                    dismiss()
                }
            }

            item.addView(swatch)
            item.addView(TextView(context).apply {
                text = t.name
                textSize = 10f
                setTextColor(if (isSelected) theme.accent else theme.textSecondary)
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            })

            themeRow.addView(item)
        }

        themeScroll.addView(themeRow)
        container.addView(themeScroll)

        container.addView(makeDivider(theme))

        // --- Layout section ---
        container.addView(makeSectionLabel("LAYOUT", theme))

        // Orientation toggle
        container.addView(makeToggleRow(
            "Portrait mode",
            "Switch between portrait and landscape orientation",
            isPortrait,
            theme
        ) { checked ->
            onOrientationChanged(checked)
        })

        // Trackpad position toggle
        container.addView(makeToggleRow(
            "Trackpad on left",
            "Place trackpad on the left side in landscape mode",
            settings.trackpadOnLeft,
            theme
        ) { checked ->
            settings.trackpadOnLeft = checked
            onTrackpadPositionChanged(checked)
        })

        container.addView(makeDivider(theme))

        // Close button
        container.addView(makeButton("Close", theme.textSecondary, theme) {
            dismiss()
        })

        scrollView.addView(container)
        setContentView(scrollView)

        // Style the dialog window
        window?.apply {
            setLayout(
                (context.resources.displayMetrics.widthPixels * 0.80).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(0.6f)
        }
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }

    private fun makeSectionLabel(text: String, theme: AppTheme): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 11f
            setTextColor(theme.accent)
            letterSpacing = 0.15f
            setPadding(0, dp(6), 0, dp(6))
        }
    }

    private fun makeDivider(theme: AppTheme): android.view.View {
        return android.view.View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            ).apply {
                topMargin = dp(4)
                bottomMargin = dp(4)
            }
            setBackgroundColor(Color.argb(40, 255, 255, 255))
        }
    }

    private fun makeButton(text: String, color: Int, theme: AppTheme, onClick: () -> Unit): Button {
        return Button(context).apply {
            this.text = text
            textSize = 14f
            setTextColor(color)
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(4)
                bottomMargin = dp(4)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun makeToggleRow(
        title: String,
        subtitle: String,
        checked: Boolean,
        theme: AppTheme,
        onChanged: (Boolean) -> Unit
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, dp(6), 0, dp(6))
            gravity = Gravity.CENTER_VERTICAL

            val textContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            textContainer.addView(TextView(context).apply {
                text = title
                textSize = 14f
                setTextColor(theme.keyText)
            })

            textContainer.addView(TextView(context).apply {
                text = subtitle
                textSize = 10f
                setTextColor(theme.textSecondary)
                setPadding(0, dp(2), 0, 0)
            })

            addView(textContainer)

            addView(Switch(context).apply {
                isChecked = checked
                setOnCheckedChangeListener { _, isChecked -> onChanged(isChecked) }
            })
        }
    }
}
