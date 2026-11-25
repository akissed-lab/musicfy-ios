package com.example.musicfy.ui.screens.modules

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.musicfy.modules.ModuleRegistry
import com.example.musicfy.ui.component.GlassCard

/**
 * Portable replacement for the upstream module manager. iOS can only host data
 * and Compose contributors, so the catalog lists registrations and opens their
 * sheets without loading code, scanning archives or touching the file system.
 */
@Composable
fun ModuleCatalogScreen(
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val registrations by ModuleRegistry.entries.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Modules", style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            text = "iOS supports data-only modules: timed lyrics, queue views, song facts and Compose sheets. Code loading, archives, sockets and file access are not available.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(12.dp))
        if (registrations.isEmpty()) {
            Text("No modules registered.", style = MaterialTheme.typography.bodyMedium)
        }
        registrations.forEach { registration ->
            GlassCard(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(registration.descriptor.name, style = MaterialTheme.typography.titleMedium)
                if (registration.descriptor.description.isNotBlank()) {
                    Text(registration.descriptor.description, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    text = "v${registration.descriptor.version}  •  " +
                        registration.descriptor.capabilities.joinToString { it.name },
                    style = MaterialTheme.typography.labelSmall,
                )
                if (!registration.enabled) {
                    Text("Disabled", style = MaterialTheme.typography.labelSmall)
                }
                registration.sheets.forEach { sheet ->
                    Button(
                        onClick = { ModuleSheetRequests.open(registration.moduleId, sheet.id) },
                        enabled = registration.enabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(sheet.title)
                    }
                }
            }
        }
    }
}
