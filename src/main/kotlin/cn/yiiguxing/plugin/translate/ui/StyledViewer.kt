package cn.yiiguxing.plugin.translate.ui

import com.intellij.openapi.ui.JBMenuItem
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.ui.PopupMenuListenerAdapter
import java.awt.Color
import java.awt.Cursor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.Icon
import javax.swing.JMenuItem
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener
import javax.swing.text.Element
import javax.swing.text.MutableAttributeSet
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants as DefaultStyleConstants

/**
 * StyledViewer
 *
 * Created by Yii.Guxing on 2019/10/11.
 */
class StyledViewer : Viewer() {

    private var onClickHandler: ((Element, Any?) -> Unit)? = null
    private var onBeforeFoldingExpandHandler: ((Element, Any?) -> Unit)? = null
    private var onFoldingExpandedHandler: ((Any?) -> Unit)? = null

    private val popupMenu = JBPopupMenu()
    private var popupMenuTargetElement: Element? = null
    private var popupMenuTargetData: Any? = null

    init {
        dragEnabled = false
        disableSelection()

        ViewerMouseAdapter().let {
            addMouseListener(it)
            addMouseMotionListener(it)
        }

        popupMenu.addPopupMenuListener(object : PopupMenuListenerAdapter() {
            override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent?) {
                popupMenuTargetElement = null
                popupMenuTargetData = null
            }

            override fun popupMenuCanceled(e: PopupMenuEvent?) {
                popupMenuTargetElement = null
                popupMenuTargetData = null
            }
        })
    }

    fun addPopupMenuItem(
        text: String,
        icon: Icon? = null,
        action: (item: JMenuItem, element: Element) -> Unit
    ): JMenuItem {
        return JBMenuItem(text, icon).apply {
            addActionListener { popupMenuTargetElement?.let { action(this, it) } }
        }
    }

    fun addPopupMenuListener(listener: PopupMenuListener) = popupMenu.addPopupMenuListener(listener)

    fun onClick(handler: ((element: Element, data: Any?) -> Unit)?) {
        onClickHandler = handler
    }

    fun onBeforeFoldingExpand(handler: ((element: Element, data: Any?) -> Unit)?) {
        onBeforeFoldingExpandHandler = handler
    }

    fun onFoldingExpanded(handler: ((data: Any?) -> Unit)?) {
        onFoldingExpandedHandler = handler
    }

    private fun showPopupMenu(event: MouseEvent, element: Element, data: Any?) {
        if (popupMenu.componentCount > 0) {
            popupMenuTargetElement = element
            popupMenuTargetData = data
            val popupLocation = getPopupLocation(event)
            popupMenu.show(this, popupLocation.x, popupLocation.y)
        }
    }

    private enum class StyleConstants {
        MouseListener;

        companion object {
            fun MutableAttributeSet.setMouseListener(listener: StyledViewer.MouseListener) {
                addAttribute(MouseListener, listener)
            }
        }
    }

    private inner class ViewerMouseAdapter : MouseAdapter() {

        private var lastElement: Element? = null
        private var activeElement: Element? = null

        private inline val MouseEvent.characterElement: Element
            get() = styledDocument.getCharacterElement(viewToModel(point))

        private inline val Element.mouseListener: MouseListener?
            get() = attributes.getAttribute(StyleConstants.MouseListener) as? MouseListener

        override fun mouseMoved(event: MouseEvent) {
            val element = event.characterElement
            if (element !== lastElement) {
                exitLastElement(event)

                lastElement = element.mouseListener?.run {
                    mouseEntered(this@StyledViewer, event, element)
                    element
                }
            }
        }

        private fun exitLastElement(event: MouseEvent) {
            activeElement = null
            val element = lastElement ?: return

            element.mouseListener?.mouseExited(this@StyledViewer, event, element)
            lastElement = null
        }

        override fun mouseExited(event: MouseEvent) = exitLastElement(event)

        override fun mousePressed(event: MouseEvent) {
            activeElement = event.characterElement
        }

        override fun mouseReleased(event: MouseEvent) { // 使用`mouseClicked`在MacOS下会出现事件丢失的情况...
            with(event) {
                characterElement.takeIf { it === activeElement }?.let { elem ->
                    (elem.attributes.getAttribute(StyleConstants.MouseListener) as? MouseListener)?.run {
                        if (modifiers and MouseEvent.BUTTON1_MASK != 0) {
                            mouseClicked(this@StyledViewer, event, elem)
                        }

                        if (isMetaDown) {
                            mouseRightButtonClicked(this@StyledViewer, event, elem)
                        }
                    }
                }
            }
            activeElement = null
        }
    }

    open class MouseListener internal constructor(val data: Any? = null) {

        fun mouseClicked(viewer: StyledViewer, event: MouseEvent, element: Element) {
            if (!onMouseClick(viewer, event, element)) {
                viewer.onClickHandler?.invoke(element, data)
            }
        }

        protected open fun onMouseClick(viewer: StyledViewer, event: MouseEvent, element: Element): Boolean = false

        fun mouseRightButtonClicked(viewer: StyledViewer, event: MouseEvent, element: Element) {
            if (!onMouseRightButtonClick(viewer, event, element)) {
                viewer.showPopupMenu(event, element, data)
            }
        }

        protected open fun onMouseRightButtonClick(viewer: StyledViewer, event: MouseEvent, element: Element): Boolean {
            return false
        }

        fun mouseEntered(viewer: StyledViewer, event: MouseEvent, element: Element) {
            if (!onMouseEnter(viewer, event, element)) {
                viewer.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            }
        }

        protected open fun onMouseEnter(viewer: StyledViewer, event: MouseEvent, element: Element): Boolean = false

        fun mouseExited(viewer: StyledViewer, event: MouseEvent, element: Element) {
            if (!onMouseExit(viewer, event, element)) {
                viewer.cursor = Cursor.getDefaultCursor()
            }
        }

        protected open fun onMouseExit(viewer: StyledViewer, event: MouseEvent, element: Element): Boolean = false
    }

    open class ColoredMouseListener internal constructor(
        color: Color,
        hoverColor: Color? = null,
        data: Any? = null
    ) : MouseListener(data) {

        private val regularAttributes = SimpleAttributeSet().apply {
            DefaultStyleConstants.setForeground(this, color)
        }
        private val hoverAttributes = SimpleAttributeSet().apply {
            DefaultStyleConstants.setForeground(this, hoverColor ?: color)
        }

        override fun onMouseEnter(viewer: StyledViewer, event: MouseEvent, element: Element): Boolean {
            with(element) {
                viewer.styledDocument.setCharacterAttributes(startOffset, offsetLength, hoverAttributes, false)
            }

            return super.onMouseEnter(viewer, event, element)
        }

        override fun onMouseExit(viewer: StyledViewer, event: MouseEvent, element: Element): Boolean {
            with(element) {
                viewer.styledDocument.setCharacterAttributes(startOffset, offsetLength, regularAttributes, false)
            }

            return super.onMouseExit(viewer, event, element)
        }

    }

    class FoldingMouseListener(
        data: Any? = null,
        private val onFoldingExpand: (viewer: StyledViewer, element: Element, data: Any?) -> Unit
    ) : MouseListener(data) {
        override fun onMouseClick(viewer: StyledViewer, event: MouseEvent, element: Element): Boolean {
            viewer.onBeforeFoldingExpandHandler?.invoke(element, data)
            onFoldingExpand(viewer, element, data)
            viewer.onFoldingExpandedHandler?.invoke(data)
            return true
        }

        override fun onMouseRightButtonClick(viewer: StyledViewer, event: MouseEvent, element: Element): Boolean = true
    }

    companion object {
        private inline val Element.offsetLength: Int
            get() = endOffset - startOffset
    }

}