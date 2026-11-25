package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.BdEntFormatter
import com.example.data.ITEM_UNIT_PRESETS
import com.example.data.OrderStatus
import com.example.data.Party
import com.example.data.PartyLedgerSummary
import com.example.data.PartyType
import com.example.data.PaymentMode
import com.example.data.Product
import com.example.ui.theme.LocalBdEntColors
import com.example.ui.theme.MonoCurrencyMedium

@Composable
fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun OrderStatusPill(status: OrderStatus, modifier: Modifier = Modifier) {
    val semantic = LocalBdEntColors.current
    val (bg, fg) = when (status) {
        OrderStatus.PENDING -> semantic.warningContainer to semantic.warning
        OrderStatus.DISPATCHED -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        OrderStatus.DELIVERED -> semantic.successContainer to semantic.success
    }
    StatusPill(text = status.label.uppercase(), containerColor = bg, contentColor = fg, modifier = modifier)
}

@Composable
fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold
        )
        if (actionLabel != null && onActionClick != null) {
            TextButton(
                onClick = onActionClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    ctaLabel: String? = null,
    onCtaClick: (() -> Unit)? = null,
    ctaTag: String? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (ctaLabel != null && onCtaClick != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onCtaClick,
                    shape = RoundedCornerShape(12.dp),
                    modifier = if (ctaTag != null) Modifier.testTag(ctaTag) else Modifier
                ) {
                    Text(ctaLabel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BdEntDatePickerDialog(
    initialMillis: Long,
    onDismiss: () -> Unit,
    onDateSelected: (Long) -> Unit
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val selected = state.selectedDateMillis ?: initialMillis
                    onDateSelected(selected)
                }
            ) {
                Text("Select")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PartyFormDialog(
    editingParty: Party?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        existingId: String?,
        name: String,
        mobile: String,
        gstOrAddress: String,
        partyType: PartyType,
        openingBalance: Double
    ) -> Unit
) {
    var name by remember(editingParty) { mutableStateOf(editingParty?.name ?: "") }
    var mobile by remember(editingParty) { mutableStateOf(editingParty?.mobile ?: "") }
    var gstOrAddress by remember(editingParty) { mutableStateOf(editingParty?.gstOrAddress ?: "") }
    var partyType by remember(editingParty) { mutableStateOf(editingParty?.resolvedType() ?: PartyType.CUSTOMER) }
    var openingBalanceText by remember(editingParty) {
        mutableStateOf(
            if (editingParty != null && editingParty.openingBalance != 0.0) {
                editingParty.openingBalance.toString()
            } else {
                ""
            }
        )
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (editingParty == null) "Add New Party" else "Edit Party",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; validationError = null },
                    label = { Text("Party / Company Name *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("party_name_input")
                )
                OutlinedTextField(
                    value = mobile,
                    onValueChange = { mobile = it.take(20) },
                    label = { Text("Mobile / WhatsApp Number") },
                    placeholder = { Text("e.g. 9876543210") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("party_mobile_input")
                )
                OutlinedTextField(
                    value = gstOrAddress,
                    onValueChange = { gstOrAddress = it.take(300) },
                    label = { Text("GSTIN / Billing Address") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("party_gst_input")
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Party Type",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PartyType.entries.forEach { type ->
                            FilterChip(
                                selected = partyType == type,
                                onClick = { partyType = type },
                                label = { Text(type.label) }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = openingBalanceText,
                    onValueChange = { openingBalanceText = it },
                    label = { Text("Opening Balance (₹ Receivable)") },
                    placeholder = { Text("0.00") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("party_opening_balance_input")
                )
                if (validationError != null) {
                    Text(
                        text = validationError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isBusy,
                modifier = Modifier.testTag("save_party_button"),
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isEmpty()) {
                        validationError = "Please enter the party name."
                        return@Button
                    }
                    val balance = openingBalanceText.trim().toDoubleOrNull()
                    if (openingBalanceText.isNotBlank() && balance == null) {
                        validationError = "Enter a valid number for opening balance."
                        return@Button
                    }
                    onSave(
                        editingParty?.id,
                        trimmed,
                        mobile.trim(),
                        gstOrAddress.trim(),
                        partyType,
                        balance ?: 0.0
                    )
                }
            ) {
                Text(if (editingParty == null) "Save Party" else "Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductFormDialog(
    editingProduct: Product?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        existingId: String?,
        name: String,
        sku: String,
        unit: String,
        category: String,
        location: String,
        purchaseRate: Double,
        sellingRate: Double,
        stockQty: Double,
        minAlertLevel: Double
    ) -> Unit
) {
    val unitPresets = remember(editingProduct) {
        val current = editingProduct?.unit?.trim().orEmpty()
        if (current.isNotEmpty() && ITEM_UNIT_PRESETS.none { it.equals(current, ignoreCase = true) }) {
            ITEM_UNIT_PRESETS + current
        } else {
            ITEM_UNIT_PRESETS
        }
    }
    var name by remember(editingProduct) { mutableStateOf(editingProduct?.name ?: "") }
    var sku by remember(editingProduct) { mutableStateOf(editingProduct?.sku ?: "") }
    var unit by remember(editingProduct) { mutableStateOf(editingProduct?.unit ?: "Pcs") }
    var sellingRateText by remember(editingProduct) {
        mutableStateOf(editingProduct?.sellingRate?.let { if (it > 0.0) it.toString() else "" } ?: "")
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (editingProduct == null) "Add Item" else "Edit Item",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; validationError = null },
                    label = { Text("Item Name *") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("product_name_input")
                )
                OutlinedTextField(
                    value = sku,
                    onValueChange = { sku = it.take(60) },
                    label = { Text("SKU / Item Code (Optional)") },
                    placeholder = { Text("BDE-101") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("product_sku_input")
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Unit",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        unitPresets.forEach { preset ->
                            FilterChip(
                                selected = unit.equals(preset, ignoreCase = true),
                                onClick = { unit = preset },
                                label = { Text(preset) },
                                modifier = Modifier.testTag("unit_chip_${preset.lowercase()}")
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = sellingRateText,
                    onValueChange = { sellingRateText = it; validationError = null },
                    label = { Text("Selling Rate (₹) *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("product_selling_rate_input")
                )
                if (validationError != null) {
                    Text(
                        text = validationError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isBusy,
                modifier = Modifier.testTag("save_product_button"),
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isEmpty()) {
                        validationError = "Please enter the item name."
                        return@Button
                    }
                    val selling = sellingRateText.trim().toDoubleOrNull()
                    if (selling == null || selling < 0.0) {
                        validationError = "Enter a valid selling rate (₹)."
                        return@Button
                    }
                    onSave(
                        editingProduct?.id,
                        trimmed,
                        sku.trim(),
                        unit.trim().ifBlank { "Pcs" },
                        "",
                        editingProduct?.location ?: "",
                        editingProduct?.purchaseRate ?: 0.0,
                        selling,
                        editingProduct?.stockQty ?: 0.0,
                        editingProduct?.minAlertLevel ?: 0.0
                    )
                }
            ) {
                Text(if (editingProduct == null) "Save Item" else "Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReceivePaymentDialog(
    partySummaries: List<PartyLedgerSummary>,
    initialPartyId: String?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onRecordPayment: (
        party: Party,
        amount: Double,
        mode: PaymentMode,
        dateMillis: Long,
        note: String
    ) -> Unit
) {
    val semantic = LocalBdEntColors.current
    var selectedSummary by remember(partySummaries, initialPartyId) {
        mutableStateOf(
            partySummaries.firstOrNull { it.party.id == initialPartyId }
                ?: partySummaries.firstOrNull { it.pendingDue > 0.01 }
                ?: partySummaries.firstOrNull()
        )
    }
    var partyMenuExpanded by remember { mutableStateOf(false) }
    var amountText by remember(selectedSummary?.party?.id) {
        mutableStateOf(
            selectedSummary?.pendingDue?.takeIf { it > 0.01 }?.let {
                String.format(java.util.Locale.US, "%.2f", it)
            } ?: ""
        )
    }
    var paymentMode by remember { mutableStateOf(PaymentMode.UPI) }
    var dateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    if (showDatePicker) {
        BdEntDatePickerDialog(
            initialMillis = dateMillis,
            onDismiss = { showDatePicker = false },
            onDateSelected = {
                dateMillis = it
                showDatePicker = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Receive / Record Payment",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (partySummaries.isEmpty()) {
                    Text(
                        text = "Please add a Party first before recording a payment.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedSummary?.party?.name ?: "Select Party",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select Party *") },
                            trailingIcon = {
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Choose party")
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { partyMenuExpanded = true }
                                .testTag("payment_party_selector")
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { partyMenuExpanded = true }
                        )
                        DropdownMenu(
                            expanded = partyMenuExpanded,
                            onDismissRequest = { partyMenuExpanded = false }
                        ) {
                            partySummaries.forEach { summary ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(summary.party.name)
                                            Spacer(Modifier.width(12.dp))
                                            Text(
                                                text = BdEntFormatter.formatInr(summary.pendingDue),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (summary.pendingDue > 0.01) semantic.danger else semantic.success
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedSummary = summary
                                        partyMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    selectedSummary?.let { summary ->
                        Surface(
                            color = if (summary.pendingDue > 0.01) semantic.dangerContainer else semantic.successContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Current Pending Due",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (summary.pendingDue > 0.01) semantic.danger else semantic.success
                                )
                                Text(
                                    text = BdEntFormatter.formatInr(summary.pendingDue),
                                    style = MonoCurrencyMedium,
                                    color = if (summary.pendingDue > 0.01) semantic.danger else semantic.success
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it; validationError = null },
                        label = { Text("Amount Received (₹) *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("payment_amount_input")
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Payment Mode",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PaymentMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = paymentMode == mode,
                                    onClick = { paymentMode = mode },
                                    label = { Text(mode.label) }
                                )
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CalendarToday,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Date: ${BdEntFormatter.formatDate(dateMillis)}")
                    }

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(240) },
                        label = { Text("Reference / UTR / Note (Optional)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("payment_note_input")
                    )

                    if (validationError != null) {
                        Text(
                            text = validationError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isBusy && selectedSummary != null,
                modifier = Modifier.testTag("confirm_record_payment_button"),
                onClick = {
                    val target = selectedSummary?.party
                    val amt = amountText.trim().toDoubleOrNull()
                    if (target == null) {
                        validationError = "Select a party."
                        return@Button
                    }
                    if (amt == null || amt <= 0.0) {
                        validationError = "Enter a payment amount greater than ₹0."
                        return@Button
                    }
                    onRecordPayment(target, amt, paymentMode, dateMillis, note.trim())
                }
            ) {
                Text("Record Payment")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
