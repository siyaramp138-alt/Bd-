package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.BdEntFormatter
import com.example.data.Order
import com.example.data.OrderDocumentType
import com.example.data.OrderLocationGroup
import com.example.data.OrderPhotoHelper
import com.example.data.OrderStatus
import com.example.ui.theme.LocalBdEntColors
import com.example.ui.theme.MonoCodeSmall
import com.example.ui.theme.MonoCurrencyLarge
import com.example.ui.theme.MonoCurrencyMedium
import com.example.viewmodel.BdEntUiState
import kotlinx.coroutines.launch
import java.util.Locale

private val OrdersEstimateAccent = Color(0xFF0F766E)
private val OrdersEstimateBg = Color(0xFFCCFBF1)

private enum class OrderSortOption(val label: String) {
    NEWEST_FIRST("Newest First"),
    HIGHEST_VALUE("Highest Value"),
    ORDER_NUMBER("Number #")
}

private enum class OrdersViewMode {
    LIST,
    BY_LOCATION
}

private data class LocationOrderGroup(
    val locationLabel: String,
    val rawLocation: String,
    val group: OrderLocationGroup?,
    val orders: List<Order>,
    val estimateValue: Double,
    val pendingCount: Int,
    val estimateCount: Int
)

@Composable
fun OrdersScreen(
    state: BdEntUiState.Ready,
    isAdmin: Boolean,
    typeFilter: OrderDocumentType?,
    onTypeFilterChanged: (OrderDocumentType?) -> Unit,
    locationGroupFilter: OrderLocationGroup? = null,
    onLocationGroupFilterChanged: (OrderLocationGroup?) -> Unit = {},
    onCreateOrder: () -> Unit,
    onCreateEstimate: () -> Unit,
    onCreateOrderForLocation: (String) -> Unit = { onCreateOrder() },
    onOpenInvoice: (Order) -> Unit,
    onEditOrder: (Order) -> Unit = {},
    onUpdateStatus: (orderId: String, newStatus: OrderStatus) -> Unit
) {
    val semantic = LocalBdEntColors.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf<OrderStatus?>(null) }
    var viewMode by remember { mutableStateOf(OrdersViewMode.BY_LOCATION) }
    var sortOption by remember { mutableStateOf(OrderSortOption.NEWEST_FIRST) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    val isFabExpanded by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 60
        }
    }
    val showScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 1 }
    }

    val confirmedOrders = remember(state.orders) { state.orders.filterNot { it.isEstimate() } }
    val estimatesCount = remember(state.orders) { state.orders.count { it.isEstimate() } }
    val deliveredCount = remember(confirmedOrders) {
        confirmedOrders.count { it.resolvedStatus() == OrderStatus.DELIVERED }
    }

    val groupCounts = remember(state.orders) {
        OrderLocationGroup.entries.associateWith { grp ->
            state.orders.count { it.resolvedLocationGroup() == grp }
        }
    }

    val filtered = remember(state.orders, typeFilter, searchQuery, statusFilter, locationGroupFilter, sortOption) {
        val q = searchQuery.trim().lowercase(Locale.ROOT)
        val matching = state.orders.filter { order ->
            val matchesType = typeFilter == null || order.resolvedDocumentType() == typeFilter
            val matchesStatus = statusFilter == null ||
                (!order.isEstimate() && order.resolvedStatus() == statusFilter)
            val matchesLocationGroup = locationGroupFilter == null ||
                order.resolvedLocationGroup() == locationGroupFilter
            val matchesQuery = q.isEmpty() ||
                order.orderNumber.lowercase(Locale.ROOT).contains(q) ||
                order.partyName.lowercase(Locale.ROOT).contains(q) ||
                order.orderLocation.lowercase(Locale.ROOT).contains(q) ||
                order.createdByName.lowercase(Locale.ROOT).contains(q) ||
                order.items.any { it.productName.lowercase(Locale.ROOT).contains(q) }
            matchesType && matchesStatus && matchesLocationGroup && matchesQuery
        }

        when (sortOption) {
            OrderSortOption.NEWEST_FIRST -> matching
            OrderSortOption.HIGHEST_VALUE -> matching.sortedByDescending { it.grandTotal }
            OrderSortOption.ORDER_NUMBER -> matching.sortedByDescending { it.orderNumber.lowercase(Locale.ROOT) }
        }
    }

    val locationGroups = remember(filtered) {
        filtered
            .groupBy { it.resolvedLocationGroup() }
            .entries
            .map { (grp, groupOrders) ->
                LocationOrderGroup(
                    locationLabel = grp?.label ?: "Other / Unassigned",
                    rawLocation = grp?.label ?: "",
                    group = grp,
                    orders = groupOrders,
                    estimateValue = groupOrders.filter { it.isEstimate() }.sumOf { it.grandTotal },
                    pendingCount = groupOrders.count { !it.isEstimate() && it.resolvedStatus() == OrderStatus.PENDING },
                    estimateCount = groupOrders.count { it.isEstimate() }
                )
            }
            .sortedWith(
                compareBy<LocationOrderGroup> { it.group == null }
                    .thenBy { it.group?.ordinal ?: Int.MAX_VALUE }
            )
    }

    val addOrderContentDesc = stringResource(R.string.cd_orders_add_fab)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AnimatedVisibility(
                    visible = showScrollToTop,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    SmallFloatingActionButton(
                        onClick = {
                            coroutineScope.launch { listState.animateScrollToItem(0) }
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.testTag("orders_scroll_top_fab")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.cd_scroll_to_top)
                        )
                    }
                }

                SmallFloatingActionButton(
                    onClick = onCreateEstimate,
                    containerColor = OrdersEstimateAccent,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("orders_add_estimate_fab")
                ) {
                    Icon(
                        imageVector = Icons.Filled.RequestQuote,
                        contentDescription = stringResource(R.string.cd_estimates_add_fab)
                    )
                }

                ExtendedFloatingActionButton(
                    onClick = {
                        if (typeFilter == OrderDocumentType.ESTIMATE) {
                            onCreateEstimate()
                        } else if (locationGroupFilter != null) {
                            onCreateOrderForLocation(locationGroupFilter.label)
                        } else {
                            onCreateOrder()
                        }
                    },
                    expanded = isFabExpanded,
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null
                        )
                    },
                    text = {
                        Text(
                            text = stringResource(
                                if (typeFilter == OrderDocumentType.ESTIMATE) {
                                    R.string.orders_fab_new_estimate
                                } else {
                                    R.string.orders_fab_new_order
                                }
                            ),
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    containerColor = if (typeFilter == OrderDocumentType.ESTIMATE) {
                        OrdersEstimateAccent
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .semantics { contentDescription = addOrderContentDesc }
                        .testTag("orders_add_fab")
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("orders_lazy_column"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Executive Orders & Estimates Summary Banner
            item(key = "orders_summary_header") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(listOf(semantic.heroStart, semantic.heroEnd))
                        )
                        .drawBehind {
                            drawCircle(
                                color = Color.White.copy(alpha = 0.08f),
                                radius = size.height * 0.85f,
                                center = Offset(size.width * 0.92f, size.height * 0.12f)
                            )
                            drawCircle(
                                color = Color(0xFF38BDF8).copy(alpha = 0.12f),
                                radius = size.height * 0.55f,
                                center = Offset(size.width * 0.78f, size.height * 0.98f)
                            )
                        }
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = if (isAdmin) stringResource(R.string.orders_estimate_sales) else "My Sale Estimate Value",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.84f)
                            )
                            Text(
                                text = BdEntFormatter.formatInr(state.metrics.totalEstimateValue),
                                style = MonoCurrencyLarge,
                                color = Color.White
                            )
                            Text(
                                text = if (!isAdmin) {
                                    "Only your created records • Local Indore, Out of Indore & Shop"
                                } else {
                                    "Grouped by Local Indore, Out of Indore & Shop Order"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.82f)
                            )
                        }

                        // Horizontal pills for Orders & Estimates counts (never wraps vertically)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = "${state.metrics.totalOrders} Orders",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = OrdersEstimateAccent.copy(alpha = 0.92f)
                            ) {
                                Text(
                                    text = "$estimatesCount Estimates",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OrderStatusSummaryChip(
                                label = "Pending",
                                count = state.metrics.pendingOrders,
                                selected = statusFilter == OrderStatus.PENDING,
                                onClick = {
                                    statusFilter = if (statusFilter == OrderStatus.PENDING) null else OrderStatus.PENDING
                                },
                                modifier = Modifier.weight(1f)
                            )
                            OrderStatusSummaryChip(
                                label = "Dispatched",
                                count = state.metrics.dispatchedOrders,
                                selected = statusFilter == OrderStatus.DISPATCHED,
                                onClick = {
                                    statusFilter = if (statusFilter == OrderStatus.DISPATCHED) null else OrderStatus.DISPATCHED
                                },
                                modifier = Modifier.weight(1f)
                            )
                            OrderStatusSummaryChip(
                                label = "Delivered",
                                count = deliveredCount,
                                selected = statusFilter == OrderStatus.DELIVERED,
                                onClick = {
                                    statusFilter = if (statusFilter == OrderStatus.DELIVERED) null else OrderStatus.DELIVERED
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Document Type Switcher (All / Orders / Sale Estimates)
            item(key = "orders_doc_type_filter") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        FilterChip(
                            selected = typeFilter == null,
                            onClick = { onTypeFilterChanged(null) },
                            label = { Text("All (${state.orders.size})") },
                            modifier = Modifier.testTag("filter_type_all")
                        )
                    }
                    item {
                        FilterChip(
                            selected = typeFilter == OrderDocumentType.ORDER,
                            onClick = { onTypeFilterChanged(OrderDocumentType.ORDER) },
                            leadingIcon = {
                                Icon(Icons.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(15.dp))
                            },
                            label = { Text("Orders (${confirmedOrders.size})") },
                            modifier = Modifier.testTag("filter_type_orders")
                        )
                    }
                    item {
                        FilterChip(
                            selected = typeFilter == OrderDocumentType.ESTIMATE,
                            onClick = {
                                statusFilter = null
                                onTypeFilterChanged(OrderDocumentType.ESTIMATE)
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.RequestQuote, contentDescription = null, modifier = Modifier.size(15.dp))
                            },
                            label = { Text("Sale Estimates ($estimatesCount)") },
                            modifier = Modifier.testTag("filter_type_estimates")
                        )
                    }
                }
            }

            // Search, View Mode (Location-Wise / List), Status Filters & Location Chips
            item(key = "orders_search_and_filters") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(R.string.orders_search_placeholder)) },
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
                            .testTag("orders_search_input")
                    )

                    // View mode & Sort bar (horizontal scroll prevents 2-line chip wrapping on compact phones)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            FilterChip(
                                selected = viewMode == OrdersViewMode.BY_LOCATION,
                                onClick = { viewMode = OrdersViewMode.BY_LOCATION },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Map,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text(stringResource(R.string.orders_view_by_location), maxLines = 1) },
                                modifier = Modifier.testTag("orders_view_by_location")
                            )
                        }
                        item {
                            FilterChip(
                                selected = viewMode == OrdersViewMode.LIST,
                                onClick = { viewMode = OrdersViewMode.LIST },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text(stringResource(R.string.orders_view_list), maxLines = 1) },
                                modifier = Modifier.testTag("orders_view_list")
                            )
                        }
                        item {
                            Box {
                                FilterChip(
                                    selected = sortOption != OrderSortOption.NEWEST_FIRST,
                                    onClick = { sortMenuExpanded = true },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Sort,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = { Text(sortOption.label, maxLines = 1) },
                                    modifier = Modifier.testTag("orders_sort_chip")
                                )
                                DropdownMenu(
                                    expanded = sortMenuExpanded,
                                    onDismissRequest = { sortMenuExpanded = false }
                                ) {
                                    OrderSortOption.entries.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.label) },
                                            onClick = {
                                                sortOption = option
                                                sortMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Location Group filter chips: All / Local Indore / Out of Indore / Shop Order
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            FilterChip(
                                selected = locationGroupFilter == null,
                                onClick = { onLocationGroupFilterChanged(null) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.LocationOn,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                },
                                label = { Text("All Groups") },
                                modifier = Modifier.testTag("orders_location_filter_all")
                            )
                        }
                        items(OrderLocationGroup.entries, key = { it.name }) { group ->
                            val count = groupCounts[group] ?: 0
                            val grpIcon = when (group) {
                                OrderLocationGroup.LOCAL_INDORE -> Icons.Filled.LocationCity
                                OrderLocationGroup.OUT_OF_INDORE -> Icons.Filled.LocalShipping
                                OrderLocationGroup.SHOP_ORDER -> Icons.Filled.Storefront
                            }
                            FilterChip(
                                selected = locationGroupFilter == group,
                                onClick = {
                                    onLocationGroupFilterChanged(if (locationGroupFilter == group) null else group)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = grpIcon,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                },
                                label = { Text("${group.label} ($count)", maxLines = 1) },
                                modifier = Modifier.testTag("orders_loc_group_filter_${group.name.lowercase(Locale.ROOT)}")
                            )
                        }
                    }
                }
            }

            if (filtered.isEmpty()) {
                item(key = "orders_empty_state") {
                    val hasActiveFilters = searchQuery.isNotBlank() || statusFilter != null || locationGroupFilter != null || typeFilter != null
                    EmptyStateCard(
                        icon = if (typeFilter == OrderDocumentType.ESTIMATE) Icons.Filled.RequestQuote else Icons.Filled.ReceiptLong,
                        title = when {
                            typeFilter == OrderDocumentType.ESTIMATE && estimatesCount == 0 -> "No sale estimates yet"
                            state.orders.isEmpty() -> if (isAdmin) "No orders or estimates yet" else "No orders created by you yet"
                            else -> "No matching records"
                        },
                        subtitle = if (typeFilter == OrderDocumentType.ESTIMATE && estimatesCount == 0) {
                            "Create a Sale Estimate with location group and camera photo."
                        } else if (state.orders.isEmpty()) {
                            "Tap + New Order or Sale Estimate below to create a location-wise record with camera photo."
                        } else {
                            "No records match your current filter or search."
                        },
                        ctaLabel = when {
                            typeFilter == OrderDocumentType.ESTIMATE && estimatesCount == 0 -> "+ Sale Estimate"
                            state.orders.isEmpty() -> "+ New Order"
                            hasActiveFilters -> "Clear Filters"
                            else -> null
                        },
                        onCtaClick = when {
                            typeFilter == OrderDocumentType.ESTIMATE && estimatesCount == 0 -> onCreateEstimate
                            state.orders.isEmpty() -> onCreateOrder
                            hasActiveFilters -> {
                                {
                                    searchQuery = ""
                                    statusFilter = null
                                    onLocationGroupFilterChanged(null)
                                    onTypeFilterChanged(null)
                                }
                            }
                            else -> null
                        },
                        ctaTag = "orders_empty_cta"
                    )
                }
            } else if (viewMode == OrdersViewMode.BY_LOCATION) {
                locationGroups.forEach { group ->
                    item(key = "loc_header_${group.locationLabel}") {
                        LocationGroupHeaderCard(
                            group = group,
                            onAddOrderHere = { onCreateOrderForLocation(group.rawLocation) }
                        )
                    }
                    items(group.orders, key = { it.id }) { order ->
                        OrderCard(
                            order = order,
                            isAdmin = isAdmin,
                            onOpenInvoice = { onOpenInvoice(order) },
                            onEditOrder = { onEditOrder(order) },
                            onUpdateStatus = { nextStatus -> onUpdateStatus(order.id, nextStatus) }
                        )
                    }
                }
            } else {
                items(filtered, key = { it.id }) { order ->
                    OrderCard(
                        order = order,
                        isAdmin = isAdmin,
                        onOpenInvoice = { onOpenInvoice(order) },
                        onEditOrder = { onEditOrder(order) },
                        onUpdateStatus = { nextStatus -> onUpdateStatus(order.id, nextStatus) }
                    )
                }
            }

            // Extra clearance so the last order card scrolls above the floating action buttons
            item(key = "orders_bottom_spacer") {
                Spacer(modifier = Modifier.height(128.dp))
            }
        }
    }
}

@Composable
private fun LocationGroupHeaderCard(
    group: LocationOrderGroup,
    onAddOrderHere: () -> Unit
) {
    val semantic = LocalBdEntColors.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.52f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .testTag("location_group_${group.locationLabel}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.locationLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val summaryMeta = buildString {
                        val orderCount = group.orders.size - group.estimateCount
                        append("$orderCount order${if (orderCount == 1) "" else "s"}")
                        if (group.pendingCount > 0) append(" • ${group.pendingCount} pending")
                        if (group.estimateCount > 0) {
                            append(" • ${group.estimateCount} est (${BdEntFormatter.formatInr(group.estimateValue, showDecimals = false)})")
                        }
                    }
                    Text(
                        text = summaryMeta,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (group.pendingCount > 0) semantic.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (group.rawLocation.isNotEmpty()) {
                FilledTonalButton(
                    onClick = onAddOrderHere,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("add_order_location_${group.locationLabel}")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Order", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun OrderStatusSummaryChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Color.White else Color.White.copy(alpha = 0.14f),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.22f)
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.White
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun OrderCard(
    order: Order,
    isAdmin: Boolean,
    onOpenInvoice: () -> Unit,
    onEditOrder: () -> Unit,
    onUpdateStatus: (OrderStatus) -> Unit
) {
    val semantic = LocalBdEntColors.current
    val showCreator = isAdmin
    val isEst = order.isEstimate()
    val status = order.resolvedStatus()
    val canEdit = isAdmin || isEst || status != OrderStatus.DELIVERED
    val accentColor = if (isEst) {
        OrdersEstimateAccent
    } else {
        when (status) {
            OrderStatus.PENDING -> semantic.warning
            OrderStatus.DISPATCHED -> MaterialTheme.colorScheme.primary
            OrderStatus.DELIVERED -> semantic.success
        }
    }
    val thumbBitmap = remember(order.photoBase64) {
        if (order.hasPhoto()) OrderPhotoHelper.decodeBase64ToBitmap(order.photoBase64) else null
    }
    val itemsPreview = remember(order.items) {
        if (order.items.isEmpty()) {
            "No line items"
        } else {
            order.items.take(2).joinToString(", ") {
                "${it.productName} ×${BdEntFormatter.formatQty(it.quantity)}"
            } + if (order.items.size > 2) " +${order.items.size - 2} more" else ""
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (isEst) OrdersEstimateAccent.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant
        ),
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenInvoice)
            .testTag("order_card_${order.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(accentColor.copy(alpha = 0.85f))
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = order.orderNumber,
                            style = MonoCodeSmall,
                            color = if (isEst) OrdersEstimateAccent else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        if (isEst) {
                            StatusPill(
                                text = "SALE ESTIMATE",
                                containerColor = OrdersEstimateBg,
                                contentColor = OrdersEstimateAccent
                            )
                        } else {
                            OrderStatusPill(status = status)
                        }
                    }
                    Text(
                        text = BdEntFormatter.formatDate(order.orderDateMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    if (thumbBitmap != null) {
                        Image(
                            bitmap = thumbBitmap.asImageBitmap(),
                            contentDescription = "Attached order photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = order.partyName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (order.orderLocation.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = order.orderLocation,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (showCreator && order.createdByName.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "By ${order.createdByName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Text(
                            text = "${order.items.size} item${if (order.items.size == 1) "" else "s"} • $itemsPreview",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = BdEntFormatter.formatInr(order.grandTotal),
                            style = MonoCurrencyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (order.hasPhoto() && thumbBitmap == null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(12.dp))
                                Text("Photo", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenInvoice,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("order_invoice_btn_${order.id}")
                    ) {
                        Icon(
                            imageVector = if (isEst) Icons.Filled.RequestQuote else Icons.Filled.Description,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (isEst) "Slip" else "Invoice",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    }
                    if (canEdit) {
                        OutlinedButton(
                            onClick = onEditOrder,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("order_edit_btn_${order.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Edit",
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1
                            )
                        }
                    }
                    if (!isEst && status == OrderStatus.PENDING) {
                        FilledTonalButton(
                            onClick = { onUpdateStatus(OrderStatus.DISPATCHED) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("order_status_btn_${order.id}")
                        ) {
                            Icon(Icons.Filled.LocalShipping, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Dispatch", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    } else if (!isEst && status == OrderStatus.DISPATCHED) {
                        FilledTonalButton(
                            onClick = { onUpdateStatus(OrderStatus.DELIVERED) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("order_status_btn_${order.id}")
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Delivered", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
