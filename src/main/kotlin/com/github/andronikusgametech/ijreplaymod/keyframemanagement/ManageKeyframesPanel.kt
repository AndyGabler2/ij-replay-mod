package com.github.andronikusgametech.ijreplaymod.keyframemanagement

import com.github.andronikusgametech.ijreplaymod.CodingReplayBundle
import java.awt.BorderLayout
import java.awt.GridLayout
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTabbedPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.JTree
import javax.swing.ScrollPaneConstants
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class ManageKeyframesPanel(
    private val wrappers: List<KeyframeManagementWrapper>,
    private val fileName: String
): JPanel(GridLayout()), ActionListener, TreeSelectionListener {

    /*
     * LEFT PANEL
     */
    private val frameListPanel: JPanel
    private val toggleDeleteButton: JButton
    private val toggleDeleteFeedBackLabel: JLabel
    private val treePanel: JPanel
    private var treeElement: JTree? = null

    /*
     * RIGHT PANEL
     */
    private val frameDetailsPanel: JPanel
    private val updateButton: JButton
    private val updateButtonFeedBackLabel: JLabel
    private val frameLabelInput: JTextField
    private val frameOrderInput: JTextField
    private val frameTextInput: JTextArea

    /*
     * State data
     */
    private var selectedWrapper: KeyframeManagementWrapper? = null

    init {
        /*
         * Setup the the panels for Keyframe list and Frame details.
         */
        frameListPanel = JPanel(BorderLayout())
        frameListPanel.add(JLabel(CodingReplayBundle.getProperty("cr.ui.keyframeManagement.panel.keyFrames")), BorderLayout.NORTH)
        frameDetailsPanel = JPanel(BorderLayout())
        frameDetailsPanel.add(JLabel(CodingReplayBundle.getProperty("cr.ui.keyframeManagement.panel.frameDetails")), BorderLayout.NORTH)

        /*
         * Setup the top of the left panel for Keyframes
         */
        val frameListCenterPanel = JPanel(BorderLayout())
        toggleDeleteButton = JButton(CodingReplayBundle.getProperty("cr.ui.keyframeManagement.panel.toggleDelete"))
        toggleDeleteButton.addActionListener(this)
        toggleDeleteButton.isEnabled = false
        val frameListActionsPanel = JPanel(GridLayout(2, 1))
        val deleteButtonWrapper = JPanel()
        deleteButtonWrapper.add(toggleDeleteButton)
        frameListActionsPanel.add(deleteButtonWrapper)
        toggleDeleteFeedBackLabel = JLabel("")
        frameListActionsPanel.add(toggleDeleteFeedBackLabel)
        frameListCenterPanel.add(frameListActionsPanel, BorderLayout.PAGE_START)
        frameListPanel.add(frameListCenterPanel, BorderLayout.CENTER)

        /*
         * Setup the center of the left panel for keyframes
         */
        treePanel = JPanel(GridLayout(1, 1))
        updateTree()
        frameListCenterPanel.add(treePanel, BorderLayout.CENTER)

        /*
         * Setup panel on right for keyframe details.
         */
        val frameDetailCenterPanel = JPanel(BorderLayout())
        updateButton = JButton(CodingReplayBundle.getProperty("cr.ui.keyframeManagement.panel.update"))
        updateButton.addActionListener(this)
        updateButton.isEnabled = false
        val frameDetailActionsPanel = JPanel(GridLayout(2, 1))
        val buttonWrapper = JPanel()
        buttonWrapper.add(updateButton)
        frameDetailActionsPanel.add(buttonWrapper)
        updateButtonFeedBackLabel = JLabel("")
        frameDetailActionsPanel.add(updateButtonFeedBackLabel)
        frameDetailCenterPanel.add(frameDetailActionsPanel, BorderLayout.PAGE_START)
        frameDetailsPanel.add(frameDetailCenterPanel, BorderLayout.CENTER)

        /*
         * Setup detail cards.
         */
        val cardPanel = JTabbedPane()
        val metaDataPanel = JPanel(GridLayout(9, 2))
        metaDataPanel.add(JLabel(CodingReplayBundle.getProperty("cr.ui.keyframeManagement.panel.label")))
        frameLabelInput = JTextField("")
        frameLabelInput.isEditable = false
        metaDataPanel.add(frameLabelInput)
        metaDataPanel.add(JLabel(CodingReplayBundle.getProperty("cr.ui.keyframeManagement.panel.order")))
        frameOrderInput = JTextField("")
        frameOrderInput.isEditable = false
        metaDataPanel.add(frameOrderInput)
        val textPanel = JPanel(BorderLayout())
        frameTextInput = JTextArea("")
        frameTextInput.isEditable = false
        val scrollPane = JScrollPane(frameTextInput)
        scrollPane.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
        textPanel.add(scrollPane, BorderLayout.CENTER)
        cardPanel.addTab(CodingReplayBundle.getProperty("cr.ui.keyframeManagement.panel.frameDetails"), metaDataPanel)
        cardPanel.addTab(CodingReplayBundle.getProperty("cr.ui.keyframeManagement.panel.text"), textPanel)
        frameDetailCenterPanel.add(cardPanel, BorderLayout.CENTER)

        add(frameListPanel)
        add(frameDetailsPanel)
    }

    override fun actionPerformed(event: ActionEvent) {
        updateButtonFeedBackLabel.text = ""
        toggleDeleteFeedBackLabel.text = ""
        if (event.source == toggleDeleteButton) {
            toggleDeleteOfCurrentElement()
        } else if (event.source == updateButton) {
            updateCurrentWrapper()
        }
    }

    private fun toggleDeleteOfCurrentElement() {
        selectedWrapper!!.isMarkedForDelete = !selectedWrapper!!.isMarkedForDelete
    }

    private fun updateCurrentWrapper() {
        val rawLabel = frameLabelInput.text
        val rawOrder = frameOrderInput.text
        if (rawLabel.isNullOrEmpty()) {
            updateButtonFeedBackLabel.text = CodingReplayBundle.getProperty("cr.ui.keyframeManagement.feedback.noLabel")
            return
        } else if (rawOrder.isNullOrEmpty()) {
            updateButtonFeedBackLabel.text = CodingReplayBundle.getProperty("cr.ui.keyframeManagement.feedback.noOrder")
            return
        }

        val text = frameTextInput.text ?: ""
        val order = try {
            Integer.parseInt(rawOrder)
        } catch (exception: Exception) {
            updateButtonFeedBackLabel.text = CodingReplayBundle.getProperty("cr.ui.keyframeManagement.feedback.invalidOrder", rawOrder)
            return
        }

        selectedWrapper!!.text = text
        selectedWrapper!!.order = order
        selectedWrapper!!.label = rawLabel

        updateTree()
    }

    private fun updateTree() {

        val rootTreeModel = DefaultMutableTreeNode(
            CodingReplayBundle.getProperty("cr.ui.keyframeManagement.panel.treeRoot", fileName)
        )

        wrappers.sortedBy { wrapper -> wrapper.order }.forEach { wrapper ->
            val wrapperTreeNode = DefaultMutableTreeNode(wrapper)
            rootTreeModel.add(wrapperTreeNode)
        }

        val treeModel = DefaultTreeModel(rootTreeModel)
        if (treeElement == null) {
            treeElement = JTree(treeModel)
            treeElement!!.isLargeModel = true
            treeElement!!.dragEnabled = false
            treeElement!!.isEditable = false
            treeElement!!.addTreeSelectionListener(this)
            treePanel.add(treeElement)
        } else {
            treeElement!!.model = treeModel
        }
    }

    override fun valueChanged(event: TreeSelectionEvent) {
        // For tree listen
        if (event.source !is JTree) {
            return
        }

        val component = event.path.lastPathComponent
        if (component is DefaultMutableTreeNode) {
            if (component.userObject is KeyframeManagementWrapper) {
                selectedWrapper = component.userObject as KeyframeManagementWrapper
                toggleDeleteButton.isEnabled = true
                updateButton.isEnabled = true

                frameOrderInput.isEditable = true
                frameLabelInput.isEditable = true
                frameTextInput.isEditable = true

                frameOrderInput.text = "${selectedWrapper!!.order}"
                frameLabelInput.text = selectedWrapper!!.label
                frameTextInput.text = selectedWrapper!!.text
            }
        }
    }
}