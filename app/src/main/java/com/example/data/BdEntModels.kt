package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

val ITEM_UNIT_PRESETS: List<String> = listOf(
    "Pcs",
    "Gm",
    "Kg",
    "Bag",
    "Drum",
    "Tin",
    "Bottle",
    "Set",
    "Box",
    "Carton"
)

enum class StaffRole(val label: String) {
    ADMIN("Admin"),
    EMPLOYEE("Employee");

    companion object {
        fun fromRaw(raw: String): StaffRole =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: EMPLOYEE
    }
}

enum class PartyType(val label: String) {
    CUSTOMER("Customer"),
    SUPPLIER("Supplier"),
    BOTH("Customer & Supplier");

    companion object {
        fun fromRaw(raw: String): PartyType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: CUSTOMER
    }
}

enum class PaymentMode(val label: String) {
    CASH("Cash"),
    UPI("UPI"),
    BANK("Bank"),
    CHEQUE("Cheque");

    companion object {
        fun fromRaw(raw: String): PaymentMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: UPI
    }
}

enum class StockMovementType(val label: String) {
    INITIAL_STOCK("Initial Stock"),
    RESTOCK("Stock In"),
    DEDUCT("Stock Out"),
    ORDER_SALE("Order Sale"),
    AUDIT_ADJUST("Stock Audit");

    companion object {
        fun fromRaw(raw: String): StockMovementType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: AUDIT_ADJUST
    }
}

@Keep
@IgnoreExtraProperties
data class StaffMember(
    @DocumentId val id: String = "",
    val email: String = "",
    val name: String = "",
    val role: String = StaffRole.EMPLOYEE.name,
    val active: Boolean = true,
    val addedByEmail: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedRole(): StaffRole = StaffRole.fromRaw(role)
}

@Keep
@IgnoreExtraProperties
data class Party(
    @DocumentId val id: String = "",
    val createdByUid: String = "",
    val createdByName: String = "",
    val name: String = "",
    val mobile: String = "",
    val gstOrAddress: String = "",
    val partyType: String = PartyType.CUSTOMER.name,
    val openingBalance: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedType(): PartyType = PartyType.fromRaw(partyType)
}

@Keep
@IgnoreExtraProperties
data class Product(
    @DocumentId val id: String = "",
    val createdByUid: String = "",
    val name: String = "",
    val sku: String = "",
    val unit: String = "Pcs",
    val category: String = "",
    val location: String = "",
    val purchaseRate: Double = 0.0,
    val sellingRate: Double = 0.0,
    val stockQty: Double = 0.0,
    val minAlertLevel: Double = 5.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun outOfStock(): Boolean = stockQty <= 0.0
    fun lowStockAlert(): Boolean = stockQty <= minAlertLevel
    fun marginPerUnit(): Double = sellingRate - purchaseRate
    fun stockValue(): Double = maxOf(0.0, stockQty) * purchaseRate
    fun stockHealthRatio(): Float {
        if (stockQty <= 0.0) return 0f
        val target = maxOf(minAlertLevel * 2.0, 10.0)
        return (stockQty / target).toFloat().coerceIn(0.05f, 1f)
    }
}

@Keep
@IgnoreExtraProperties
data class StockMovement(
    @DocumentId val id: String = "",
    val productId: String = "",
    val productName: String = "",
    val sku: String = "",
    val unit: String = "Pcs",
    val changeQty: Double = 0.0,
    val movementType: String = StockMovementType.AUDIT_ADJUST.name,
    val reason: String = "",
    val performedByUid: String = "",
    val performedByName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun resolvedType(): StockMovementType = StockMovementType.fromRaw(movementType)
}

@Keep
@IgnoreExtraProperties
data class Payment(
    @DocumentId val id: String = "",
    val createdByUid: String = "",
    val createdByName: String = "",
    val partyId: String = "",
    val partyName: String = "",
    val orderId: String = "",
    val orderNumber: String = "",
    val amount: Double = 0.0,
    val paymentMode: String = PaymentMode.UPI.name,
    val paymentDateMillis: Long = 0L,
    val referenceNote: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedMode(): PaymentMode = PaymentMode.fromRaw(paymentMode)
}

data class PartyLedgerSummary(
    val party: Party,
    val totalOrderAmount: Double,
    val totalPaidAmount: Double,
    val pendingDue: Double,
    val orderCount: Int,
    val paymentCount: Int
)

data class LocationGroupMetric(
    val group: OrderLocationGroup,
    val orderCount: Int = 0,
    val estimateCount: Int = 0,
    val totalValue: Double = 0.0
) {
    val totalCount: Int get() = orderCount + estimateCount
}

data class DashboardMetrics(
    val totalOrders: Int = 0,
    val totalEstimates: Int = 0,
    val totalEstimateValue: Double = 0.0,
    val pendingOrders: Int = 0,
    val dispatchedOrders: Int = 0,
    val lowStockCount: Int = 0,
    val outOfStockCount: Int = 0,
    val totalStockUnits: Double = 0.0,
    val totalReceivable: Double = 0.0,
    val totalSalesValue: Double = 0.0,
    val totalCollected: Double = 0.0,
    val totalStockValuation: Double = 0.0,
    val locationGroupMetrics: List<LocationGroupMetric> = emptyList()
)

data class BackupPayload(
    val exportedAtMillis: Long,
    val parties: List<Party>,
    val products: List<Product>,
    val orders: List<Order>,
    val payments: List<Payment>
)
