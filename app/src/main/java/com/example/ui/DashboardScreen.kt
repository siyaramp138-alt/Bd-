package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AccessResolution
import com.example.data.BdEntFormatter
import com.example.data.LocationGroupMetric
import com.example.data.Order
import com.example.data.OrderLocationGroup
import com.example.data.StaffRole
import com.example.ui.theme.LocalBdEntColors
import com.example.ui.theme.MonoCodeSmall
import com.example.ui.theme.MonoCurrencyLarge
import com.example.ui.theme.MonoCurrencyMedium
import com.example.viewmodel.BdEntUiState
import java.util.Locale

private val EstimateAccent = Color(0xFF0F766E)
private val EstimateContainer = Color(0xFFCCFBF1)

@Composable
fun DashboardScreen(
    state: BdEntUiState.Ready,
    access: AccessResolution.Authorized,
    onNewOrder: () -> Unit,
    onNewEstimate: () -> Unit,
    onAddItem: () -> Unit,
    onOpenOrders: () -> Unit,
    onOpenItems: () -> Unit,
    onOpenLocationGroup: (OrderLocationGroup) -> Unit,
    onNewOrderForLocationGroup: (OrderLocationGroup) -> Unit,
    onOpenOrderInvoice: (Order) -> Unit
) {
    val semantic = LocalBdEntColors.current
    val isAdmin = access.role == StaffRole.ADMIN
    val recentRecords = state.orders.take(6)
    val locationMetrics = state.metrics.locationGroupMetrics.ifEmpty {
        OrderLocationGroup.entries.map { LocationGroupMetric(group = it) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Executive Hero Header Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(listOf(semantic.heroStart, semantic.heroEnd))
                    )
                    .drawBehind {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.07f),
                            radius = size.height * 0.85f,
                            center = Offset(size.width * 0.94f, size.height * 0.15f)
                        )
                        drawCircle(
                            color = Color(0xFF38BDF8).copy(alpha = 0.12f),
                            radius = size.height * 0.5f,
                            center = Offset(size.width * 0.8f, size.height * 0.95f)
                        )
                    }
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "BD Ent",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${access.displayName} • ${access.role.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.84f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                color = if (isAdmin) Color.White else Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = access.role.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAdmin) MaterialTheme.colorScheme.primary else Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CloudDone,
                                    contentDescription = "Synced to cloud",
                                    tint = Color(0xFF7DD3FC),
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Cloud Sync",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Employee Privacy Scope Notice
        if (!isAdmin) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VerifiedUser,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Employee Workspace — Only orders and sale estimates created by you are shown.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        // Quick Actions (No Payment option; Add Stock visible to Admin only)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Quick Actions",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onNewOrder,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_new_order_button")
                    ) {
                        Icon(Icons.Filled.AddShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.action_new_order),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Button(
                        onClick = onNewEstimate,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EstimateAccent,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_sale_estimate_button")
                    ) {
                        Icon(Icons.Filled.RequestQuote, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.action_sale_estimate),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (isAdmin) {
                    FilledTonalButton(
                        onClick = onAddItem,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quick_add_stock_button")
                    ) {
                        Icon(Icons.Filled.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.action_add_stock),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Location Groups Section on Dashboard: Local Indore, Out of Indore, Shop Order
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(
                    title = stringResource(R.string.dashboard_location_groups),
                    actionLabel = "View Orders",
                    onActionClick = onOpenOrders
                )
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val isWide = maxWidth >= 600.dp
                    if (isWide) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            locationMetrics.forEach { metric ->
                                DashboardLocationGroupCard(
                                    metric = metric,
                                    onOpen = { onOpenLocationGroup(metric.group) },
                                    onQuickAdd = { onNewOrderForLocationGroup(metric.group) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            locationMetrics.forEach { metric ->
                                DashboardLocationGroupRowCard(
                                    metric = metric,
                                    onOpen = { onOpenLocationGroup(metric.group) },
                                    onQuickAdd = { onNewOrderForLocationGroup(metric.group) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Core KPI Summary Cards (Counts only — no sales value or estimate totals on Dashboard)
        item {
            val kpiList = listOf(
                KpiSpec(
                    title = if (isAdmin) "Total Orders" else "My Orders",
                    value = state.metrics.totalOrders.toString(),
                    caption = "${state.metrics.pendingOrders} pending • ${state.metrics.dispatchedOrders} dispatched",
                    icon = Icons.Filled.ReceiptLong,
                    accentColor = MaterialTheme.colorScheme.primary,
                    accentBg = MaterialTheme.colorScheme.primaryContainer,
                    alertHighlight = false,
                    tag = "kpi_total_orders",
                    onClick = onOpenOrders
                ),
                KpiSpec(
                    title = "Items Catalog",
                    value = state.products.size.toString(),
                    caption = "Active items & rates",
                    icon = Icons.Filled.Inventory2,
                    accentColor = MaterialTheme.colorScheme.secondary,
                    accentBg = MaterialTheme.colorScheme.secondaryContainer,
                    alertHighlight = false,
                    tag = "kpi_total_items",
                    onClick = onOpenItems
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                kpiList.forEach { spec ->
                    KpiCard(spec = spec, modifier = Modifier.weight(1f))
                }
            }
        }

        // Recent Orders & Sale Estimates Section
        item {
            SectionHeader(
                title = if (isAdmin) "Recent Orders & Estimates" else "My Recent Orders & Estimates",
                actionLabel = if (state.orders.isNotEmpty()) "View All" else null,
                onActionClick = if (state.orders.isNotEmpty()) onOpenOrders else null
            )
        }

        if (recentRecords.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Filled.ReceiptLong,
                    title = if (isAdmin) "No orders or estimates yet" else "You haven't created any orders yet",
                    subtitle = "Create a Sale Order or Sale Estimate for Local Indore, Out of Indore, or Shop Order with camera photo.",
                    ctaLabel = "+ Create First Order",
                    onCtaClick = onNewOrder,
                    ctaTag = "dashboard_empty_new_order"
                )
            }
        } else {
            items(recentRecords, key = { "dash_order_${it.id}" }) { order ->
                val isEst = order.isEstimate()
                val locGroup = order.resolvedLocationGroup()
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (isEst) EstimateAccent.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenOrderInvoice(order) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = order.orderNumber,
                                    style = MonoCodeSmall,
                                    color = if (isEst) EstimateAccent else MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                if (isEst) {
                                    StatusPill(
                                        text = "ESTIMATE",
                                        containerColor = EstimateContainer,
                                        contentColor = EstimateAccent
                                    )
                                } else {
                                    OrderStatusPill(status = order.resolvedStatus())
                                }
                                if (order.hasPhoto()) {
                                    Icon(
                                        imageVector = Icons.Filled.PhotoCamera,
                                        contentDescription = "Has photo attached",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Text(
                                text = order.partyName,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = buildString {
                                    if (locGroup != null) {
                                        append(locGroup.label)
                                        append(" • ")
                                    }
                                    append(BdEntFormatter.formatDate(order.orderDateMillis))
                                    append(" • ${order.items.size} item(s)")
                                    if (isAdmin && order.createdByName.isNotBlank()) {
                                        append(" • By ${order.createdByName}")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = BdEntFormatter.formatInr(order.grandTotal),
                            style = MonoCurrencyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun locationGroupStyle(group: OrderLocationGroup): Triple<ImageVector, Color, Color> {
    return when (group) {
        OrderLocationGroup.LOCAL_INDORE -> Triple(
            Icons.Filled.LocationCity,
            Color(0xFF1D4ED8),
            Color(0xFFDBEAFE)
        )
        OrderLocationGroup.OUT_OF_INDORE -> Triple(
            Icons.Filled.LocalShipping,
            Color(0xFFB45309),
            Color(0xFFFEF3C7)
        )
        OrderLocationGroup.SHOP_ORDER -> Triple(
            Icons.Filled.Storefront,
            Color(0xFF6D28D9),
            Color(0xFFEDE9FE)
        )
    }
}

@Composable
private fun DashboardLocationGroupRowCard(
    metric: LocationGroupMetric,
    onOpen: () -> Unit,
    onQuickAdd: () -> Unit
) {
    val (icon, accentColor, accentBg) = locationGroupStyle(metric.group)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f)),
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .testTag("dashboard_loc_group_${metric.group.name.lowercase(Locale.ROOT)}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = metric.group.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildString {
                            append("${metric.orderCount} order${if (metric.orderCount == 1) "" else "s"}")
                            if (metric.estimateCount > 0) {
                                append(" • ${metric.estimateCount} estimate${if (metric.estimateCount == 1) "" else "s"}")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = accentColor,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            FilledTonalIconButton(
                onClick = onQuickAdd,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("dashboard_loc_add_${metric.group.name.lowercase(Locale.ROOT)}")
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "New order for ${metric.group.label}",
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun DashboardLocationGroupCard(
    metric: LocationGroupMetric,
    onOpen: () -> Unit,
    onQuickAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (icon, accentColor, accentBg) = locationGroupStyle(metric.group)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f)),
        shadowElevation = 1.dp,
        modifier = modifier
            .clickable(onClick = onOpen)
            .testTag("dashboard_loc_group_${metric.group.name.lowercase(Locale.ROOT)}")
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
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                FilledTonalIconButton(
                    onClick = onQuickAdd,
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("dashboard_loc_add_${metric.group.name.lowercase(Locale.ROOT)}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "New order for ${metric.group.label}",
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = metric.group.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${metric.orderCount} order${if (metric.orderCount == 1) "" else "s"}",
                style = MonoCurrencyMedium,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (metric.estimateCount > 0) "${metric.estimateCount} estimate${if (metric.estimateCount == 1) "" else "s"}" else "Tap to view",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

private data class KpiSpec(
    val title: String,
    val value: String,
    val caption: String,
    val icon: ImageVector,
    val accentColor: Color,
    val accentBg: Color,
    val alertHighlight: Boolean,
    val tag: String,
    val onClick: () -> Unit
)

@Composable
private fun KpiCard(
    spec: KpiSpec,
    modifier: Modifier = Modifier
) {
    val semantic = LocalBdEntColors.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (spec.alertHighlight) 1.5.dp else 1.dp,
            color = if (spec.alertHighlight) semantic.danger else MaterialTheme.colorScheme.outlineVariant
        ),
        shadowElevation = 1.dp,
        modifier = modifier
            .clickable(onClick = spec.onClick)
            .testTag(spec.tag)
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
                Text(
                    text = spec.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(spec.accentBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = spec.icon,
                        contentDescription = null,
                        tint = spec.accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = spec.value,
                style = MonoCurrencyLarge,
                color = if (spec.alertHighlight) semantic.danger else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = spec.caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
