package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.util.Locale

enum class OrderLocationGroup(val label: String) {
    LOCAL_INDORE("Local Indore"),
    OUT_OF_INDORE("Out of Indore"),
    SHOP_ORDER("Shop Order");

    companion object {
        fun fromRaw(raw: String?): OrderLocationGroup? {
            if (raw.isNullOrBlank()) return null
            return entries.firstOrNull {
                it.name.equals(raw, ignoreCase = true) || it.label.equals(raw, ignoreCase = true)
            }
        }

        fun fromLocationText(raw: String): OrderLocationGroup? {
            val lower = raw.trim().lowercase(Locale.ROOT)
            if (lower.isEmpty()) return null
            return when {
                lower.startsWith("shop order") ||
                    lower.contains("shop order") ||
                    lower == "shop" ||
                    lower.contains("counter") -> SHOP_ORDER

                lower.startsWith("out of indore") ||
                    lower.contains("out of indore") ||
                    lower.contains("outstation") ||
                    lower.contains("pithampur") ||
                    lower.contains("ujjain") ||
                    lower.contains("bhopal") ||
                    (lower.contains("dewas") && !lower.contains("dewas naka")) -> OUT_OF_INDORE

                lower.startsWith("local indore") ||
                    lower.contains("local indore") ||
                    lower.contains("indore") -> LOCAL_INDORE

                else -> null
            }
        }

        fun formatLocation(group: OrderLocationGroup, areaDetail: String): String {
            val detail = areaDetail.trim()
            if (detail.isEmpty()) return group.label
            if (detail.startsWith(group.label, ignoreCase = true)) return detail.take(160)
            return "${group.label} - $detail".take(160)
        }

        fun extractAreaDetail(raw: String, group: OrderLocationGroup?): String {
            val trimmed = raw.trim()
            if (group == null) return trimmed
            val prefixDash = "${group.label} - "
            val prefixEmDash = "${group.label} — "
            return when {
                trimmed.equals(group.label, ignoreCase = true) -> ""
                trimmed.startsWith(prefixDash, ignoreCase = true) -> trimmed.substring(prefixDash.length).trim()
                trimmed.startsWith(prefixEmDash, ignoreCase = true) -> trimmed.substring(prefixEmDash.length).trim()
                else -> trimmed
            }
        }
    }
}

enum class OrderDocumentType(val label: String) {
    ORDER("Sale Order"),
    ESTIMATE("Sale Estimate");

    companion object {
        fun fromRaw(raw: String): OrderDocumentType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: ORDER
    }
}

enum class OrderStatus(val label: String) {
    PENDING("Pending"),
    DISPATCHED("Dispatched"),
    DELIVERED("Delivered");

    companion object {
        fun fromRaw(raw: String): OrderStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: PENDING
    }
}

@Keep
@IgnoreExtraProperties
data class OrderLineItem(
    val productId: String = "",
    val productName: String = "",
    val sku: String = "",
    val unit: String = "Pcs",
    val quantity: Double = 1.0,
    val unitPrice: Double = 0.0,
    val lineTotal: Double = 0.0
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "productId" to productId,
        "productName" to productName,
        "sku" to sku,
        "unit" to unit,
        "quantity" to quantity,
        "unitPrice" to unitPrice,
        "lineTotal" to lineTotal
    )
}

/**
 * Firestore document model stored under `/orders/{orderId}`.
 * Supports both confirmed Sales Orders (`ORDER`) and Sale Estimates (`ESTIMATE`),
 * optional GPS/manual location, and an optional compressed camera photo attachment.
 */
@Keep
@IgnoreExtraProperties
data class Order(
    @DocumentId val id: String = "",
    val createdByUid: String = "",
    val createdByName: String = "",
    val orderNumber: String = "",
    val documentType: String = OrderDocumentType.ORDER.name,
    val partyId: String = "",
    val partyName: String = "",
    val partyMobile: String = "",
    val partyGstOrAddress: String = "",
    val orderLocation: String = "",
    val orderDateMillis: Long = 0L,
    val status: String = OrderStatus.PENDING.name,
    val items: List<OrderLineItem> = emptyList(),
    val subtotal: Double = 0.0,
    val taxPercent: Double = 0.0,
    val taxAmount: Double = 0.0,
    val discountAmount: Double = 0.0,
    val grandTotal: Double = 0.0,
    val advancePaid: Double = 0.0,
    val notes: String = "",
    val photoBase64: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun resolvedDocumentType(): OrderDocumentType = OrderDocumentType.fromRaw(documentType)

    fun isEstimate(): Boolean = resolvedDocumentType() == OrderDocumentType.ESTIMATE

    fun hasPhoto(): Boolean = photoBase64.isNotBlank()

    fun resolvedStatus(): OrderStatus = OrderStatus.fromRaw(status)

    fun resolvedLocation(): String = orderLocation.trim().ifBlank { "Unassigned Area" }

    fun resolvedLocationGroup(): OrderLocationGroup? = OrderLocationGroup.fromLocationText(orderLocation)

    fun resolvedLocationGroupLabel(): String = resolvedLocationGroup()?.label ?: "Other Area"

    fun balanceAfterAdvance(): Double = maxOf(0.0, grandTotal - advancePaid)

    fun totalItemQuantity(): Double = items.sumOf { maxOf(0.0, it.quantity) }

    fun toFirestoreCreateMap(
        uid: String,
        creatorName: String,
        resolvedOrderNumber: String,
        normalizedItems: List<OrderLineItem> = items
    ): Map<String, Any> = mapOf(
        "createdByUid" to uid,
        "createdByName" to creatorName,
        "orderNumber" to resolvedOrderNumber,
        "documentType" to resolvedDocumentType().name,
        "partyId" to partyId,
        "partyName" to partyName.trim().take(120),
        "partyMobile" to partyMobile.trim().take(20),
        "partyGstOrAddress" to partyGstOrAddress.trim().take(300),
        "orderLocation" to orderLocation.trim().take(160),
        "orderDateMillis" to orderDateMillis,
        "status" to resolvedStatus().name,
        "items" to normalizedItems.map { it.toFirestoreMap() },
        "subtotal" to subtotal,
        "taxPercent" to taxPercent,
        "taxAmount" to taxAmount,
        "discountAmount" to discountAmount,
        "grandTotal" to grandTotal,
        "advancePaid" to advancePaid,
        "notes" to notes.trim().take(500),
        "photoBase64" to photoBase64.take(400_000),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}
