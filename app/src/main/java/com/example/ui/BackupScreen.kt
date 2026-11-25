package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AccessResolution
import com.example.data.BOOTSTRAP_ADMIN_EMAIL
import com.example.data.BackupPayload
import com.example.data.BdEntFormatter
import com.example.data.ExportImportUtils
import com.example.data.StaffMember
import com.example.data.StaffRole
import com.example.ui.theme.LocalBdEntColors
import com.example.viewmodel.BdEntUiState

private data class PendingFileExport(
    val content: String,
    val successMessage: String
)

@Composable
fun BackupScreen(
    state: BdEntUiState.Ready,
    access: AccessResolution.Authorized,
    isBusy: Boolean,
    onSaveStaff: (email: String, name: String, role: StaffRole, active: Boolean, isUpdate: Boolean, onSuccess: () -> Unit) -> Unit,
    onToggleStaffActive: (email: String, active: Boolean) -> Unit,
    onDeleteStaff: (email: String) -> Unit,
    onRestoreBackup: (BackupPayload) -> Unit,
    onSignOut: () -> Unit,
    onSnackbarMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val semantic = LocalBdEntColors.current
    val isAdmin = access.role == StaffRole.ADMIN

    var pendingExport by remember { mutableStateOf<PendingFileExport?>(null) }
    var pendingRestorePreview by remember { mutableStateOf<BackupPayload?>(null) }

    // Admin Staff Management Dialog States
    var showStaffDialog by remember { mutableStateOf(false) }
    var editingStaff by remember { mutableStateOf<StaffMember?>(null) }
    var staffToDelete by remember { mutableStateOf<StaffMember?>(null) }

    val saveCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        val export = pendingExport
        pendingExport = null
        if (uri != null && export != null) {
            ExportImportUtils.writeTextToUri(context, uri, export.content)
                .onSuccess { onSnackbarMessage(export.successMessage) }
                .onFailure { onSnackbarMessage("Failed to save CSV: ${it.message}") }
        }
    }

    val saveJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val export = pendingExport
        pendingExport = null
        if (uri != null && export != null) {
            ExportImportUtils.writeTextToUri(context, uri, export.content)
                .onSuccess { onSnackbarMessage(export.successMessage) }
                .onFailure { onSnackbarMessage("Failed to save JSON backup: ${it.message}") }
        }
    }

    val openJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            ExportImportUtils.readTextFromUri(context, uri)
                .onSuccess { rawJson ->
                    ExportImportUtils.parseJsonBackup(rawJson)
                        .onSuccess { parsed ->
                            pendingRestorePreview = parsed
                        }
                        .onFailure {
                            onSnackbarMessage("Invalid BD Ent JSON backup file.")
                        }
                }
                .onFailure {
                    onSnackbarMessage("Could not read selected file.")
                }
        }
    }

    if (showStaffDialog && isAdmin) {
        StaffFormDialog(
            editingStaff = editingStaff,
            isBusy = isBusy,
            onDismiss = {
                showStaffDialog = false
                editingStaff = null
            },
            onSave = { email, name, role, active, isUpdate ->
                onSaveStaff(email, name, role, active, isUpdate) {
                    showStaffDialog = false
                    editingStaff = null
                }
            }
        )
    }

    staffToDelete?.let { member ->
        AlertDialog(
            onDismissRequest = { staffToDelete = null },
            title = { Text("Remove Staff Access?") },
            text = {
                Text("Remove ${member.name} (${member.email}) from BD Enterprises Indore? They will lose access immediately.")
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        onDeleteStaff(member.email)
                        staffToDelete = null
                    }
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { staffToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // Confirmation dialog before restoring JSON backup into Firestore
    pendingRestorePreview?.let { backup ->
        AlertDialog(
            onDismissRequest = { pendingRestorePreview = null },
            title = { Text("Restore BD Ent Backup?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "This will merge and restore the following records into your BD Enterprises cloud ledger:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text("• Parties: ${backup.parties.size}", fontWeight = FontWeight.SemiBold)
                    Text("• Items: ${backup.products.size}", fontWeight = FontWeight.SemiBold)
                    Text("• Orders: ${backup.orders.size}", fontWeight = FontWeight.SemiBold)
                    Text("• Payments: ${backup.payments.size}", fontWeight = FontWeight.SemiBold)
                }
            },
            confirmButton = {
                Button(
                    enabled = !isBusy,
                    onClick = {
                        val payload = backup
                        pendingRestorePreview = null
                        onRestoreBackup(payload)
                    }
                ) { Text("Restore Now") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestorePreview = null }) { Text("Cancel") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Signed-in Google Account & Cloud Sync Card
        item {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isAdmin) Icons.Filled.AdminPanelSettings else Icons.Filled.Badge,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = access.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = access.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        StatusPill(
                            text = if (access.isBootstrapOwner) "OWNER ADMIN" else access.role.name,
                            containerColor = if (isAdmin) MaterialTheme.colorScheme.primaryContainer else semantic.successContainer,
                            contentColor = if (isAdmin) MaterialTheme.colorScheme.primary else semantic.success
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Filled.CloudDone,
                            contentDescription = null,
                            tint = semantic.success,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Cloud Synced • ${state.parties.size} Parties • ${state.products.size} Items • ${state.orders.size} Orders",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = onSignOut,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.action_sign_out))
                    }
                }
            }
        }

        // ADMIN ONLY: Team & Staff Access Management (Admin / Employee Login Control)
        if (isAdmin) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Team & Login Access",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Add Employee or Admin Gmail accounts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            editingStaff = null
                            showStaffDialog = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("add_staff_button")
                    ) {
                        Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Staff", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // Bootstrapped Owner Admin Card (Always active & protected)
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Primary Owner Admin",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = BOOTSTRAP_ADMIN_EMAIL,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        StatusPill(
                            text = "OWNER",
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        )
                    }
                }
            }

            val managedStaff = state.staff.filterNot { it.email.equals(BOOTSTRAP_ADMIN_EMAIL, ignoreCase = true) }
            if (managedStaff.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No employees or extra admins added yet. Tap \"Add Staff\" and enter their Gmail address so they can sign in with Employee or Admin access.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            } else {
                items(managedStaff, key = { "staff_${it.email}" }) { member ->
                    StaffMemberCard(
                        member = member,
                        onToggleActive = { active -> onToggleStaffActive(member.email, active) },
                        onEdit = {
                            editingStaff = member
                            showStaffDialog = true
                        },
                        onDelete = { staffToDelete = member }
                    )
                }
            }
        }

        // CSV / Excel Exports Section
        item {
            Text(
                text = "Export CSV / Excel Reports",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            ExportCard(
                icon = Icons.Filled.ReceiptLong,
                title = "Orders Report (CSV)",
                subtitle = "${state.orders.size} orders with subtotal, GST, discount, grand total & advance",
                saveTag = "export_orders_csv_save",
                shareTag = "export_orders_csv_share",
                onSave = {
                    val csv = ExportImportUtils.buildOrdersCsv(state.orders)
                    pendingExport = PendingFileExport(csv, "Orders CSV exported")
                    saveCsvLauncher.launch("BD_Ent_Orders_${BdEntFormatter.fileTimestamp()}.csv")
                },
                onShare = {
                    val csv = ExportImportUtils.buildOrdersCsv(state.orders)
                    ExportImportUtils.shareTextFile(
                        context = context,
                        fileName = "BD_Ent_Orders_${BdEntFormatter.fileTimestamp()}.csv",
                        mimeType = "text/csv",
                        content = csv,
                        chooserTitle = "Share BD Ent Orders CSV"
                    ).onFailure { onSnackbarMessage("Unable to share CSV") }
                }
            )
        }

        item {
            ExportCard(
                icon = Icons.Filled.Inventory2,
                title = "Items Catalog Report (CSV)",
                subtitle = "${state.products.size} items with SKU, unit & selling rates",
                saveTag = "export_stock_csv_save",
                shareTag = "export_stock_csv_share",
                onSave = {
                    val csv = ExportImportUtils.buildStockCsv(state.products)
                    pendingExport = PendingFileExport(csv, "Items CSV exported")
                    saveCsvLauncher.launch("BD_Ent_Items_${BdEntFormatter.fileTimestamp()}.csv")
                },
                onShare = {
                    val csv = ExportImportUtils.buildStockCsv(state.products)
                    ExportImportUtils.shareTextFile(
                        context = context,
                        fileName = "BD_Ent_Items_${BdEntFormatter.fileTimestamp()}.csv",
                        mimeType = "text/csv",
                        content = csv,
                        chooserTitle = "Share BD Ent Items CSV"
                    ).onFailure { onSnackbarMessage("Unable to share CSV") }
                }
            )
        }

        item {
            ExportCard(
                icon = Icons.Filled.Groups,
                title = "Party Dues Ledger (CSV)",
                subtitle = "${state.partySummaries.size} parties with opening balance, orders, paid & pending due",
                saveTag = "export_dues_csv_save",
                shareTag = "export_dues_csv_share",
                onSave = {
                    val csv = ExportImportUtils.buildPartyDuesCsv(state.partySummaries)
                    pendingExport = PendingFileExport(csv, "Party Dues CSV exported")
                    saveCsvLauncher.launch("BD_Ent_Party_Dues_${BdEntFormatter.fileTimestamp()}.csv")
                },
                onShare = {
                    val csv = ExportImportUtils.buildPartyDuesCsv(state.partySummaries)
                    ExportImportUtils.shareTextFile(
                        context = context,
                        fileName = "BD_Ent_Party_Dues_${BdEntFormatter.fileTimestamp()}.csv",
                        mimeType = "text/csv",
                        content = csv,
                        chooserTitle = "Share BD Ent Party Dues CSV"
                    ).onFailure { onSnackbarMessage("Unable to share CSV") }
                }
            )
        }

        // JSON Full Backup & Restore Section
        item {
            Text(
                text = if (isAdmin) "Full JSON Backup & Restore" else "Full JSON Backup",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (isAdmin) {
                            "Download a complete JSON snapshot of all BD Enterprises parties, items, orders, and payments — or restore from a previous backup."
                        } else {
                            "Download a complete JSON backup of BD Enterprises data. Only Admins can run JSON Restore."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                val json = ExportImportUtils.buildJsonBackup(
                                    parties = state.parties,
                                    products = state.products,
                                    orders = state.orders,
                                    payments = state.payments
                                )
                                pendingExport = PendingFileExport(json, "BD Ent JSON backup saved")
                                saveJsonLauncher.launch("BD_Ent_Backup_${BdEntFormatter.fileTimestamp()}.json")
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("backup_json_save_button")
                        ) {
                            Icon(Icons.Filled.SaveAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Backup JSON")
                        }
                        FilledTonalButton(
                            onClick = {
                                val json = ExportImportUtils.buildJsonBackup(
                                    parties = state.parties,
                                    products = state.products,
                                    orders = state.orders,
                                    payments = state.payments
                                )
                                ExportImportUtils.shareTextFile(
                                    context = context,
                                    fileName = "BD_Ent_Backup_${BdEntFormatter.fileTimestamp()}.json",
                                    mimeType = "application/json",
                                    content = json,
                                    chooserTitle = "Share BD Ent JSON Backup"
                                ).onFailure { onSnackbarMessage("Unable to share JSON backup") }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("backup_json_share_button")
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = "Share JSON backup", modifier = Modifier.size(18.dp))
                        }
                    }
                    if (isAdmin) {
                        OutlinedButton(
                            enabled = !isBusy,
                            onClick = { openJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("restore_json_button")
                        ) {
                            Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (isBusy) "Restoring Backup..." else "Restore from JSON Backup")
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(72.dp)) }
    }
}

@Composable
private fun StaffMemberCard(
    member: StaffMember,
    onToggleActive: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val semantic = LocalBdEntColors.current
    val role = member.resolvedRole()
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (member.active) MaterialTheme.colorScheme.outlineVariant else semantic.warning.copy(alpha = 0.6f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("staff_card_${member.email}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (role == StaffRole.ADMIN) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.secondaryContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (role == StaffRole.ADMIN) Icons.Filled.AdminPanelSettings else Icons.Filled.Badge,
                            contentDescription = null,
                            tint = if (role == StaffRole.ADMIN) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = member.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            StatusPill(
                                text = role.name,
                                containerColor = if (role == StaffRole.ADMIN) MaterialTheme.colorScheme.primaryContainer else semantic.successContainer,
                                contentColor = if (role == StaffRole.ADMIN) MaterialTheme.colorScheme.primary else semantic.success
                            )
                        }
                        Text(
                            text = member.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Switch(
                    checked = member.active,
                    onCheckedChange = onToggleActive
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (member.active) "Active — Can sign in with Gmail" else "Suspended — Access blocked",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (member.active) semantic.success else semantic.danger
                )
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Edit ${member.name}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = "Remove ${member.name}",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StaffFormDialog(
    editingStaff: StaffMember?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (email: String, name: String, role: StaffRole, active: Boolean, isUpdate: Boolean) -> Unit
) {
    val isUpdate = editingStaff != null
    var name by remember(editingStaff) { mutableStateOf(editingStaff?.name.orEmpty()) }
    var email by remember(editingStaff) { mutableStateOf(editingStaff?.email.orEmpty()) }
    var role by remember(editingStaff) { mutableStateOf(editingStaff?.resolvedRole() ?: StaffRole.EMPLOYEE) }
    var active by remember(editingStaff) { mutableStateOf(editingStaff?.active ?: true) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isUpdate) "Edit Staff Access" else "Add Employee / Admin") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Full Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("staff_name_input")
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; error = null },
                    label = { Text("Gmail Address") },
                    placeholder = { Text("employee@gmail.com") },
                    enabled = !isUpdate,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("staff_email_input")
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Login Role",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = role == StaffRole.EMPLOYEE,
                            onClick = { role = StaffRole.EMPLOYEE },
                            leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("Employee") },
                            modifier = Modifier.testTag("staff_role_employee")
                        )
                        FilterChip(
                            selected = role == StaffRole.ADMIN,
                            onClick = { role = StaffRole.ADMIN },
                            leadingIcon = { Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text("Admin") },
                            modifier = Modifier.testTag("staff_role_admin")
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Account Active", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = active, onCheckedChange = { active = it })
                }
                if (error != null) {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isBusy,
                onClick = {
                    val cleanName = name.trim()
                    val cleanEmail = email.trim().lowercase()
                    if (cleanName.isBlank()) {
                        error = "Enter staff name."
                        return@Button
                    }
                    if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) {
                        error = "Enter a valid Gmail address."
                        return@Button
                    }
                    onSave(cleanEmail, cleanName, role, active, isUpdate)
                },
                modifier = Modifier.testTag("save_staff_button")
            ) {
                Text(if (isUpdate) "Save Changes" else "Grant Access")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ExportCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    saveTag: String,
    shareTag: String,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onSave,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag(saveTag)
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Save CSV", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onShare,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag(shareTag)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Share", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
