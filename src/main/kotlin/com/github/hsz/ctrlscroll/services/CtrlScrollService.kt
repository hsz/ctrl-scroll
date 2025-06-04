package com.github.hsz.ctrlscroll.services

import com.github.hsz.ctrlscroll.ui.HighlightOverlay
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import java.awt.*
import java.awt.event.KeyEvent
import java.awt.event.MouseWheelEvent
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.ceil
import kotlin.math.floor

@Service(Service.Level.APP)
class CtrlScrollService {
    private val log = logger<CtrlScrollService>()

    // Variables to track state
    private val ctrlPressed = AtomicBoolean(false)
    private val isToolWindowBrightened = AtomicBoolean(false)
    private val originalBackground = mutableMapOf<Component, Color?>()
    private var activeToolWindowComponent: Component? = null
    private var overlayPanel: HighlightOverlay? = null
    private var finalUpdateTimer: Timer? = null

    // Animation variables
    private var focusEffectIncreasing = true
    private var focusEffectIntensity = 0
    private val focusEffectTimer = Timer(50, null) // Timer for pulsing effect

    // Variables to track the scroll direction
    private val lastScrollDirection = AtomicBoolean(true) // true for vertical, false for horizontal
    private val lastScrollAmount = AtomicLong(0) // positive for down/right, negative for up/left

    // Timer to check for tool window under mouse when Ctrl is pressed
    private val brightnessCheckTimer = Timer(100) {
        when {
            ctrlPressed.get() -> {
                // If Ctrl is pressed, check for a tool window under the mouse
                checkToolWindowUnderMouse()?.let { toolWindow ->
                    // Highlight tool window and start focus effect
                    brightenToolWindow(toolWindow.component)
                    if (!focusEffectTimer.isRunning) startFocusEffect()
                } ?: restoreAllToolWindows() // No tool window under the mouse
            }
            isToolWindowBrightened.get() -> restoreAllToolWindows() // Ctrl released
        }
    }

    init {
        log.info("Initializing CtrlScrollService")
        initKeyboardListener()
        initMouseWheelListener()
        startTimers()
    }

    private fun initKeyboardListener() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher { event ->
            if (event.keyCode == KeyEvent.VK_CONTROL) {
                when (event.id) {
                    KeyEvent.KEY_PRESSED -> ctrlPressed.set(true)
                    KeyEvent.KEY_RELEASED -> ctrlPressed.set(false)
                }
            }
            false // Allow the event to be dispatched to its target
        }
    }

    private fun initMouseWheelListener() {
        Toolkit.getDefaultToolkit().addAWTEventListener({ event ->
            if (event !is MouseWheelEvent || !ctrlPressed.get()) return@addAWTEventListener

            // Track scroll direction and amount
            lastScrollDirection.set(!event.isShiftDown)
            lastScrollAmount.set(event.preciseWheelRotation.toLong())

            // Get tool window under mouse or return
            val toolWindow = checkToolWindowUnderMouse() ?: return@addAWTEventListener

            // Resize the tool window based on a scroll direction
            runInEdt {
                val resizeAmount = with(event.preciseWheelRotation) {
                    when {
                        this >= 0 -> ceil(this)
                        else -> floor(this)
                    }
                }.toInt()

                // Resize based on anchor
                when (toolWindow.anchor) {
                    ToolWindowAnchor.LEFT, ToolWindowAnchor.RIGHT -> toolWindow.stretchWidth(resizeAmount)
                    ToolWindowAnchor.TOP, ToolWindowAnchor.BOTTOM -> toolWindow.stretchHeight(resizeAmount)
                }

                // Update overlay if needed
                if (activeToolWindowComponent == toolWindow.component) {
                    updateOverlayBounds()

                    // Schedule final update
                    finalUpdateTimer?.stop()
                    finalUpdateTimer = Timer(100) { updateOverlayBounds() }.apply {
                        isRepeats = false
                        start()
                    }
                }
            }
        }, AWTEvent.MOUSE_WHEEL_EVENT_MASK)
    }

    private fun startTimers() {
        brightnessCheckTimer.start()
        focusEffectTimer.isRepeats = true
    }

    // Function to start the focus effect
    private fun startFocusEffect() {
        focusEffectIntensity = 0
        focusEffectIncreasing = true

        // Reset and configure the timer
        focusEffectTimer.apply {
            stop()
            removeActionListener(actionListeners.firstOrNull())
            addActionListener { _ ->
                // Update intensity with pulsing effect (0-100-0)
                focusEffectIntensity = when {
                    focusEffectIncreasing -> (focusEffectIntensity + 5).also { newIntensity -> 
                        if (newIntensity >= 100) focusEffectIncreasing = false 
                    }
                    else -> (focusEffectIntensity - 5).also { newIntensity -> 
                        if (newIntensity <= 0) focusEffectIncreasing = true 
                    }
                }

                // Update visual effect
                overlayPanel?.let { overlay ->
                    overlay.focusEffectIntensity = focusEffectIntensity
                    overlay.repaint()
                }
            }
            start()
        }
    }

    // Function to highlight a tool window
    private fun brightenToolWindow(component: Component) {
        if (isToolWindowBrightened.get() && originalBackground.containsKey(component)) {
            return // Already highlighted this component
        }

        if (component !is JComponent) {
            return // Only JComponents can be highlighted
        }

        try {
            // Store original state
            originalBackground[component] = component.background
            component.putClientProperty("original.tooltip", component.toolTipText)

            // Update component state
            component.toolTipText = "Tool Window Active - Use Ctrl+Scroll to resize"
            component.putClientProperty("toolWindow.active", true)
            activeToolWindowComponent = component

            // Create and add overlay
            SwingUtilities.getRootPane(component)?.let { rootPane ->
                val layeredPane = rootPane.layeredPane

                // Remove existing overlay
                overlayPanel?.let { existingOverlay -> 
                    layeredPane.remove(existingOverlay) 
                }

                // Create and add new overlay
                val newOverlay = HighlightOverlay(component)
                overlayPanel = newOverlay
                layeredPane.add(newOverlay, JLayeredPane.POPUP_LAYER)
                layeredPane.revalidate()
                layeredPane.repaint()
            }

            isToolWindowBrightened.set(true)
        } catch (e: Exception) {
            log.error("Error highlighting tool window", e)
        }
    }

    // Function to update the overlay bounds to match the target component
    private fun updateOverlayBounds() {
        try {
            overlayPanel?.let { overlay ->
                overlay.updateBounds()
                overlay.revalidate()
                overlay.repaint()
            }
        } catch (e: Exception) {
            log.error("Error updating overlay bounds", e)
        }
    }

    // Function to restore the original appearance of all tool windows
    private fun restoreAllToolWindows() {
        if (!isToolWindowBrightened.get()) return

        try {
            // Stop timers
            focusEffectTimer.stop()
            finalUpdateTimer?.stop()
            finalUpdateTimer = null

            // Remove overlay
            if (activeToolWindowComponent != null) {
                val component = activeToolWindowComponent!!
                val rootPane = SwingUtilities.getRootPane(component)
                if (rootPane != null) {
                    val layeredPane = rootPane.layeredPane
                    if (overlayPanel != null) {
                        val overlay = overlayPanel!!
                        layeredPane.remove(overlay)
                        layeredPane.revalidate()
                        layeredPane.repaint()
                    }
                }
            }

            // Reset components
            val jComponents = originalBackground.keys.filterIsInstance<JComponent>()
            for (component in jComponents) {
                component.putClientProperty("toolWindow.active", null)
                component.toolTipText = component.getClientProperty("original.tooltip") as? String
                component.putClientProperty("original.tooltip", null)
            }

            // Clear state
            overlayPanel = null
            activeToolWindowComponent = null
            originalBackground.clear()
            isToolWindowBrightened.set(false)
        } catch (e: Exception) {
            log.error("Error restoring tool windows", e)
        }
    }

    // Helper function to check if the mouse is over a component
    private fun isMouseOverComponent(component: Component, mousePosition: Point): Boolean {
        if (!component.isShowing) return false

        // Convert screen coordinates to component coordinates and check bounds
        return Point(mousePosition).apply { 
            SwingUtilities.convertPointFromScreen(this, component) 
        }.let { p ->
            p.x in 0 until component.width && p.y in 0 until component.height
        }
    }

    // Function to check if the mouse is over a tool window
    private fun checkToolWindowUnderMouse(): ToolWindowEx? {
        val mousePosition = MouseInfo.getPointerInfo().location

        // Get visible tool windows from the current project
        return ProjectManager.getInstance().openProjects.firstOrNull()?.let { project ->
            ToolWindowManager.getInstance(project).toolWindowIds
                .mapNotNull { id -> ToolWindowManager.getInstance(project).getToolWindow(id) }
                .filter { it.isVisible }
                .firstOrNull { toolWindow -> 
                    isMouseOverComponent(toolWindow.component, mousePosition) 
                } as? ToolWindowEx
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(): CtrlScrollService = service()
    }
}
