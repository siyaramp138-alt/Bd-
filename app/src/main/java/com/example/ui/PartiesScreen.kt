package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.BdEntFormatter
import com.example.data.ExportImportUtils
import com.example.data.Order
import com.example.data.Party
import com.example.data.PartyLedgerSummary
import com.example.ui.theme.LocalBdEntColors
import com.example.ui.theme.MonoCodeSmall
import com.example.ui.theme.MonoCurrencyLarge
import com.example.ui.theme.MonoCurrencyMedium
import com.example.viewmodel.BdEntUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartiesScreen(
    state: BdEntUiState.Ready,
    isAdmin: Boolean,
    onAddParty: () -> Unit,
    onEditParty: (Party) -> Unit,
    onDeleteParty: (Party) -> Unit,
    onOpenOrderInvoice: (Order) -> Unit,
    onSnackbarMessage: (String) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedLedgerPartyId by remember { mutableStateOf<String?>(null) }
    var partyToDelete by remember { mutableStateOf<Party?>(null) }

    val filtered = remember(state.partySummaries, searchQuery) {
        val q = searchQuery.trim().lowercase()
        state.partySummaries.filter { summary ->
            q.isEmpty() ||
                summary.party.name.lowercase().contains(q) ||
                summary.party.mobile.lowercase().contains(q) ||
                summary.party.gstOrAddress.lowercase().contains(q)
        }.sortedBy { it.party.name.lowercase() }
    }

    // Confirm Delete Party Dialog
    partyToDelete?.let { party ->
        AlertDialog(
            onDismissRequest = { partyToDelete = null },
            title = { Text("Delete Party?") },
            text = {
                Text("Remove \"${party.name}\" from your BD Ent directory? Existing orders will retain their party name snapshot.")
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        onDeleteParty(party)
                        partyToDelete = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { partyToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // Party Order History Sheet
    selectedLedgerPartyId?.let { pid ->
        val summary = state.partySummaries.firstOrNull { it.party.id == pid }
        if (summary != null) {
            val partyOrders = state.orders.filter { it.partyId == pid }
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { selectedLedgerPartyId = null },
                sheetState = sheetState
            ) {
                PartyOrdersDetailContent(
                    summary = summary,
                    orders = partyOrders,
                    onWhatsApp = {
                        ExportImportUtils.launchWhatsAppReminder(context, summary)
                            .onFailure { onSnackbarMessage("Unable to open WhatsApp") }
                    },
                    onOpenOrder = { order ->
                        selectedLedgerPartyId = null
                        onOpenOrderInvoice(order)
                    }
                )
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header Directory Card
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Party Directory",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${state.parties.size} Parties",
                            style = MonoCurrencyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Customers & suppliers linked to orders",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = onAddParty,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("add_party_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Party")
                    }
                }
            }
        }

        // Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search party name, mobile, address...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("parties_search_input")
            )
        }

        if (filtered.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Filled.Groups,
                    title = if (state.parties.isEmpty()) "No parties added yet" else "No matching parties",
                    subtitle = if (state.parties.isEmpty()) {
                        "Add customers and suppliers to quickly create orders and sale estimates."
                    } else {
                        "Try clearing your search query."
                    },
                    ctaLabel = if (state.parties.isEmpty()) "+ Add First Party" else null,
                    onCtaClick = if (state.parties.isEmpty()) onAddParty else null
                )
            }
        } else {
            items(filtered, key = { it.party.id }) { summary ->
                PartyCard(
                    summary = summary,
                    isAdmin = isAdmin,
                    onOpenDetails = { selectedLedgerPartyId = summary.party.id },
                    onWhatsApp = {
                        ExportImportUtils.launchWhatsAppReminder(context, summary)
                            .onFailure { onSnackbarMessage("Unable to open WhatsApp") }
                    },
                    onEdit = { onEditParty(summary.party) },
                    onDelete = { partyToDelete = summary.party }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(72.dp)) }
    }
}

@Composable
private fun PartyCard(
    summary: PartyLedgerSummary,
    isAdmin: Boolean,
    onOpenDetails: () -> Unit,
    onWhatsApp: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val semantic = LocalBdEntColors.current

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetails)
            .testTag("party_card_${summary.party.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = summary.party.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        StatusPill(
                            text = summary.party.resolvedType().label,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (summary.party.mobile.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Phone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = summary.party.mobile,
                                style = MonoCodeSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (summary.party.gstOrAddress.isNotBlank()) {
                        Text(
                            text = summary.party.gstOrAddress,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit ${summary.party.name}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (isAdmin) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Delete ${summary.party.name}",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Orders Summary + WhatsApp Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Orders (${summary.orderCount})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = BdEntFormatter.formatInr(summary.totalOrderAmount, showDecimals = false),
                        style = MonoCurrencyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                FilledTonalButton(
                    onClick = onWhatsApp,
                    shape = RoundedCornerShape(11.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = semantic.whatsAppContainer,
                        contentColor = semantic.whatsApp
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("whatsapp_remind_btn_${summary.party.id}")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_whatsapp_remind),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun PartyOrdersDetailContent(
    summary: PartyLedgerSummary,
    orders: List<Order>,
    onWhatsApp: () -> Unit,
    onOpenOrder: (Order) -> Unit
) {
    val semantic = LocalBdEntColors.current
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = summary.party.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (summary.party.mobile.isNotBlank() || summary.party.gstOrAddress.isNotBlank()) {
                    Text(
                        text = listOf(summary.party.mobile, summary.party.gstOrAddress)
                            .filter { it.isNotBlank() }
                            .joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                FilledTonalButton(
                    onClick = onWhatsApp,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = semantic.whatsAppContainer,
                        contentColor = semantic.whatsApp
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Message on WhatsApp")
                }
            }
        }

        item { HorizontalDivider() }

        item {
            Text(
                text = "Orders & Estimates (${orders.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        if (orders.isEmpty()) {
            item {
                Text(
                    text = "No orders or estimates placed for this party yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(orders, key = { "ord_${it.id}" }) { order ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenOrder(order) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = order.orderNumber,
                                style = MonoCodeSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = buildString {
                                    append(BdEntFormatter.formatDate(order.orderDateMillis))
                                    if (order.orderLocation.isNotBlank()) append(" • ${order.orderLocation}")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (order.isEstimate()) {
                                StatusPill(
                                    text = "ESTIMATE",
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            } else {
                                OrderStatusPill(status = order.resolvedStatus())
                            }
                            Text(
                                text = BdEntFormatter.formatInr(order.grandTotal),
                                style = MonoCurrencyMedium
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
