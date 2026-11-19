package com.openswipe.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.openswipe.model.GestureType
import com.openswipe.model.SectionRange
import com.openswipe.model.TriggerMode
import com.openswipe.overlay.Edge
import com.openswipe.ui.component.ActionPickerDialog
import com.openswipe.ui.viewmodel.RuleConfigViewModel
import com.openswipe.ui.util.actionIcon
import com.openswipe.ui.util.edgeIcon
import com.openswipe.ui.util.edgeLabel
import com.openswipe.ui.util.gestureLabel
import com.openswipe.ui.util.sectionLabel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleDetailScreen(
    ruleId: String,
    viewModel: RuleConfigViewModel,
    onNavigateBack: () -> Unit,
) {
    val rules by viewModel.rules.collectAsState()
    val rule = rules.find { it.id == ruleId }

    var showActionPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showEdgeMenu by remember { mutableStateOf(false) }
    var showSectionMenu by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rule details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.applyRules()
                        scope.launch {
                            snackbarHostState.showSnackbar("Saved")
                        }
                        onNavigateBack()
                    }) {
                        Icon(Icons.Filled.Check, contentDescription = "Save")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (rule == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Rule not found", style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Enable/Disable ──
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text("Enable rule") },
                    supportingContent = {
                        Text(if (rule.enabled) "Enabled" else "Disabled")
                    },
                    trailingContent = {
                        Switch(
                            checked = rule.enabled,
                            onCheckedChange = { viewModel.toggleRuleEnabled(ruleId) },
                        )
                    },
                )
            }

            // ── Trigger Condition ──
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Trigger",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(12.dp))

                    // Edge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Edge", modifier = Modifier.weight(1f))
                        androidx.compose.foundation.layout.Box {
                            OutlinedButton(onClick = { showEdgeMenu = true }) {
                                Text("${edgeIcon(rule.trigger.edge)} ${edgeLabel(rule.trigger.edge)}")
                            }
                            DropdownMenu(
                                expanded = showEdgeMenu,
                                onDismissRequest = { showEdgeMenu = false },
                            ) {
                                Edge.entries.forEach { edge ->
                                    DropdownMenuItem(
                                        text = { Text("${edgeIcon(edge)} ${edgeLabel(edge)}") },
                                        onClick = {
                                            viewModel.updateRuleTrigger(
                                                ruleId,
                                                rule.trigger.copy(edge = edge),
                                            )
                                            showEdgeMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Section
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Section", modifier = Modifier.weight(1f))
                        val sectionOptions = SectionRange.presets(rule.trigger.edge)
                        androidx.compose.foundation.layout.Box {
                            OutlinedButton(onClick = { showSectionMenu = true }) {
                                Text(sectionLabel(rule.trigger.section, rule.trigger.edge))
                            }
                            DropdownMenu(
                                expanded = showSectionMenu,
                                onDismissRequest = { showSectionMenu = false },
                            ) {
                                sectionOptions.forEach { (label, section) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            viewModel.updateRuleTrigger(
                                                ruleId,
                                                rule.trigger.copy(section = section),
                                            )
                                            showSectionMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Gesture type (read-only, always SWIPE)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Gesture type", modifier = Modifier.weight(1f))
                        Text(
                            text = gestureLabel(rule.trigger.gestureType),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── Trigger Mode ──
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Trigger mode",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.selectableGroup()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = rule.triggerMode == TriggerMode.SWIPE,
                                    onClick = { viewModel.updateRuleTriggerMode(ruleId, TriggerMode.SWIPE) },
                                    role = Role.RadioButton,
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = rule.triggerMode == TriggerMode.SWIPE, onClick = null)
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text("Swipe", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Triggers on swipe only; taps pass through to the app below",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = rule.triggerMode == TriggerMode.TOUCH,
                                    onClick = { viewModel.updateRuleTriggerMode(ruleId, TriggerMode.TOUCH) },
                                    role = Role.RadioButton,
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = rule.triggerMode == TriggerMode.TOUCH, onClick = null)
                            Column(modifier = Modifier.padding(start = 8.dp)) {
                                Text("Tap", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Triggers on touch (responds immediately)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // ── Action ──
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text("Action") },
                    supportingContent = {
                        Text("${actionIcon(rule.action)} ${rule.action.label}")
                    },
                    trailingContent = {
                        OutlinedButton(onClick = { showActionPicker = true }) {
                            Text("Change")
                        }
                    },
                )
            }

            // ── Delete ──
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = { showDeleteConfirm = true },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Delete rule")
            }
        }
    }

    // ── Dialogs ──

    if (showActionPicker) {
        ActionPickerDialog(
            onDismiss = { showActionPicker = false },
            onSelect = { action ->
                viewModel.updateRuleAction(ruleId, action)
                showActionPicker = false
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Confirm delete") },
            text = { Text("Delete this rule? This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeRule(ruleId)
                    showDeleteConfirm = false
                    onNavigateBack()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}
