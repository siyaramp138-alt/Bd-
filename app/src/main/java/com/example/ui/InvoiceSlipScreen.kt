package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCartCheckout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.BdEntFormatter
import com.example.data.ExportImportUtils
import com.example.data.Order
import com.example.data.OrderPhotoHelper
import com.example.data.OrderStatus
import com.example.ui.theme.BdDeepNavy
import com.example.ui.theme.BdInkPrimaryLight
import com.example.ui.theme.BdInkSecondaryLight
import com.example.ui.theme.BdRoyalBlue
import com.example.ui.theme.LocalBdEntColors
import com.example.ui.theme.MonoCodeSmall
import com.example.ui.theme.MonoCurrencyLarge
import com.example.ui.theme.MonoCurrencyMedium
import kotlinx.coroutines.launch

private val SlipEstimateTeal = Color(0xFF0F766E)
private val SlipEstimateTealBg = Color(0xFFCCFBF1)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceSlipScreen(
    order: Order,
    isAdmin: Boolean,
    isBusy: Boolean = false,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onBack: () -> Unit,
    onEditOrder: () -> Unit = {},
    onUpdateStatus: (OrderStatus) -> Unit,
    onUpdatePhoto: (String) -> Unit = {},
    onConvertToOrder: () -> Unit = {},
    onDeleteOrder: () -> Unit,
    onSnackbarMessage: (String) -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val semantic = LocalBdEntColors.current
    val isEst = order.isEstimate()
    val headerBrandColor = if (isEst) SlipEstimateTeal else BdRoyalBlue
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showConvertConfirm by remember { mutableStateOf(false) }
    var showFullPhotoDialog by remember { mutableStateOf(false) }
    var showCameraXCapture by remember { mutableStateOf(false) }
    var isProcessingPhoto by remember { mutableStateOf(false) }
    var pendingPhotoBase64 by remember(order.id) { mutableStateOf<String?>(null) }
    val status = order.resolvedStatus()
    val canEditDetails = isAdmin || isEst || status != OrderStatus.DELIVERED
    val canEditPhoto = true

    LaunchedEffect(order.photoBase64) {
        if (pendingPhotoBase64 != null && pendingPhotoBase64 == order.photoBase64) {
            pendingPhotoBase64 = null
        }
    }

    val displayedPhotoBase64 = pendingPhotoBase64 ?: order.photoBase64
    val attachedBitmap = remember(displayedPhotoBase64) {
        if (displayedPhotoBase64.isNotBlank()) OrderPhotoHelper.decodeBase64ToBitmap(displayedPhotoBase64) else null
    }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                isProcessingPhoto = true
                OrderPhotoHelper.encodeUriToCompressedBase64(context, uri)
                    .onSuccess { encoded ->
                        pendingPhotoBase64 = encoded
                        onUpdatePhoto(encoded)
                    }
                    .onFailure { err -> onSnackbarMessage(err.message ?: "Unable to process photo") }
                isProcessingPhoto = false
            }
        }
    }

    if (showCameraXCapture) {
        CameraXCaptureScreen(
            title = stringResource(
                if (isEst) R.string.camerax_title_estimate else R.string.camerax_title_order
            ),
            onPhotoCaptured = { file ->
                showCameraXCapture = false
                coroutineScope.launch {
                    isProcessingPhoto = true
                    OrderPhotoHelper.encodeFileToCompressedBase64(context, file)
                        .onSuccess { encoded ->
                            pendingPhotoBase64 = encoded
                            onUpdatePhoto(encoded)
                        }
                        .onFailure { err -> onSnackbarMessage(err.message ?: "Unable to process captured photo") }
                    isProcessingPhoto = false
                }
            },
            onPickFromGallery = {
                galleryPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onDismiss = { showCameraXCapture = false }
        )
        return
    }

    if (showFullPhotoDialog && attachedBitmap != null) {
        AlertDialog(
            onDismissRequest = { showFullPhotoDialog = false },
            title = { Text("Attached Photo — ${order.orderNumber}") },
            text = {
                Image(
                    bitmap = attachedBitmap.asImageBitmap(),
                    contentDescription = "Full size attached photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            },
            confirmButton = {
                TextButton(onClick = { showFullPhotoDialog = false }) { Text("Close") }
            }
        )
    }

    if (showConvertConfirm) {
        AlertDialog(
            onDismissRequest = { showConvertConfirm = false },
            title = { Text("Convert to Sale Order?") },
            text = {
                Text("This will create a new confirmed Sale Order from Estimate ${order.orderNumber}, preserving all items, location, and attached photo.")
            },
            confirmButton = {
                Button(
                    enabled = !isBusy,
                    onClick = {
                        showConvertConfirm = false
                        onConvertToOrder()
                    }
                ) { Text("Convert to Order") }
            },
            dismissButton = {
                TextButton(onClick = { showConvertConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete ${if (isEst) "Estimate" else "Order"} ${order.orderNumber}?") },
            text = {
                Text(
                    if (isEst) {
                        "This permanently deletes the sale estimate record."
                    } else {
                        "This permanently deletes the order invoice record."
                    }
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteOrder()
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(if (isEst) "Estimate ${order.orderNumber}" else "Invoice ${order.orderNumber}")
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("invoice_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (canEditDetails) {
                        IconButton(
                            onClick = onEditOrder,
                            modifier = Modifier.testTag("edit_invoice_notes_button")
                        ) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = if (isEst) "Modify estimate" else "Modify order",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (isAdmin) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = "Delete record",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Print & Share PDF Action Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { ExportImportUtils.printInvoiceHtml(context, order) },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = headerBrandColor),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("print_invoice_button")
                    ) {
                        Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_print_invoice))
                    }
                    FilledTonalButton(
                        onClick = {
                            ExportImportUtils.generateAndShareInvoicePdf(context, order)
                                .onFailure { onSnackbarMessage("Unable to share PDF") }
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("share_invoice_pdf_button")
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.action_share_pdf))
                    }
                }
            }

            // Modify Order / Sale Estimate Button
            if (canEditDetails) {
                item {
                    OutlinedButton(
                        enabled = !isBusy,
                        onClick = onEditOrder,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.2.dp, headerBrandColor),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = headerBrandColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("modify_order_button")
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (isEst) "Modify Sale Estimate" else "Modify Order",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Estimate -> Convert to Sale Order Bar OR Order Status Strip
            if (isEst) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = SlipEstimateTealBg.copy(alpha = 0.65f),
                        border = BorderStroke(1.dp, SlipEstimateTeal.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Sale Estimate",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = SlipEstimateTeal
                                )
                                Text(
                                    text = "Ready to confirm? Convert to Sale Order.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BdInkSecondaryLight
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                enabled = !isBusy,
                                onClick = { showConvertConfirm = true },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SlipEstimateTeal),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("convert_estimate_to_order_button")
                            ) {
                                Icon(Icons.Filled.ShoppingCartCheckout, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("To Order", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            } else if (status != OrderStatus.DELIVERED) {
                item {
                    val nextStatus = if (status == OrderStatus.PENDING) OrderStatus.DISPATCHED else OrderStatus.DELIVERED
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Status:", style = MaterialTheme.typography.labelLarge)
                                OrderStatusPill(status = status)
                            }
                            FilledTonalButton(
                                onClick = { onUpdateStatus(nextStatus) },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    imageVector = if (nextStatus == OrderStatus.DISPATCHED) Icons.Filled.LocalShipping else Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Mark ${nextStatus.label}")
                            }
                        }
                    }
                }
            }

            // Crisp White Paper Invoice / Sale Estimate Slip
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    shadowElevation = 3.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Brand Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(headerBrandColor)
                                .padding(18.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "BD Ent",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (isEst) "SALE ESTIMATE / QUOTATION" else "TAX INVOICE / ORDER SLIP",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFDBEAFE)
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = order.orderNumber,
                                    style = MonoCodeSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = BdEntFormatter.formatDate(order.orderDateMillis),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFDBEAFE)
                                )
                                Spacer(Modifier.height(4.dp))
                                if (isEst) {
                                    StatusPill(
                                        text = "ESTIMATE",
                                        containerColor = Color.White,
                                        contentColor = SlipEstimateTeal
                                    )
                                } else {
                                    OrderStatusPill(status = status)
                                }
                            }
                        }

                        // Bill To / Estimate For Section
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Surface(
                                color = Color(0xFFF4F7FB),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = if (isEst) "ESTIMATE FOR" else "BILL TO",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = headerBrandColor
                                    )
                                    Text(
                                        text = order.partyName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = BdInkPrimaryLight
                                    )
                                    if (order.partyMobile.isNotBlank()) {
                                        Text(
                                            text = "Mobile: ${order.partyMobile}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = BdInkSecondaryLight
                                        )
                                    }
                                    if (order.partyGstOrAddress.isNotBlank()) {
                                        Text(
                                            text = order.partyGstOrAddress,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = BdInkSecondaryLight
                                        )
                                    }
                                    if (order.orderLocation.isNotBlank()) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.LocationOn,
                                                contentDescription = null,
                                                tint = headerBrandColor,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "Location: ${order.orderLocation}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = headerBrandColor,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                    if (order.createdByName.isNotBlank()) {
                                        Text(
                                            text = "Prepared by: ${order.createdByName}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = BdInkSecondaryLight
                                        )
                                    }
                                }
                            }

                            // Items Table Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFE7EEF8), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text("Item", style = MaterialTheme.typography.labelSmall, color = BdDeepNavy, modifier = Modifier.weight(2.2f))
                                Text("Qty", style = MaterialTheme.typography.labelSmall, color = BdDeepNavy, textAlign = TextAlign.End, modifier = Modifier.weight(0.9f))
                                Text("Rate", style = MaterialTheme.typography.labelSmall, color = BdDeepNavy, textAlign = TextAlign.End, modifier = Modifier.weight(1.1f))
                                Text("Total", style = MaterialTheme.typography.labelSmall, color = BdDeepNavy, textAlign = TextAlign.End, modifier = Modifier.weight(1.2f))
                            }

                            // Items Rows
                            order.items.forEach { item ->
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(2.2f)) {
                                            Text(item.productName, style = MaterialTheme.typography.bodyMedium, color = BdInkPrimaryLight, fontWeight = FontWeight.Medium)
                                            if (item.sku.isNotBlank()) {
                                                Text(item.sku, style = MonoCodeSmall, color = BdInkSecondaryLight)
                                            }
                                        }
                                        Text(
                                            text = "${BdEntFormatter.formatQty(item.quantity)} ${item.unit}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = BdInkPrimaryLight,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.weight(0.9f)
                                        )
                                        Text(
                                            text = BdEntFormatter.formatInr(item.unitPrice, showDecimals = false),
                                            style = MonoCodeSmall,
                                            color = BdInkSecondaryLight,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.weight(1.1f)
                                        )
                                        Text(
                                            text = BdEntFormatter.formatInr(item.lineTotal),
                                            style = MonoCodeSmall,
                                            color = BdInkPrimaryLight,
                                            fontWeight = FontWeight.SemiBold,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.weight(1.2f)
                                        )
                                    }
                                    HorizontalDivider(color = Color(0xFFE2E8F0))
                                }
                            }

                            // Totals Breakdown
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                InvoiceTotalLine("Subtotal", BdEntFormatter.formatInr(order.subtotal))
                                if (order.discountAmount > 0.0) {
                                    InvoiceTotalLine("Discount", "-${BdEntFormatter.formatInr(order.discountAmount)}")
                                }
                                if (order.taxAmount > 0.0) {
                                    InvoiceTotalLine("Tax (${BdEntFormatter.formatQty(order.taxPercent)}%)", BdEntFormatter.formatInr(order.taxAmount))
                                }
                                HorizontalDivider(color = headerBrandColor, thickness = 1.5.dp)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isEst) "Estimated Total" else "Grand Total",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = headerBrandColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(BdEntFormatter.formatInr(order.grandTotal), style = MonoCurrencyLarge, color = headerBrandColor)
                                }
                                if (!isEst && order.advancePaid > 0.0) {
                                    InvoiceTotalLine("Advance Paid", BdEntFormatter.formatInr(order.advancePaid), valueColor = semantic.success)
                                    InvoiceTotalLine(
                                        label = "Balance Due",
                                        value = BdEntFormatter.formatInr(order.balanceAfterAdvance()),
                                        valueColor = if (order.balanceAfterAdvance() > 0.01) semantic.danger else semantic.success,
                                        bold = true
                                    )
                                }
                            }

                            if (order.notes.isNotBlank()) {
                                Surface(
                                    color = Color(0xFFF8FAFC),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Notes: ${order.notes}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = BdInkSecondaryLight,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Attached Camera / Gallery Photo Card (view, attach, replace, or remove)
            if (attachedBitmap != null || canEditPhoto) {
                item {
                    val isSyncingPhoto = isProcessingPhoto || (pendingPhotoBase64 != null && isBusy)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("invoice_attached_photo_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PhotoCamera,
                                        contentDescription = null,
                                        tint = headerBrandColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = if (attachedBitmap != null) {
                                            "Attached Photo (Tap to enlarge)"
                                        } else {
                                            stringResource(R.string.photo_attachment_title)
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (attachedBitmap != null && !isSyncingPhoto && order.hasPhoto()) {
                                    StatusPill(
                                        text = "SAVED",
                                        containerColor = semantic.successContainer,
                                        contentColor = semantic.success
                                    )
                                }
                            }

                            if (isSyncingPhoto) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(vertical = 6.dp)
                                ) {
                                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                                    Text("Saving photo to cloud...", style = MaterialTheme.typography.bodySmall)
                                }
                            }

                            if (attachedBitmap != null) {
                                Image(
                                    bitmap = attachedBitmap.asImageBitmap(),
                                    contentDescription = "Attached camera photo for ${order.orderNumber}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(210.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { showFullPhotoDialog = true }
                                        .testTag("invoice_attached_photo_image")
                                )
                                if (canEditPhoto) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilledTonalButton(
                                            enabled = !isBusy && !isProcessingPhoto,
                                            onClick = { showCameraXCapture = true },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("invoice_update_photo_button")
                                        ) {
                                            Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Camera")
                                        }
                                        OutlinedButton(
                                            enabled = !isBusy && !isProcessingPhoto,
                                            onClick = {
                                                galleryPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("invoice_replace_gallery_button")
                                        ) {
                                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Gallery")
                                        }
                                        OutlinedButton(
                                            enabled = !isBusy && !isProcessingPhoto,
                                            onClick = {
                                                pendingPhotoBase64 = ""
                                                onUpdatePhoto("")
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error
                                            ),
                                            modifier = Modifier.testTag("invoice_remove_photo_button")
                                        ) {
                                            Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(R.string.action_remove_photo), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            } else if (canEditPhoto) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilledTonalButton(
                                        enabled = !isBusy && !isProcessingPhoto,
                                        onClick = { showCameraXCapture = true },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("invoice_attach_camera_button")
                                    ) {
                                        Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.action_attach_photo))
                                    }
                                    OutlinedButton(
                                        enabled = !isBusy && !isProcessingPhoto,
                                        onClick = {
                                            galleryPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("invoice_attach_gallery_button")
                                    ) {
                                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.action_gallery_photo))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun InvoiceTotalLine(
    label: String,
    value: String,
    valueColor: Color = BdInkPrimaryLight,
    bold: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            color = if (bold) BdInkPrimaryLight else BdInkSecondaryLight
        )
        Text(
            text = value,
            style = MonoCurrencyMedium,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            color = valueColor
        )
    }
}
