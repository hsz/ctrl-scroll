package com.github.hsz.ctrlscroll.ui

import java.awt.*
import javax.swing.JPanel
import javax.swing.SwingUtilities
import com.intellij.ui.JBColor

/**
 * Custom overlay component for highlighting tool windows
 */
class HighlightOverlay(private val targetComponent: Component) : JPanel() {
    private val highlightColor = JBColor(Color(0, 120, 215), Color(0, 120, 215)) // Bright blue
    private val padding = 2
    private val cornerRadius = 10
    
    // Animation intensity (0-100)
    var focusEffectIntensity: Int = 0

    init {
        isOpaque = false
        layout = null
        updateBounds()
    }

    /**
     * Updates the bounds of the overlay to match the target component
     */
    fun updateBounds() {
        bounds = SwingUtilities.convertRectangle(
            targetComponent.parent,
            targetComponent.bounds,
            SwingUtilities.getWindowAncestor(targetComponent)
        )
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)

        (g as Graphics2D).apply {
            setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            // Draw a glowing outline with dynamic alpha based on intensity
            composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f + (focusEffectIntensity / 200f))
            stroke = BasicStroke(3f)
            color = highlightColor

            // Draw outline
            drawRoundRect(padding, padding, width - padding * 2, height - padding * 2, cornerRadius, cornerRadius)

            // Add subtle fill
            composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.05f)
            fillRoundRect(padding, padding, width - padding * 2, height - padding * 2, cornerRadius, cornerRadius)
        }
    }
}