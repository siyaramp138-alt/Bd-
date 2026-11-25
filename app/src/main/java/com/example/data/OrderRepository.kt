package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.math.roundToLong

/**
 * Dedicated Firestore repository for reading, creating, storing, updating, and deleting
 * [Order] and Sale Estimate documents in the named Firebase Firestore database (`R.string.firestore_database_id`).
 *
 * Role-based scoping aligns 1:1 with `firestore.rules`:
 * - [StaffRole.ADMIN] lists all documents in `/orders`.
 * - [StaffRole.EMPLOYEE] queries only documents where `createdByUid == auth.uid`.
 */
class OrderRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val ordersCol = db.collection("orders")
    private val productsCol = db.collection("products")
    private val stockMovementsCol = db.collection("stock_movements")
    private val paymentsCol = db.collection("payments")

    private fun requireSignedInUser(): Pair<String, String> {
        val user = auth.currentUser
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        val name = user.displayName?.trim()?.take(120).orEmpty()
            .ifBlank { user.email?.substringBefore("@")?.take(120).orEmpty() }
        return user.uid to name
    }

    private fun roundCurrency(value: Double): Double = (value * 100.0).roundToLong() / 100.0

    /**
     * Real-time stream of [Order] & Sale Estimate documents scoped by [currentRole].
     * Employees only receive documents they created (`createdByUid == uid`).
     */
    fun observeOrders(currentRole: StaffRole): Flow<List<Order>> = flow {
        val uid = requireSignedInUser().first
        val query = if (currentRole == StaffRole.ADMIN) {
            ordersCol
        } else {
            ordersCol.whereEqualTo("createdByUid", uid)
        }
        emitAll(
            query
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(Order::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, "orders")
                    }
                    throw error
                }
        )
    }

    /**
     * Real-time stream of a single [Order] document by ID.
     */
    fun observeOrder(orderId: String): Flow<Order?> = flow {
        val uid = requireSignedInUser().first
        require(orderId.isNotBlank()) { "Order ID cannot be blank." }
        val docPath = "orders/$orderId"
        emitAll(
            ordersCol.document(orderId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(Order::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docPath)
                    }
                    throw error
                }
        )
    }

    /**
     * One-shot Firestore read of a single [Order] document by ID.
     */
    suspend fun getOrderById(orderId: String): Result<Order?> {
        val docPath = "orders/$orderId"
        return try {
            requireSignedInUser()
            require(orderId.isNotBlank()) { "Order ID cannot be blank." }
            val snap = ordersCol.document(orderId).get().await()
            val order = if (snap.exists()) {
                snap.toObject(Order::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else {
                null
            }
            Result.success(order)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docPath)
            Result.failure(e)
        }
    }

    /**
     * Stores an [Order] or Sale Estimate instance directly in Firestore `/orders/{orderId}`.
     */
    suspend fun storeOrder(order: Order): Result<String> {
        var docPath = "orders"
        return try {
            val (uid, userName) = requireSignedInUser()
            require(order.partyId.isNotBlank()) { "Please select a valid party." }
            require(order.partyName.trim().isNotEmpty()) { "Party name is required." }
            require(order.items.isNotEmpty() && order.items.size <= 50) { "Add between 1 and 50 line items." }
            val resolvedDateMillis = if (order.orderDateMillis > 0L) order.orderDateMillis else System.currentTimeMillis()
            require(order.taxPercent in 0.0..100.0) { "Tax percent must be between 0 and 100." }
            require(order.discountAmount >= 0.0) { "Discount cannot be negative." }
            require(order.advancePaid >= 0.0) { "Advance paid cannot be negative." }
            require(order.photoBase64.length <= 400_000) { "Attached photo exceeds maximum sync size." }

            val normalizedItems = order.items.map { item ->
                val qty = roundCurrency(item.quantity)
                val unitPrice = roundCurrency(item.unitPrice)
                item.copy(
                    quantity = qty,
                    unitPrice = unitPrice,
                    lineTotal = roundCurrency(qty * unitPrice)
                )
            }
            val subtotal = roundCurrency(normalizedItems.sumOf { it.lineTotal })
            val clampedDiscount = minOf(roundCurrency(order.discountAmount), subtotal)
            val taxableBase = maxOf(0.0, subtotal - clampedDiscount)
            val taxAmount = roundCurrency(taxableBase * (order.taxPercent / 100.0))
            val grandTotal = roundCurrency(taxableBase + taxAmount)
            val clampedAdvance = if (order.isEstimate()) 0.0 else roundCurrency(order.advancePaid)

            val orderRef = ordersCol.document()
            docPath = orderRef.path
            val prefix = if (order.isEstimate()) "EST" else "BD"
            val resolvedOrderNumber = order.orderNumber.trim().take(40)
                .ifBlank { "$prefix-${orderRef.id.take(6).uppercase()}" }

            val normalizedOrder = order.copy(
                orderNumber = resolvedOrderNumber,
                orderDateMillis = resolvedDateMillis,
                items = normalizedItems,
                subtotal = subtotal,
                taxPercent = roundCurrency(order.taxPercent),
                taxAmount = taxAmount,
                discountAmount = clampedDiscount,
                grandTotal = grandTotal,
                advancePaid = clampedAdvance
            )

            orderRef.set(
                normalizedOrder.toFirestoreCreateMap(
                    uid = uid,
                    creatorName = userName,
                    resolvedOrderNumber = resolvedOrderNumber,
                    normalizedItems = normalizedItems
                )
            ).await()
            Result.success(orderRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    /**
     * Atomically creates and stores a new [Order] or Sale Estimate document.
     * - For [OrderDocumentType.ORDER]: deducts stock from `/products`, logs `/stock_movements`,
     *   and records any advance payment in `/payments`.
     * - For [OrderDocumentType.ESTIMATE]: stores quotation without deducting stock or adding ledger dues.
     */
    suspend fun createOrderWithStockDeduction(
        orderNumber: String,
        party: Party,
        documentType: OrderDocumentType = OrderDocumentType.ORDER,
        orderLocation: String = "",
        orderDateMillis: Long,
        status: OrderStatus,
        items: List<OrderLineItem>,
        taxPercent: Double,
        discountAmount: Double,
        advancePaid: Double,
        advancePaymentMode: PaymentMode,
        notes: String,
        photoBase64: String = ""
    ): Result<String> {
        var docPath = "orders"
        return try {
            val (uid, userName) = requireSignedInUser()
            require(party.id.isNotBlank()) { "Please select a valid party." }
            require(items.isNotEmpty() && items.size <= 50) { "Add between 1 and 50 line items." }
            require(orderDateMillis > 0L) { "Invalid order date." }
            require(taxPercent in 0.0..100.0) { "Tax percent must be between 0 and 100." }
            require(discountAmount >= 0.0) { "Discount cannot be negative." }
            require(advancePaid >= 0.0) { "Advance paid cannot be negative." }
            require(photoBase64.length <= 400_000) { "Attached photo exceeds maximum sync size." }

            val isEstimate = documentType == OrderDocumentType.ESTIMATE
            val normalizedItems = items.map { item ->
                val lineTotal = roundCurrency(item.quantity * item.unitPrice)
                item.copy(
                    quantity = roundCurrency(item.quantity),
                    unitPrice = roundCurrency(item.unitPrice),
                    lineTotal = lineTotal
                )
            }
            val subtotal = roundCurrency(normalizedItems.sumOf { it.lineTotal })
            val clampedDiscount = minOf(roundCurrency(discountAmount), subtotal)
            val taxableBase = maxOf(0.0, subtotal - clampedDiscount)
            val taxAmount = roundCurrency(taxableBase * (taxPercent / 100.0))
            val grandTotal = roundCurrency(taxableBase + taxAmount)
            val clampedAdvance = if (isEstimate) 0.0 else roundCurrency(advancePaid)

            val orderRef = ordersCol.document()
            docPath = orderRef.path
            val batch = db.batch()
            val prefix = if (isEstimate) "EST" else "BD"
            val resolvedOrderNumber = orderNumber.trim().take(40)
                .ifBlank { "$prefix-${orderRef.id.take(6).uppercase()}" }

            val orderPayload = Order(
                orderNumber = resolvedOrderNumber,
                documentType = documentType.name,
                partyId = party.id,
                partyName = party.name.trim().take(120),
                partyMobile = party.mobile.trim().take(20),
                partyGstOrAddress = party.gstOrAddress.trim().take(300),
                orderLocation = orderLocation.trim().take(160),
                orderDateMillis = orderDateMillis,
                status = if (isEstimate) OrderStatus.PENDING.name else status.name,
                items = normalizedItems,
                subtotal = subtotal,
                taxPercent = roundCurrency(taxPercent),
                taxAmount = taxAmount,
                discountAmount = clampedDiscount,
                grandTotal = grandTotal,
                advancePaid = clampedAdvance,
                notes = notes.trim().take(500),
                photoBase64 = photoBase64
            )

            batch.set(
                orderRef,
                orderPayload.toFirestoreCreateMap(
                    uid = uid,
                    creatorName = userName,
                    resolvedOrderNumber = resolvedOrderNumber,
                    normalizedItems = normalizedItems
                )
            )

            if (!isEstimate && clampedAdvance > 0.0) {
                val payRef = paymentsCol.document()
                batch.set(
                    payRef,
                    mapOf(
                        "createdByUid" to uid,
                        "createdByName" to userName,
                        "partyId" to party.id,
                        "partyName" to party.name.trim().take(120),
                        "orderId" to orderRef.id,
                        "orderNumber" to resolvedOrderNumber,
                        "amount" to clampedAdvance,
                        "paymentMode" to advancePaymentMode.name,
                        "paymentDateMillis" to orderDateMillis,
                        "referenceNote" to "Advance on Order $resolvedOrderNumber",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            batch.commit().await()
            Result.success(orderRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    /**
     * Converts a Sale Estimate into a confirmed Sale Order with atomic stock deduction,
     * preserving party, location, line items, pricing, notes, and attached camera photo.
     */
    suspend fun convertEstimateToOrder(
        estimate: Order,
        newOrderNumber: String
    ): Result<String> {
        val partySnapshot = Party(
            id = estimate.partyId,
            name = estimate.partyName,
            mobile = estimate.partyMobile,
            gstOrAddress = estimate.partyGstOrAddress
        )
        val mergedNotes = buildString {
            append("Converted from Estimate ${estimate.orderNumber}")
            if (estimate.notes.isNotBlank()) {
                append(" • ")
                append(estimate.notes.trim())
            }
        }.take(500)
        return createOrderWithStockDeduction(
            orderNumber = newOrderNumber,
            party = partySnapshot,
            documentType = OrderDocumentType.ORDER,
            orderLocation = estimate.orderLocation,
            orderDateMillis = System.currentTimeMillis(),
            status = OrderStatus.PENDING,
            items = estimate.items,
            taxPercent = estimate.taxPercent,
            discountAmount = estimate.discountAmount,
            advancePaid = 0.0,
            advancePaymentMode = PaymentMode.UPI,
            notes = mergedNotes,
            photoBase64 = estimate.photoBase64
        )
    }

    /**
     * Updates an existing [Order] or Sale Estimate document in Firestore `/orders/{orderId}`.
     */
    suspend fun updateOrder(
        orderId: String,
        orderNumber: String,
        party: Party,
        documentType: OrderDocumentType,
        orderLocation: String,
        orderDateMillis: Long,
        status: OrderStatus,
        items: List<OrderLineItem>,
        taxPercent: Double,
        discountAmount: Double,
        notes: String,
        photoBase64: String
    ): Result<String> {
        val docPath = "orders/$orderId"
        return try {
            requireSignedInUser()
            require(orderId.isNotBlank()) { "Order ID cannot be blank." }
            require(party.id.isNotBlank()) { "Please select a valid party." }
            require(items.isNotEmpty() && items.size <= 50) { "Add between 1 and 50 line items." }
            require(orderDateMillis > 0L) { "Invalid order date." }
            require(taxPercent in 0.0..100.0) { "Tax percent must be between 0 and 100." }
            require(discountAmount >= 0.0) { "Discount cannot be negative." }
            require(photoBase64.length <= 400_000) { "Attached photo exceeds maximum sync size." }

            val isEstimate = documentType == OrderDocumentType.ESTIMATE
            val normalizedItems = items.map { item ->
                val qty = roundCurrency(item.quantity)
                val unitPrice = roundCurrency(item.unitPrice)
                item.copy(
                    unit = item.unit.trim().take(20).ifBlank { "Pcs" },
                    quantity = qty,
                    unitPrice = unitPrice,
                    lineTotal = roundCurrency(qty * unitPrice)
                )
            }
            val subtotal = roundCurrency(normalizedItems.sumOf { it.lineTotal })
            val clampedDiscount = minOf(roundCurrency(discountAmount), subtotal)
            val taxableBase = maxOf(0.0, subtotal - clampedDiscount)
            val taxAmount = roundCurrency(taxableBase * (taxPercent / 100.0))
            val grandTotal = roundCurrency(taxableBase + taxAmount)
            val prefix = if (isEstimate) "EST" else "BD"
            val resolvedOrderNumber = orderNumber.trim().take(40)
                .ifBlank { "$prefix-${orderId.take(6).uppercase()}" }

            ordersCol.document(orderId).update(
                mapOf(
                    "orderNumber" to resolvedOrderNumber,
                    "documentType" to documentType.name,
                    "partyId" to party.id,
                    "partyName" to party.name.trim().take(120),
                    "partyMobile" to party.mobile.trim().take(20),
                    "partyGstOrAddress" to party.gstOrAddress.trim().take(300),
                    "orderLocation" to orderLocation.trim().take(160),
                    "orderDateMillis" to orderDateMillis,
                    "status" to if (isEstimate) OrderStatus.PENDING.name else status.name,
                    "items" to normalizedItems.map { it.toFirestoreMap() },
                    "subtotal" to subtotal,
                    "taxPercent" to roundCurrency(taxPercent),
                    "taxAmount" to taxAmount,
                    "discountAmount" to clampedDiscount,
                    "grandTotal" to grandTotal,
                    "notes" to notes.trim().take(500),
                    "photoBase64" to photoBase64,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(orderId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateOrderStatus(orderId: String, newStatus: OrderStatus): Result<Unit> {
        val docPath = "orders/$orderId"
        return try {
            requireSignedInUser()
            require(orderId.isNotBlank()) { "Order ID cannot be blank." }
            ordersCol.document(orderId).update(
                mapOf(
                    "status" to newStatus.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateOrderNotes(orderId: String, notes: String): Result<Unit> {
        val docPath = "orders/$orderId"
        return try {
            requireSignedInUser()
            require(orderId.isNotBlank()) { "Order ID cannot be blank." }
            val trimmedNotes = notes.trim()
            require(trimmedNotes.length <= 500) { "Order notes must be 500 characters or fewer." }
            ordersCol.document(orderId).update(
                mapOf(
                    "notes" to trimmedNotes,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateOrderLocation(orderId: String, orderLocation: String): Result<Unit> {
        val docPath = "orders/$orderId"
        return try {
            requireSignedInUser()
            require(orderId.isNotBlank()) { "Order ID cannot be blank." }
            val trimmedLoc = orderLocation.trim()
            require(trimmedLoc.length <= 160) { "Order location must be 160 characters or fewer." }
            ordersCol.document(orderId).update(
                mapOf(
                    "orderLocation" to trimmedLoc,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateOrderPhoto(orderId: String, photoBase64: String): Result<Unit> {
        val docPath = "orders/$orderId"
        return try {
            requireSignedInUser()
            require(orderId.isNotBlank()) { "Order ID cannot be blank." }
            require(photoBase64.length <= 400_000) { "Attached photo exceeds maximum sync size." }
            ordersCol.document(orderId).update(
                mapOf(
                    "photoBase64" to photoBase64,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteOrder(orderId: String): Result<Unit> {
        val docPath = "orders/$orderId"
        return try {
            requireSignedInUser()
            require(orderId.isNotBlank()) { "Order ID cannot be blank." }
            ordersCol.document(orderId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
