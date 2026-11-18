package com.openswipe.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.widget.Toast
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openswipe.ui.viewmodel.HomeViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
) {
    val config by viewModel.configState.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── Edge trigger width settings ──
        Text(
            text = "Left/right edge trigger width",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = "Trigger area width", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "10", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = config.edgeTriggerWidthDp,
                        onValueChange = { viewModel.setEdgeTriggerWidth(it) },
                        valueRange = 10f..50f,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    )
                    Text(text = "50", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    text = "${config.edgeTriggerWidthDp.roundToInt()}dp",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Bottom trigger settings (kept) ──
        Text(
            text = "Bottom trigger area",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Height slider
                Text(text = "Trigger area height", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "20", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = config.bottomTriggerHeightDp,
                        onValueChange = { viewModel.setBottomTriggerHeight(it) },
                        valueRange = 20f..80f,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    )
                    Text(text = "80", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    text = "${config.bottomTriggerHeightDp.roundToInt()}dp",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Keyboard avoidance ──
        Text(
            text = "Keyboard",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text("Pause bottom gesture while keyboard is open") },
                    supportingContent = { Text("Lets you tap the keyboard's bottom row (space, enter)") },
                    trailingContent = {
                        Switch(
                            checked = config.disableBottomOnIme,
                            onCheckedChange = { viewModel.setDisableBottomOnIme(it) },
                        )
                    },
                )
                ListItem(
                    headlineContent = { Text("Pause side gestures while keyboard is open") },
                    supportingContent = { Text("Lets you tap keys at the screen edges") },
                    trailingContent = {
                        Switch(
                            checked = config.disableSidesOnIme,
                            onCheckedChange = { viewModel.setDisableSidesOnIme(it) },
                        )
                    },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Config file ──
        Text(
            text = "Config file",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = viewModel.configFilePath,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        viewModel.importConfig { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                    }) { Text("Import") }
                    OutlinedButton(onClick = {
                        viewModel.exportConfig { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                    }) { Text("Export") }
                }
            }
        }
    }
}
