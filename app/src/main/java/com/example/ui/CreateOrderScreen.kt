package com.example.ui

import android.Manifest
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.BdEntFormatter
import com.example.data.DeviceLocationHelper
import com.example.data.ITEM_UNIT_PRESETS
import com.example.data.Order
import com.example.data.OrderDocumentType
import com.example.data.OrderLineItem
import com.example.data.OrderLocationGroup
import com.example.data.OrderPhotoHelper
import com.example.data.OrderStatus
import com.example.data.Party
import com.example.data.PaymentMode
import com.example.data.Product
import com.example.ui.theme.LocalBdEntColors
import com.example.ui.theme.MonoCurrencyLarge
import com.example.ui.theme.MonoCurrencyMedium
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

private val EstimateTeal = Color(0xFF0F766E)
private val EstimateTealBg = Color(0xFFCCFBF1)

private data class DraftLineItem(
    val uid: Long,
    val product: Product,
    val unit: String = product.unit.ifBlank { "Pcs" },
    val quantityText: String,
    val unitPriceText: String
) {
    val qty: Double get() = quantityText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val rate: Double get() = unitPriceText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val lineTotal: Double get() = qty * rate
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateOrderScreen(
    editingOrder: Order? = null,
    isAdmin: Boolean = true,
    initialDocumentType: OrderDocumentType = OrderDocumentType.ORDER,
    suggestedOrderNumber: String,
    suggestedEstimateNumber: String,
    parties: List<Party>,
    products: List<Product>,
    existingLocations: List<String> = emptyList(),
    initialLocation: String = "",
    isBusy: Boolean,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onBack: () -> Unit,
    onQuickAddParty: () -> Unit,
    onQuickAddProduct: () -> Unit,
    onSubmitOrder: (
        orderNumber: String,
        party: Party,
        documentType: OrderDocumentType,
        orderLocation: String,
        orderDateMillis: Long,
        status: OrderStatus,
        items: List<OrderLineItem>,
        taxPercent: Double,
        discountAmount: Double,
        advancePaid: Double,
        advanceMode: PaymentMode,
        notes: String,
        photoBase64: String
    ) -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val semantic = LocalBdEntColors.current

    val isEditing = editingOrder != null
    val initialDocType = editingOrder?.resolvedDocumentType() ?: initialDocumentType
    val initialLoc = editingOrder?.orderLocation ?: initialLocation

    var documentType by remember(editingOrder?.id, initialDocType) { mutableStateOf(initialDocType) }
    val isEstimate = documentType == OrderDocumentType.ESTIMATE
    val currentSuggestedNumber = if (isEstimate) suggestedEstimateNumber else suggestedOrderNumber

    var orderNumber by remember(editingOrder?.id, initialDocType, suggestedOrderNumber, suggestedEstimateNumber) {
        mutableStateOf(
            editingOrder?.orderNumber?.takeIf { it.isNotBlank() }
                ?: if (initialDocType == OrderDocumentType.ESTIMATE) suggestedEstimateNumber else suggestedOrderNumber
        )
    }
    var selectedPartyId by remember(editingOrder?.id, parties) {
        mutableStateOf(editingOrder?.partyId?.takeIf { it.isNotBlank() } ?: parties.firstOrNull()?.id ?: "")
    }
    val selectedParty = parties.firstOrNull { it.id == selectedPartyId }
        ?: editingOrder?.let { existing ->
            Party(
                id = existing.partyId,
                name = existing.partyName,
                mobile = existing.partyMobile,
                gstOrAddress = existing.partyGstOrAddress
            )
        }
        ?: parties.firstOrNull()
    var partyMenuExpanded by remember { mutableStateOf(false) }

    var selectedLocationGroup by remember(editingOrder?.id, initialLoc) {
        mutableStateOf(
            OrderLocationGroup.fromLocationText(initialLoc) ?: OrderLocationGroup.LOCAL_INDORE
        )
    }
    var orderLocationDetail by remember(editingOrder?.id, initialLoc) {
        val parsedGroup = OrderLocationGroup.fromLocationText(initialLoc)
        mutableStateOf(OrderLocationGroup.extractAreaDetail(initialLoc, parsedGroup))
    }
    var isDetectingLocation by remember { mutableStateOf(false) }
    var locationStatusMessage by remember { mutableStateOf<String?>(null) }

    // CameraX / Gallery Photo Attachment State (draft cache path survives Activity recreation)
    var draftPhotoCachePath by rememberSaveable(editingOrder?.id) { mutableStateOf("") }
    var photoBase64 by remember(editingOrder?.id) { mutableStateOf(editingOrder?.photoBase64 ?: "") }
    var isProcessingPhoto by remember { mutableStateOf(false) }
    var photoStatusMessage by remember(editingOrder?.id) {
        mutableStateOf(
            if (editingOrder?.hasPhoto() == true) "Existing photo loaded" else null
        )
    }
    var showCameraXCapture by remember { mutableStateOf(false) }
    var showFullPhotoPreview by remember { mutableStateOf(false) }

    LaunchedEffect(draftPhotoCachePath) {
        if (draftPhotoCachePath.isNotBlank() && photoBase64.isBlank()) {
            val restored = OrderPhotoHelper.loadDraftPhotoFromCache(draftPhotoCachePath)
            if (restored.isNotBlank()) {
                photoBase64 = restored
                photoStatusMessage = "Photo attached & ready to save"
            }
        }
    }

    val previewBitmap = remember(photoBase64) {
        OrderPhotoHelper.decodeBase64ToBitmap(photoBase64)
    }

    fun processSelectedPhotoUri(uri: Uri) {
        coroutineScope.launch {
            isProcessingPhoto = true
            photoStatusMessage = null
            OrderPhotoHelper.encodeUriToCompressedBase64(context, uri)
                .onSuccess { encoded ->
                    OrderPhotoHelper.deleteDraftPhotoCache(draftPhotoCachePath)
                    draftPhotoCachePath = OrderPhotoHelper.saveDraftPhotoToCache(context, encoded)
                    photoBase64 = encoded
                    photoStatusMessage = "Photo attached & ready to save"
                }
                .onFailure { err ->
                    photoStatusMessage = err.message ?: "Unable to process photo"
                }
            isProcessingPhoto = false
        }
    }

    fun processCapturedPhotoFile(file: File) {
        coroutineScope.launch {
            isProcessingPhoto = true
            photoStatusMessage = null
            OrderPhotoHelper.encodeFileToCompressedBase64(context, file)
                .onSuccess { encoded ->
                    OrderPhotoHelper.deleteDraftPhotoCache(draftPhotoCachePath)
                    draftPhotoCachePath = OrderPhotoHelper.saveDraftPhotoToCache(context, encoded)
                    photoBase64 = encoded
                    photoStatusMessage = "Camera photo attached & ready to save"
                }
                .onFailure { err ->
                    photoStatusMessage = err.message ?: "Unable to process captured photo"
                }
            isProcessingPhoto = false
        }
    }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            processSelectedPhotoUri(uri)
        }
    }

    val quickLocations = remember(existingLocations, selectedLocationGroup) {
        val defaults = when (selectedLocationGroup) {
            OrderLocationGroup.LOCAL_INDORE -> listOf("Vijay Nagar", "Dewas Naka", "Palasia", "Rajwada", "Sapna Sangeeta")
            OrderLocationGroup.OUT_OF_INDORE -> listOf("Pithampur", "Ujjain", "Dewas", "Bhopal", "Ratlam")
            OrderLocationGroup.SHOP_ORDER -> listOf("Main Counter", "Showroom", "Godown Pickup")
        }
        val matchingExisting = existingLocations
            .filter { OrderLocationGroup.fromLocationText(it) == selectedLocationGroup }
            .map { OrderLocationGroup.extractAreaDetail(it, selectedLocationGroup) }
        (matchingExisting + defaults)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .take(8)
    }

    fun triggerGpsLookup() {
        coroutineScope.launch {
            isDetectingLocation = true
            locationStatusMessage = null
            DeviceLocationHelper.fetchCurrentLocationLabel(context)
                .onSuccess { detected ->
                    val detectedGroup = OrderLocationGroup.fromLocationText(detected)
                    if (detectedGroup != null) {
                        selectedLocationGroup = detectedGroup
                    }
                    orderLocationDetail = OrderLocationGroup.extractAreaDetail(detected, detectedGroup)
                    locationStatusMessage = "Location detected from GPS"
                }
                .onFailure { err ->
                    locationStatusMessage = err.message ?: "Unable to detect location"
                }
            isDetectingLocation = false
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            triggerGpsLookup()
        } else {
            locationStatusMessage = "Location permission denied. You can type or pick an area below."
        }
    }

    var orderDateMillis by remember(editingOrder?.id) {
        mutableLongStateOf(editingOrder?.orderDateMillis?.takeIf { it > 0L } ?: System.currentTimeMillis())
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var orderStatus by remember(editingOrder?.id) {
        mutableStateOf(editingOrder?.resolvedStatus() ?: OrderStatus.PENDING)
    }

    val draftItems = remember(editingOrder?.id) {
        mutableStateListOf<DraftLineItem>().apply {
            editingOrder?.items?.forEachIndexed { index, item ->
                val matchedProduct = products.firstOrNull { it.id == item.productId }
                    ?: Product(
                        id = item.productId,
                        name = item.productName,
                        sku = item.sku,
                        unit = item.unit.ifBlank { "Pcs" },
                        sellingRate = item.unitPrice
                    )
                add(
                    DraftLineItem(
                        uid = (index + 1).toLong(),
                        product = matchedProduct,
                        unit = item.unit.ifBlank { matchedProduct.unit.ifBlank { "Pcs" } },
                        quantityText = BdEntFormatter.formatQty(item.quantity),
                        unitPriceText = item.unitPrice.toString()
                    )
                )
            }
        }
    }
    var nextLineUid by remember(editingOrder?.id) {
        mutableLongStateOf((editingOrder?.items?.size ?: 0).toLong() + 1L)
    }
    var showProductPicker by remember { mutableStateOf(false) }

    var discountText by remember(editingOrder?.id) {
        mutableStateOf(
            editingOrder?.discountAmount?.takeIf { it > 0.0 }?.toString() ?: ""
        )
    }
    var notes by remember(editingOrder?.id) { mutableStateOf(editingOrder?.notes ?: "") }
    var validationError by remember { mutableStateOf<String?>(null) }

    val subtotal = draftItems.sumOf { it.lineTotal }
    val discountAmt = (discountText.toDoubleOrNull() ?: 0.0).coerceIn(0.0, subtotal)
    val grandTotal = maxOf(0.0, subtotal - discountAmt)

    if (showCameraXCapture) {
        CameraXCaptureScreen(
            title = stringResource(
                if (isEstimate) R.string.camerax_title_estimate else R.string.camerax_title_order
            ),
            onPhotoCaptured = { file ->
                showCameraXCapture = false
                processCapturedPhotoFile(file)
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

    if (showFullPhotoPreview && previewBitmap != null) {
        AlertDialog(
            onDismissRequest = { showFullPhotoPreview = false },
            title = { Text("Attached Photo Preview") },
            text = {
                Image(
                    bitmap = previewBitmap.asImageBitmap(),
                    contentDescription = "Full size attached photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            },
            confirmButton = {
                TextButton(onClick = { showFullPhotoPreview = false }) { Text("Close") }
            }
        )
    }

    if (showDatePicker) {
        BdEntDatePickerDialog(
            initialMillis = orderDateMillis,
            onDismiss = { showDatePicker = false },
            onDateSelected = {
                orderDateMillis = it
                showDatePicker = false
            }
        )
    }

    if (showProductPicker) {
        StockItemPickerDialog(
            products = products,
            isAdmin = isAdmin,
            onDismiss = { showProductPicker = false },
            onQuickAddProduct = {
                showProductPicker = false
                onQuickAddProduct()
            },
            onSelectProduct = { product ->
                val existingIdx = draftItems.indexOfFirst { it.product.id == product.id }
                if (existingIdx >= 0) {
                    val existing = draftItems[existingIdx]
                    val newQty = existing.qty + 1.0
                    draftItems[existingIdx] = existing.copy(quantityText = BdEntFormatter.formatQty(newQty))
                } else {
                    draftItems.add(
                        DraftLineItem(
                            uid = nextLineUid++,
                            product = product,
                            quantityText = "1",
                            unitPriceText = product.sellingRate.toString()
                        )
                    )
                }
                validationError = null
                showProductPicker = false
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when {
                            isEditing && isEstimate -> "Edit Sale Estimate"
                            isEditing -> "Edit Order"
                            isEstimate -> stringResource(R.string.create_estimate_title)
                            else -> stringResource(R.string.create_order_title)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("create_order_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Document Type Switcher (Sale Order vs Sale Estimate)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterChip(
                            selected = !isEstimate,
                            onClick = {
                                if (documentType != OrderDocumentType.ORDER) {
                                    documentType = OrderDocumentType.ORDER
                                    if (orderNumber.isBlank() || orderNumber == suggestedEstimateNumber) {
                                        orderNumber = suggestedOrderNumber
                                    }
                                }
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = { Text("Sale Order") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("create_type_order")
                        )
                        FilterChip(
                            selected = isEstimate,
                            onClick = {
                                if (documentType != OrderDocumentType.ESTIMATE) {
                                    documentType = OrderDocumentType.ESTIMATE
                                    if (orderNumber.isBlank() || orderNumber == suggestedOrderNumber) {
                                        orderNumber = suggestedEstimateNumber
                                    }
                                }
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.RequestQuote, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = { Text("Sale Estimate") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("create_type_estimate")
                        )
                    }
                    if (isEstimate) {
                        Surface(
                            color = EstimateTealBg.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, EstimateTeal.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Sale Estimate mode — Share quotation first and convert to a Sale Order anytime.",
                                style = MaterialTheme.typography.bodySmall,
                                color = EstimateTeal,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // Order / Estimate Header: Party, #, Date, Location, Status
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isEstimate) "Estimate Details" else "Order Details",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = onQuickAddParty) {
                                Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("New Party")
                            }
                        }

                        if (parties.isEmpty()) {
                            Surface(
                                color = semantic.warningContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Add a party first to assign this ${if (isEstimate) "estimate" else "order"}.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = semantic.warning,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = onQuickAddParty) { Text("+ Add") }
                                }
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = selectedParty?.name ?: "Select Party",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Select Party *") },
                                    trailingIcon = {
                                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Select party")
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("order_party_selector")
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
                                    parties.forEach { p ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(p.name, fontWeight = FontWeight.Medium)
                                                    if (p.mobile.isNotBlank()) {
                                                        Text(
                                                            p.mobile,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                selectedPartyId = p.id
                                                partyMenuExpanded = false
                                                validationError = null
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = orderNumber,
                                onValueChange = { orderNumber = it.take(40) },
                                label = { Text(if (isEstimate) "Estimate #" else "Order / Invoice #") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("order_number_input")
                            )
                            OutlinedButton(
                                onClick = { showDatePicker = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(56.dp)
                                    .align(Alignment.Bottom)
                            ) {
                                Icon(Icons.Filled.CalendarToday, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(BdEntFormatter.formatDate(orderDateMillis), style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        // Location Group (Local Indore / Out of Indore / Shop Order) + Optional Area Detail
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Location Group *",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OrderLocationGroup.entries.forEach { group ->
                                    val groupIcon = when (group) {
                                        OrderLocationGroup.LOCAL_INDORE -> Icons.Filled.LocationCity
                                        OrderLocationGroup.OUT_OF_INDORE -> Icons.Filled.LocalShipping
                                        OrderLocationGroup.SHOP_ORDER -> Icons.Filled.Storefront
                                    }
                                    FilterChip(
                                        selected = selectedLocationGroup == group,
                                        onClick = {
                                            selectedLocationGroup = group
                                            locationStatusMessage = null
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = groupIcon,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        label = { Text(group.label) },
                                        modifier = Modifier.testTag("order_loc_group_${group.name.lowercase(Locale.ROOT)}")
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = orderLocationDetail,
                                onValueChange = {
                                    orderLocationDetail = it.take(120)
                                    locationStatusMessage = null
                                },
                                label = {
                                    Text(
                                        when (selectedLocationGroup) {
                                            OrderLocationGroup.LOCAL_INDORE -> "Indore Area / Locality (Optional)"
                                            OrderLocationGroup.OUT_OF_INDORE -> "City / Town Name (Optional)"
                                            OrderLocationGroup.SHOP_ORDER -> "Counter / Shop Note (Optional)"
                                        }
                                    )
                                },
                                placeholder = {
                                    Text(
                                        when (selectedLocationGroup) {
                                            OrderLocationGroup.LOCAL_INDORE -> "e.g. Vijay Nagar, Palasia"
                                            OrderLocationGroup.OUT_OF_INDORE -> "e.g. Ujjain, Pithampur, Bhopal"
                                            OrderLocationGroup.SHOP_ORDER -> "e.g. Main Counter"
                                        }
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.LocationOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                trailingIcon = {
                                    if (selectedLocationGroup != OrderLocationGroup.SHOP_ORDER) {
                                        if (isDetectingLocation) {
                                            CircularProgressIndicator(
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        } else {
                                            IconButton(
                                                onClick = {
                                                    if (DeviceLocationHelper.hasLocationPermission(context)) {
                                                        triggerGpsLookup()
                                                    } else {
                                                        locationPermissionLauncher.launch(
                                                            arrayOf(
                                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                                            )
                                                        )
                                                    }
                                                },
                                                modifier = Modifier.testTag("order_use_gps_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.MyLocation,
                                                    contentDescription = stringResource(R.string.cd_detect_gps_location),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("order_location_input")
                            )

                            if (locationStatusMessage != null) {
                                Text(
                                    text = locationStatusMessage ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val partyAddr = selectedParty?.gstOrAddress?.trim().orEmpty()
                                if (partyAddr.isNotBlank() && selectedLocationGroup != OrderLocationGroup.SHOP_ORDER) {
                                    item {
                                        FilterChip(
                                            selected = orderLocationDetail.equals(partyAddr.take(120), ignoreCase = true),
                                            onClick = { orderLocationDetail = partyAddr.take(120) },
                                            label = { Text("Party Address", maxLines = 1) }
                                        )
                                    }
                                }
                                items(quickLocations) { loc ->
                                    FilterChip(
                                        selected = orderLocationDetail.trim().equals(loc, ignoreCase = true),
                                        onClick = {
                                            orderLocationDetail = loc
                                            locationStatusMessage = null
                                        },
                                        label = { Text(loc, maxLines = 1) }
                                    )
                                }
                            }
                        }

                        if (!isEstimate) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Order Status", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OrderStatus.entries.forEach { st ->
                                        FilterChip(
                                            selected = orderStatus == st,
                                            onClick = { orderStatus = st },
                                            label = { Text(st.label) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Camera Photo Attachment Card (For both Order & Sale Estimate)
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("order_photo_card")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PhotoCamera,
                                contentDescription = null,
                                tint = if (isEstimate) EstimateTeal else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = stringResource(R.string.photo_attachment_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Capture a live camera photo of items, handwritten slip, or shopfront to attach with this ${if (isEstimate) "sale estimate" else "order"}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (isProcessingPhoto) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                                Text("Compressing photo for cloud sync...", style = MaterialTheme.typography.bodySmall)
                            }
                        } else if (previewBitmap != null) {
                            Image(
                                bitmap = previewBitmap.asImageBitmap(),
                                contentDescription = "Captured attachment photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(175.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { showFullPhotoPreview = true }
                                    .testTag("order_photo_preview")
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = { showCameraXCapture = true },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("order_retake_photo_button")
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.action_retake_photo))
                                }
                                OutlinedButton(
                                    onClick = {
                                        OrderPhotoHelper.deleteDraftPhotoCache(draftPhotoCachePath)
                                        draftPhotoCachePath = ""
                                        photoBase64 = ""
                                        photoStatusMessage = null
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("order_remove_photo_button")
                                ) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.action_remove_photo))
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = { showCameraXCapture = true },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("order_camera_photo_button")
                                ) {
                                    Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.action_camera_photo))
                                }
                                OutlinedButton(
                                    onClick = {
                                        galleryPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("order_gallery_photo_button")
                                ) {
                                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.action_gallery_photo))
                                }
                            }
                        }

                        if (photoStatusMessage != null) {
                            Text(
                                text = photoStatusMessage ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (photoBase64.isNotBlank()) semantic.success else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Line Items Section
            item {
                val accentColor = if (isEstimate) EstimateTeal else MaterialTheme.colorScheme.primary
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${if (isEstimate) "Estimate" else "Order"} Items",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isEstimate) EstimateTealBg else MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "${draftItems.size} ${if (draftItems.size == 1) "item" else "items"}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isEstimate) EstimateTeal else MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Button(
                        onClick = { showProductPicker = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("add_order_line_item_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.action_add_item),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (draftItems.isEmpty()) {
                item {
                    val accentColor = if (isEstimate) EstimateTeal else MaterialTheme.colorScheme.primary
                    val cardTint = if (isEstimate) {
                        EstimateTealBg.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = cardTint,
                        border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showProductPicker = true }
                            .testTag("empty_add_order_item_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = accentColor,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = stringResource(R.string.action_add_item),
                                        tint = Color.White,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Tap here to add items",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Item selling rate auto-fills when selected.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(draftItems, key = { it.uid }) { line ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = line.product.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val itemMeta = listOfNotNull(
                                        line.product.sku.takeIf { it.isNotBlank() }?.let { "SKU: $it" },
                                        "Unit: ${line.unit}"
                                    ).joinToString(" • ")
                                    Text(
                                        text = itemMeta,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                var unitMenuExpanded by remember(line.uid) { mutableStateOf(false) }
                                val lineUnitOptions = remember(line.unit) {
                                    if (ITEM_UNIT_PRESETS.any { it.equals(line.unit, ignoreCase = true) }) {
                                        ITEM_UNIT_PRESETS
                                    } else {
                                        ITEM_UNIT_PRESETS + line.unit
                                    }
                                }
                                Box {
                                    OutlinedButton(
                                        onClick = { unitMenuExpanded = true },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("line_item_unit_selector_${line.uid}")
                                    ) {
                                        Text(
                                            text = line.unit,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(Modifier.width(2.dp))
                                        Icon(
                                            imageVector = Icons.Filled.KeyboardArrowDown,
                                            contentDescription = "Select item unit",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = unitMenuExpanded,
                                        onDismissRequest = { unitMenuExpanded = false }
                                    ) {
                                        lineUnitOptions.forEach { unitOption ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = unitOption,
                                                        fontWeight = if (line.unit.equals(unitOption, ignoreCase = true)) {
                                                            FontWeight.Bold
                                                        } else {
                                                            FontWeight.Normal
                                                        },
                                                        color = if (line.unit.equals(unitOption, ignoreCase = true)) {
                                                            MaterialTheme.colorScheme.primary
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurface
                                                        }
                                                    )
                                                },
                                                onClick = {
                                                    val idx = draftItems.indexOfFirst { it.uid == line.uid }
                                                    if (idx >= 0) draftItems[idx] = line.copy(unit = unitOption)
                                                    unitMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = { draftItems.removeAll { it.uid == line.uid } },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.DeleteOutline,
                                        contentDescription = "Remove item",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = line.quantityText,
                                    onValueChange = { newVal ->
                                        val idx = draftItems.indexOfFirst { it.uid == line.uid }
                                        if (idx >= 0) draftItems[idx] = line.copy(quantityText = newVal)
                                    },
                                    label = { Text("Qty (${line.unit})") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = line.unitPriceText,
                                    onValueChange = { newVal ->
                                        val idx = draftItems.indexOfFirst { it.uid == line.uid }
                                        if (idx >= 0) draftItems[idx] = line.copy(unitPriceText = newVal)
                                    },
                                    label = { Text("Rate (₹)") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f)
                                )
                                Column(
                                    horizontalAlignment = Alignment.End,
                                    modifier = Modifier.width(86.dp)
                                ) {
                                    Text("Total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = BdEntFormatter.formatInr(line.lineTotal, showDecimals = false),
                                        style = MonoCurrencyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    FilledTonalButton(
                        onClick = { showProductPicker = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = if (isEstimate) {
                            ButtonDefaults.filledTonalButtonColors(
                                containerColor = EstimateTealBg,
                                contentColor = EstimateTeal
                            )
                        } else {
                            ButtonDefaults.filledTonalButtonColors()
                        },
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("add_more_order_item_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.action_add_more_item),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Pricing Summary & Notes Card (No GST rate, No Payment option)
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
                            text = if (isEstimate) "Estimate Summary & Notes" else "Order Summary & Notes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = discountText,
                            onValueChange = { discountText = it },
                            label = { Text("Discount (₹) — Optional") },
                            placeholder = { Text("0") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("order_discount_input")
                        )

                        HorizontalDivider()

                        SummaryCalcRow("Subtotal", BdEntFormatter.formatInr(subtotal))
                        if (discountAmt > 0.0) {
                            SummaryCalcRow("Discount", "-${BdEntFormatter.formatInr(discountAmt)}")
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isEstimate) "Estimated Total" else "Grand Total",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = BdEntFormatter.formatInr(grandTotal),
                                style = MonoCurrencyLarge,
                                color = if (isEstimate) EstimateTeal else MaterialTheme.colorScheme.primary
                            )
                        }

                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it.take(500) },
                            label = {
                                Text(if (isEstimate) "Estimate Terms / Notes (Optional)" else "Order / Delivery Notes (Optional)")
                            },
                            minLines = 2,
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (validationError != null) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = validationError ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            item {
                Button(
                    enabled = !isBusy && !isProcessingPhoto,
                    shape = RoundedCornerShape(14.dp),
                    colors = if (isEstimate) {
                        ButtonDefaults.buttonColors(containerColor = EstimateTeal, contentColor = Color.White)
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("submit_create_order_button"),
                    onClick = {
                        val party = selectedParty
                        if (party == null) {
                            validationError = "Please select or add a Party first."
                            return@Button
                        }
                        if (draftItems.isEmpty()) {
                            validationError = "Add at least one item."
                            return@Button
                        }
                        val validLineItems = draftItems.map { d ->
                            OrderLineItem(
                                productId = d.product.id,
                                productName = d.product.name,
                                sku = d.product.sku,
                                unit = d.unit.trim().ifBlank { "Pcs" },
                                quantity = d.qty,
                                unitPrice = d.rate,
                                lineTotal = d.lineTotal
                            )
                        }
                        if (validLineItems.any { it.quantity <= 0.0 }) {
                            validationError = "Every line item must have quantity greater than 0."
                            return@Button
                        }
                        val formattedLocation = OrderLocationGroup.formatLocation(
                            group = selectedLocationGroup,
                            areaDetail = orderLocationDetail
                        )
                        OrderPhotoHelper.deleteDraftPhotoCache(draftPhotoCachePath)
                        onSubmitOrder(
                            orderNumber.trim().ifBlank { currentSuggestedNumber },
                            party,
                            documentType,
                            formattedLocation,
                            orderDateMillis,
                            if (isEstimate) OrderStatus.PENDING else orderStatus,
                            validLineItems,
                            0.0,
                            discountAmt,
                            0.0,
                            PaymentMode.UPI,
                            notes.trim(),
                            photoBase64
                        )
                    }
                ) {
                    val label = when {
                        isBusy && isEditing && isEstimate -> "Updating Sale Estimate..."
                        isBusy && isEditing -> "Updating Order..."
                        isBusy && isEstimate -> "Saving Sale Estimate..."
                        isBusy -> "Creating Order..."
                        isEditing && isEstimate -> "Update Sale Estimate (${BdEntFormatter.formatInr(grandTotal)})"
                        isEditing -> "Update Order (${BdEntFormatter.formatInr(grandTotal)})"
                        isEstimate -> "Save Sale Estimate (${BdEntFormatter.formatInr(grandTotal)})"
                        else -> "Save Order (${BdEntFormatter.formatInr(grandTotal)})"
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SummaryCalcRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MonoCurrencyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun StockItemPickerDialog(
    products: List<Product>,
    isAdmin: Boolean,
    onDismiss: () -> Unit,
    onQuickAddProduct: () -> Unit,
    onSelectProduct: (Product) -> Unit
) {
    val semantic = LocalBdEntColors.current
    var query by remember { mutableStateOf("") }
    val filtered = remember(products, query) {
        val q = query.trim().lowercase()
        products.filter {
            q.isEmpty() ||
                it.name.lowercase().contains(q) ||
                it.sku.lowercase().contains(q) ||
                it.unit.lowercase().contains(q)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.94f),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select Item",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (isAdmin) {
                    FilledTonalButton(
                        onClick = onQuickAddProduct,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("picker_quick_add_item_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New Item", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search item name or SKU...") },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    },
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stock_item_picker_search")
                )
                if (filtered.isEmpty()) {
                    Text(
                        text = if (products.isEmpty()) {
                            if (isAdmin) "No items available. Tap + New Item to add one." else "No items available yet. Ask Admin to add items."
                        } else {
                            "No matching items."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 20.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 300.dp, max = 440.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filtered, key = { it.id }) { prod ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectProduct(prod) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Text(
                                            text = prod.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val subtitle = listOfNotNull(
                                            prod.sku.takeIf { it.isNotBlank() }?.let { "SKU: $it" },
                                            "Unit: ${prod.unit}"
                                        ).joinToString(" • ")
                                        Text(
                                            text = subtitle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = BdEntFormatter.formatInr(prod.sellingRate),
                                            style = MonoCurrencyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Filled.Add,
                                                    contentDescription = "Add ${prod.name}",
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}
