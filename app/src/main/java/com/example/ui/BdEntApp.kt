package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.AccessResolution
import com.example.data.BdEntRepository
import com.example.data.OrderDocumentType
import com.example.data.OrderLocationGroup
import com.example.data.OrderRepository
import com.example.data.Party
import com.example.data.Product
import com.example.data.StaffRole
import com.example.viewmodel.BdEntUiState
import com.example.viewmodel.BdEntViewModel
import com.example.viewmodel.OrderUiState
import com.example.viewmodel.OrderViewModel
import com.google.firebase.auth.FirebaseUser

private enum class BdEntTab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    DASHBOARD(R.string.tab_dashboard, Icons.Filled.Dashboard, Icons.Outlined.Dashboard, "nav_dashboard"),
    ORDERS(R.string.tab_orders, Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong, "nav_orders"),
    PARTIES(R.string.tab_parties, Icons.Filled.Groups, Icons.Outlined.Groups, "nav_parties"),
    STOCK(R.string.tab_items, Icons.Filled.Inventory2, Icons.Outlined.Inventory2, "nav_stock"),
    BACKUP(R.string.tab_backup, Icons.Filled.AdminPanelSettings, Icons.Outlined.AdminPanelSettings, "nav_backup")
}

@Composable
fun BdEntWorkspace(
    user: FirebaseUser,
    access: AccessResolution.Authorized,
    onSignOut: () -> Unit
) {
    val isAdmin = access.role == StaffRole.ADMIN

    val viewModel: BdEntViewModel = viewModel(
        key = "${user.uid}_${access.role.name}",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                BdEntViewModel(
                    repository = BdEntRepository(app),
                    currentRole = access.role
                )
            }
        }
    )

    val orderViewModel: OrderViewModel = viewModel(
        key = "${user.uid}_${access.role.name}_orders",
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                OrderViewModel(
                    orderRepository = OrderRepository(app),
                    currentRole = access.role
                )
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val orderUiState by orderViewModel.uiState.collectAsStateWithLifecycle()
    val isMainBusy by viewModel.busy.collectAsStateWithLifecycle()
    val isOrderBusy by orderViewModel.busy.collectAsStateWithLifecycle()
    val isBusy = isMainBusy || isOrderBusy
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(orderViewModel) {
        orderViewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    var currentTab by rememberSaveable { mutableStateOf(BdEntTab.DASHBOARD) }

    // Full-screen Sub-routes
    var showCreateOrder by rememberSaveable { mutableStateOf(false) }
    var editingOrderId by rememberSaveable { mutableStateOf<String?>(null) }
    var createDocumentTypeRaw by rememberSaveable { mutableStateOf(OrderDocumentType.ORDER.name) }
    var prefilledOrderLocation by rememberSaveable { mutableStateOf("") }
    var ordersTypeFilterRaw by rememberSaveable { mutableStateOf<String?>(null) }
    var ordersLocationGroupRaw by rememberSaveable { mutableStateOf<String?>(null) }
    var viewingInvoiceOrderId by rememberSaveable { mutableStateOf<String?>(null) }

    // Reusable Dialog States
    var showPartyDialog by remember { mutableStateOf(false) }
    var editingParty by remember { mutableStateOf<Party?>(null) }
    var showProductDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }

    val readyState = uiState as? BdEntUiState.Ready

    // Global Dialogs (accessible from any tab or sub-screen)
    if (showPartyDialog) {
        PartyFormDialog(
            editingParty = editingParty,
            isBusy = isBusy,
            onDismiss = {
                showPartyDialog = false
                editingParty = null
            },
            onSave = { existingId, name, mobile, gstOrAddress, partyType, openingBalance ->
                viewModel.saveParty(
                    existingId = existingId,
                    name = name,
                    mobile = mobile,
                    gstOrAddress = gstOrAddress,
                    partyType = partyType,
                    openingBalance = openingBalance,
                    onSuccess = {
                        showPartyDialog = false
                        editingParty = null
                    }
                )
            }
        )
    }

    if (showProductDialog) {
        ProductFormDialog(
            editingProduct = editingProduct,
            isBusy = isBusy,
            onDismiss = {
                showProductDialog = false
                editingProduct = null
            },
            onSave = { existingId, name, sku, unit, category, location, purchaseRate, sellingRate, stockQty, minAlert ->
                viewModel.saveProduct(
                    existingId = existingId,
                    name = name,
                    sku = sku,
                    unit = unit,
                    category = category,
                    location = location,
                    purchaseRate = purchaseRate,
                    sellingRate = sellingRate,
                    stockQty = stockQty,
                    minAlertLevel = minAlert,
                    previousStockQty = editingProduct?.stockQty,
                    onSuccess = {
                        showProductDialog = false
                        editingProduct = null
                    }
                )
            }
        )
    }

    // Sub-screen 1: Create New OR Modify Existing Order / Sale Estimate (persisted via OrderViewModel -> Firestore)
    val currentOrdersList = (orderUiState as? OrderUiState.Ready)?.orders ?: readyState?.orders.orEmpty()
    val editingOrder = editingOrderId?.let { id ->
        currentOrdersList.firstOrNull { it.id == id }
    }
    if ((showCreateOrder || editingOrder != null) && readyState != null) {
        val recentLocations = remember(currentOrdersList) {
            currentOrdersList.map { it.orderLocation.trim() }.filter { it.isNotEmpty() }.distinct()
        }
        CreateOrderScreen(
            editingOrder = editingOrder,
            isAdmin = isAdmin,
            initialDocumentType = OrderDocumentType.fromRaw(createDocumentTypeRaw),
            suggestedOrderNumber = orderViewModel.nextSuggestedOrderNumber(currentOrdersList, OrderDocumentType.ORDER),
            suggestedEstimateNumber = orderViewModel.nextSuggestedOrderNumber(currentOrdersList, OrderDocumentType.ESTIMATE),
            parties = readyState.parties,
            products = readyState.products,
            existingLocations = recentLocations,
            initialLocation = prefilledOrderLocation,
            isBusy = isBusy,
            snackbarHostState = snackbarHostState,
            onBack = {
                showCreateOrder = false
                editingOrderId = null
                prefilledOrderLocation = ""
            },
            onQuickAddParty = {
                editingParty = null
                showPartyDialog = true
            },
            onQuickAddProduct = {
                editingProduct = null
                showProductDialog = true
            },
            onSubmitOrder = { orderNo, party, docType, orderLoc, dateMillis, status, items, taxPct, discountAmt, advPaid, advMode, notes, photoBase64 ->
                if (editingOrder != null) {
                    orderViewModel.updateOrder(
                        orderId = editingOrder.id,
                        orderNumber = orderNo,
                        party = party,
                        documentType = docType,
                        orderLocation = orderLoc,
                        orderDateMillis = dateMillis,
                        status = status,
                        items = items,
                        taxPercent = taxPct,
                        discountAmount = discountAmt,
                        notes = notes,
                        photoBase64 = photoBase64,
                        onSuccess = { updatedOrderId ->
                            editingOrderId = null
                            showCreateOrder = false
                            viewingInvoiceOrderId = updatedOrderId
                        }
                    )
                } else {
                    orderViewModel.createOrder(
                        orderNumber = orderNo,
                        party = party,
                        documentType = docType,
                        orderLocation = orderLoc,
                        orderDateMillis = dateMillis,
                        status = status,
                        items = items,
                        taxPercent = taxPct,
                        discountAmount = discountAmt,
                        advancePaid = advPaid,
                        advancePaymentMode = advMode,
                        notes = notes,
                        photoBase64 = photoBase64,
                        onSuccess = { newOrderId ->
                            showCreateOrder = false
                            prefilledOrderLocation = ""
                            viewingInvoiceOrderId = newOrderId
                        }
                    )
                }
            }
        )
        return
    }

    // Sub-screen 2: Invoice / Sale Estimate Slip View
    val activeInvoiceOrder = viewingInvoiceOrderId?.let { id ->
        currentOrdersList.firstOrNull { it.id == id }
    }
    if (activeInvoiceOrder != null) {
        InvoiceSlipScreen(
            order = activeInvoiceOrder,
            isAdmin = isAdmin,
            isBusy = isBusy,
            snackbarHostState = snackbarHostState,
            onBack = { viewingInvoiceOrderId = null },
            onEditOrder = {
                editingOrderId = activeInvoiceOrder.id
            },
            onUpdateStatus = { newStatus ->
                orderViewModel.updateOrderStatus(activeInvoiceOrder.id, newStatus)
            },
            onUpdatePhoto = { updatedPhoto ->
                orderViewModel.updateOrderPhoto(activeInvoiceOrder.id, updatedPhoto)
            },
            onConvertToOrder = {
                orderViewModel.convertEstimateToOrder(activeInvoiceOrder, currentOrdersList) { newOrderId ->
                    viewingInvoiceOrderId = newOrderId
                }
            },
            onDeleteOrder = {
                orderViewModel.deleteOrder(activeInvoiceOrder.id) {
                    viewingInvoiceOrderId = null
                }
            },
            onSnackbarMessage = viewModel::emitMessage
        )
        return
    }

    // Main Adaptive Navigation Layout (NavigationBar on Compact, NavigationRail on Medium/Expanded)
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        BdEntTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            val labelId = tab.labelRes
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(labelId)
                                    )
                                },
                                label = { Text(stringResource(labelId), maxLines = 1) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        header = {
                            if (readyState != null) {
                                FloatingActionButton(
                                    onClick = {
                                        createDocumentTypeRaw = OrderDocumentType.ORDER.name
                                        prefilledOrderLocation = ""
                                        showCreateOrder = true
                                    },
                                    elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation(),
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier
                                        .padding(top = 8.dp, bottom = 12.dp)
                                        .testTag("rail_new_order_fab")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = stringResource(R.string.cd_orders_add_fab)
                                    )
                                }
                            }
                        }
                    ) {
                        BdEntTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            val labelId = tab.labelRes
                            NavigationRailItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(labelId)
                                    )
                                },
                                label = { Text(stringResource(labelId)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 960.dp)
                            .fillMaxSize()
                    ) {
                        when (val state = uiState) {
                            BdEntUiState.Loading -> {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                                    Text(
                                        text = "Syncing BD Enterprises ledger...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 12.dp)
                                    )
                                }
                            }

                            is BdEntUiState.Error -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "Could not sync BD Enterprises data",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }

                            is BdEntUiState.Ready -> {
                                when (currentTab) {
                                    BdEntTab.DASHBOARD -> DashboardScreen(
                                        state = state,
                                        access = access,
                                        onNewOrder = {
                                            createDocumentTypeRaw = OrderDocumentType.ORDER.name
                                            prefilledOrderLocation = ""
                                            showCreateOrder = true
                                        },
                                        onNewEstimate = {
                                            createDocumentTypeRaw = OrderDocumentType.ESTIMATE.name
                                            prefilledOrderLocation = ""
                                            showCreateOrder = true
                                        },
                                        onAddItem = {
                                            if (isAdmin) {
                                                editingProduct = null
                                                showProductDialog = true
                                            }
                                        },
                                        onOpenOrders = {
                                            ordersTypeFilterRaw = null
                                            ordersLocationGroupRaw = null
                                            currentTab = BdEntTab.ORDERS
                                        },
                                        onOpenItems = {
                                            currentTab = BdEntTab.STOCK
                                        },
                                        onOpenLocationGroup = { group ->
                                            ordersTypeFilterRaw = null
                                            ordersLocationGroupRaw = group.name
                                            currentTab = BdEntTab.ORDERS
                                        },
                                        onNewOrderForLocationGroup = { group ->
                                            createDocumentTypeRaw = OrderDocumentType.ORDER.name
                                            prefilledOrderLocation = group.label
                                            showCreateOrder = true
                                        },
                                        onOpenOrderInvoice = { order ->
                                            viewingInvoiceOrderId = order.id
                                        }
                                    )

                                    BdEntTab.ORDERS -> OrdersScreen(
                                        state = state,
                                        isAdmin = isAdmin,
                                        typeFilter = ordersTypeFilterRaw?.let { OrderDocumentType.fromRaw(it) },
                                        onTypeFilterChanged = { ordersTypeFilterRaw = it?.name },
                                        locationGroupFilter = OrderLocationGroup.fromRaw(ordersLocationGroupRaw),
                                        onLocationGroupFilterChanged = { ordersLocationGroupRaw = it?.name },
                                        onCreateOrder = {
                                            createDocumentTypeRaw = OrderDocumentType.ORDER.name
                                            prefilledOrderLocation = ""
                                            showCreateOrder = true
                                        },
                                        onCreateEstimate = {
                                            createDocumentTypeRaw = OrderDocumentType.ESTIMATE.name
                                            prefilledOrderLocation = ""
                                            showCreateOrder = true
                                        },
                                        onCreateOrderForLocation = { loc ->
                                            createDocumentTypeRaw = OrderDocumentType.ORDER.name
                                            prefilledOrderLocation = loc
                                            showCreateOrder = true
                                        },
                                        onOpenInvoice = { order -> viewingInvoiceOrderId = order.id },
                                        onEditOrder = { order -> editingOrderId = order.id },
                                        onUpdateStatus = orderViewModel::updateOrderStatus
                                    )

                                    BdEntTab.PARTIES -> PartiesScreen(
                                        state = state,
                                        isAdmin = isAdmin,
                                        onAddParty = {
                                            editingParty = null
                                            showPartyDialog = true
                                        },
                                        onEditParty = { party ->
                                            editingParty = party
                                            showPartyDialog = true
                                        },
                                        onDeleteParty = { party -> viewModel.deleteParty(party.id) },
                                        onOpenOrderInvoice = { order -> viewingInvoiceOrderId = order.id },
                                        onSnackbarMessage = viewModel::emitMessage
                                    )

                                    BdEntTab.STOCK -> StockScreen(
                                        state = state,
                                        isAdmin = isAdmin,
                                        onAddProduct = {
                                            editingProduct = null
                                            showProductDialog = true
                                        },
                                        onEditProduct = { prod ->
                                            editingProduct = prod
                                            showProductDialog = true
                                        },
                                        onDeleteProduct = { prod -> viewModel.deleteProduct(prod.id) }
                                    )

                                    BdEntTab.BACKUP -> BackupScreen(
                                        state = state,
                                        access = access,
                                        isBusy = isBusy,
                                        onSaveStaff = { email, name, role, active, isUpdate, onDone ->
                                            viewModel.saveStaffMember(email, name, role, active, isUpdate, onDone)
                                        },
                                        onToggleStaffActive = viewModel::toggleStaffActive,
                                        onDeleteStaff = viewModel::deleteStaffMember,
                                        onRestoreBackup = { payload -> viewModel.restoreBackup(payload) },
                                        onSignOut = onSignOut,
                                        onSnackbarMessage = viewModel::emitMessage
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
