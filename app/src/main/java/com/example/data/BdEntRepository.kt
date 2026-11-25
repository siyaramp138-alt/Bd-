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
import java.util.Locale
import kotlin.math.roundToLong

const val BOOTSTRAP_ADMIN_EMAIL = "siyaramp138@gmail.com"

sealed interface AccessResolution {
    data object Loading : AccessResolution
    data class Authorized(
        val role: StaffRole,
        val displayName: String,
        val email: String,
        val isBootstrapOwner: Boolean
    ) : AccessResolution
    data class Unauthorized(
        val email: String,
        val reason: String
    ) : AccessResolution
    data class Error(val message: String) : AccessResolution
}

class BdEntRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val orderRepository = OrderRepository(db, auth)

    private val staffCol = db.collection("staff")
    private val partiesCol = db.collection("parties")
    private val productsCol = db.collection("products")
    private val stockMovementsCol = db.collection("stock_movements")
    private val ordersCol = db.collection("orders")
    private val paymentsCol = db.collection("payments")

    private fun requireSignedInUser(): Pair<String, String> {
        val user = auth.currentUser
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
        val name = user.displayName?.trim()?.take(120).orEmpty()
            .ifBlank { user.email?.substringBefore("@")?.take(120).orEmpty() }
        return user.uid to name
    }

    private fun requireSignedInEmail(): String {
        val email = auth.currentUser?.email?.trim()?.lowercase(Locale.ROOT)
        if (email.isNullOrBlank()) {
            throw IllegalStateException("Signed-in Google account has no email address.")
        }
        return email
    }

    suspend fun resolveCurrentUserAccess(): AccessResolution {
        val user = auth.currentUser
            ?: return AccessResolution.Unauthorized("", "Please sign in with Google to continue.")
        val email = user.email?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (email.isBlank() || !user.isEmailVerified) {
            return AccessResolution.Unauthorized(
                email = email,
                reason = "Your Google account email must be verified to access BD Enterprises Indore."
            )
        }
        val fallbackName = user.displayName?.trim().orEmpty().ifBlank { email.substringBefore("@") }

        // Bootstrapped Owner Admin has guaranteed Admin access
        if (email == BOOTSTRAP_ADMIN_EMAIL) {
            return AccessResolution.Authorized(
                role = StaffRole.ADMIN,
                displayName = fallbackName,
                email = email,
                isBootstrapOwner = true
            )
        }

        val docPath = "staff/$email"
        return try {
            val snap = staffCol.document(email).get().await()
            if (!snap.exists()) {
                AccessResolution.Unauthorized(
                    email = email,
                    reason = "This Gmail is not registered in BD Enterprises Indore yet. Ask your Admin to add $email in the Team tab."
                )
            } else {
                val staff = snap.toObject(StaffMember::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                if (staff == null || !staff.active) {
                    AccessResolution.Unauthorized(
                        email = email,
                        reason = "Your staff access for BD Enterprises Indore is currently suspended. Contact your Admin."
                    )
                } else {
                    AccessResolution.Authorized(
                        role = staff.resolvedRole(),
                        displayName = staff.name.ifBlank { fallbackName },
                        email = email,
                        isBootstrapOwner = false
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docPath)
            AccessResolution.Error(e.message ?: "Unable to verify staff permissions.")
        }
    }

    private fun <T> observeCollection(
        collectionPath: String,
        clazz: Class<T>
    ): Flow<List<T>> = flow {
        val uid = requireSignedInUser().first
        emitAll(
            db.collection(collectionPath)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(clazz, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, collectionPath)
                    }
                    throw error
                }
        )
    }

    fun observeStaff(): Flow<List<StaffMember>> = observeCollection("staff", StaffMember::class.java)
    fun observeParties(): Flow<List<Party>> = observeCollection("parties", Party::class.java)
    fun observeProducts(): Flow<List<Product>> = observeCollection("products", Product::class.java)
    fun observeStockMovements(): Flow<List<StockMovement>> =
        observeCollection("stock_movements", StockMovement::class.java)
    fun observeOrders(currentRole: StaffRole): Flow<List<Order>> = orderRepository.observeOrders(currentRole)
    fun observePayments(): Flow<List<Payment>> = observeCollection("payments", Payment::class.java)

    private fun roundCurrency(value: Double): Double = (value * 100.0).roundToLong() / 100.0

    // --- STAFF MANAGEMENT (ADMIN ONLY) ---

    suspend fun saveStaffMember(
        emailRaw: String,
        name: String,
        role: StaffRole,
        active: Boolean,
        isUpdate: Boolean
    ): Result<String> {
        val email = emailRaw.trim().lowercase(Locale.ROOT)
        val trimmedName = name.trim()
        val docPath = "staff/$email"
        return try {
            val adminEmail = requireSignedInEmail()
            val emailRegex = Regex("^[a-z0-9._%+\\-]+@[a-z0-9.\\-]+\\.[a-z]{2,}$")
            require(email.length in 5..128 && emailRegex.matches(email)) {
                "Enter a valid Gmail / email address."
            }
            require(trimmedName.length in 1..120) { "Staff name must be 1 to 120 characters." }

            val docRef = staffCol.document(email)
            if (isUpdate) {
                docRef.update(
                    mapOf(
                        "name" to trimmedName,
                        "role" to role.name,
                        "active" to active,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val existingSnap = docRef.get().await()
                if (existingSnap.exists()) {
                    docRef.update(
                        mapOf(
                            "name" to trimmedName,
                            "role" to role.name,
                            "active" to active,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                } else {
                    docRef.set(
                        mapOf(
                            "email" to email,
                            "name" to trimmedName,
                            "role" to role.name,
                            "active" to active,
                            "addedByEmail" to adminEmail,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
            }
            Result.success(email)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (isUpdate) OperationType.UPDATE else OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleStaffActive(email: String, active: Boolean): Result<Unit> {
        val normalized = email.trim().lowercase(Locale.ROOT)
        val docPath = "staff/$normalized"
        return try {
            requireSignedInEmail()
            require(normalized != BOOTSTRAP_ADMIN_EMAIL) { "Owner Admin cannot be suspended." }
            staffCol.document(normalized).update(
                mapOf(
                    "active" to active,
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

    suspend fun deleteStaffMember(email: String): Result<Unit> {
        val normalized = email.trim().lowercase(Locale.ROOT)
        val docPath = "staff/$normalized"
        return try {
            requireSignedInEmail()
            require(normalized != BOOTSTRAP_ADMIN_EMAIL) { "Owner Admin cannot be removed." }
            staffCol.document(normalized).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- PARTY OPERATIONS ---

    suspend fun saveParty(
        existingId: String?,
        name: String,
        mobile: String,
        gstOrAddress: String,
        partyType: PartyType,
        openingBalance: Double
    ): Result<String> {
        val trimmedName = name.trim()
        val trimmedMobile = mobile.trim()
        val trimmedGst = gstOrAddress.trim()
        var docPath = "parties"
        return try {
            val (uid, userName) = requireSignedInUser()
            require(trimmedName.length in 1..120) { "Party name must be 1 to 120 characters." }
            require(trimmedMobile.length <= 20) { "Mobile number is too long (max 20 chars)." }
            require(trimmedGst.length <= 300) { "GST/Address is too long (max 300 chars)." }
            require(openingBalance in -100_000_000.0..100_000_000.0) { "Opening balance out of bounds." }

            if (existingId.isNullOrBlank()) {
                val docRef = partiesCol.document()
                docPath = docRef.path
                docRef.set(
                    mapOf(
                        "createdByUid" to uid,
                        "createdByName" to userName,
                        "name" to trimmedName,
                        "mobile" to trimmedMobile,
                        "gstOrAddress" to trimmedGst,
                        "partyType" to partyType.name,
                        "openingBalance" to roundCurrency(openingBalance),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(docRef.id)
            } else {
                val docRef = partiesCol.document(existingId)
                docPath = docRef.path
                docRef.update(
                    mapOf(
                        "name" to trimmedName,
                        "mobile" to trimmedMobile,
                        "gstOrAddress" to trimmedGst,
                        "partyType" to partyType.name,
                        "openingBalance" to roundCurrency(openingBalance),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(existingId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(
                e,
                if (existingId.isNullOrBlank()) OperationType.CREATE else OperationType.UPDATE,
                docPath
            )
            Result.failure(e)
        }
    }

    suspend fun deleteParty(partyId: String): Result<Unit> {
        val docPath = "parties/$partyId"
        return try {
            requireSignedInUser()
            require(partyId.isNotBlank()) { "Party ID cannot be blank." }
            partiesCol.document(partyId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- PRODUCT / STOCK OPERATIONS ---

    suspend fun saveProduct(
        existingId: String?,
        name: String,
        sku: String,
        unit: String,
        category: String = "",
        location: String = "",
        purchaseRate: Double,
        sellingRate: Double,
        stockQty: Double,
        minAlertLevel: Double,
        previousStockQty: Double? = null
    ): Result<String> {
        val trimmedName = name.trim()
        val trimmedSku = sku.trim()
        val trimmedUnit = unit.trim().ifBlank { "Pcs" }
        val trimmedCategory = category.trim().take(60)
        val trimmedLocation = location.trim().take(80)
        var docPath = "products"
        return try {
            val (uid, userName) = requireSignedInUser()
            require(trimmedName.length in 1..120) { "Product name must be 1 to 120 characters." }
            require(trimmedSku.length <= 60) { "SKU is too long (max 60 chars)." }
            require(trimmedUnit.length in 1..20) { "Unit must be 1 to 20 characters." }
            require(purchaseRate in 0.0..100_000_000.0) { "Purchase rate must be 0 or greater." }
            require(sellingRate in 0.0..100_000_000.0) { "Selling rate must be 0 or greater." }
            require(stockQty in -100_000_000.0..100_000_000.0) { "Stock quantity out of bounds." }
            require(minAlertLevel in 0.0..100_000_000.0) { "Minimum alert level must be 0 or greater." }

            val roundedStock = roundCurrency(stockQty)
            val batch = db.batch()

            if (existingId.isNullOrBlank()) {
                val docRef = productsCol.document()
                docPath = docRef.path
                batch.set(
                    docRef,
                    mapOf(
                        "createdByUid" to uid,
                        "name" to trimmedName,
                        "sku" to trimmedSku,
                        "unit" to trimmedUnit,
                        "category" to trimmedCategory,
                        "location" to trimmedLocation,
                        "purchaseRate" to roundCurrency(purchaseRate),
                        "sellingRate" to roundCurrency(sellingRate),
                        "stockQty" to roundedStock,
                        "minAlertLevel" to roundCurrency(minAlertLevel),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                if (roundedStock != 0.0) {
                    batch.set(
                        stockMovementsCol.document(),
                        mapOf(
                            "productId" to docRef.id,
                            "productName" to trimmedName,
                            "sku" to trimmedSku,
                            "unit" to trimmedUnit,
                            "changeQty" to roundedStock,
                            "movementType" to StockMovementType.INITIAL_STOCK.name,
                            "reason" to "Opening stock added",
                            "performedByUid" to uid,
                            "performedByName" to userName,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
                Result.success(docRef.id)
            } else {
                val docRef = productsCol.document(existingId)
                docPath = docRef.path
                batch.update(
                    docRef,
                    mapOf(
                        "name" to trimmedName,
                        "sku" to trimmedSku,
                        "unit" to trimmedUnit,
                        "category" to trimmedCategory,
                        "location" to trimmedLocation,
                        "purchaseRate" to roundCurrency(purchaseRate),
                        "sellingRate" to roundCurrency(sellingRate),
                        "stockQty" to roundedStock,
                        "minAlertLevel" to roundCurrency(minAlertLevel),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                val stockDelta = previousStockQty?.let { roundCurrency(roundedStock - it) } ?: 0.0
                if (stockDelta != 0.0) {
                    batch.set(
                        stockMovementsCol.document(),
                        mapOf(
                            "productId" to existingId,
                            "productName" to trimmedName,
                            "sku" to trimmedSku,
                            "unit" to trimmedUnit,
                            "changeQty" to stockDelta,
                            "movementType" to StockMovementType.AUDIT_ADJUST.name,
                            "reason" to "Catalog stock level updated",
                            "performedByUid" to uid,
                            "performedByName" to userName,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
                Result.success(existingId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(
                e,
                if (existingId.isNullOrBlank()) OperationType.CREATE else OperationType.UPDATE,
                docPath
            )
            Result.failure(e)
        }
    }

    suspend fun adjustProductStock(
        product: Product,
        deltaQty: Double,
        reason: String = ""
    ): Result<Unit> {
        val docPath = "products/${product.id}"
        return try {
            val (uid, userName) = requireSignedInUser()
            require(product.id.isNotBlank()) { "Product ID cannot be blank." }
            val roundedDelta = roundCurrency(deltaQty)
            require(roundedDelta != 0.0) { "Adjustment quantity cannot be zero." }
            require(roundedDelta in -100_000_000.0..100_000_000.0) { "Adjustment quantity out of bounds." }

            val movementType = if (roundedDelta > 0.0) {
                StockMovementType.RESTOCK
            } else {
                StockMovementType.DEDUCT
            }
            val cleanReason = reason.trim().take(240).ifBlank {
                if (roundedDelta > 0.0) "Manual stock addition" else "Manual stock deduction"
            }

            val batch = db.batch()
            batch.update(
                productsCol.document(product.id),
                mapOf(
                    "stockQty" to FieldValue.increment(roundedDelta),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.set(
                stockMovementsCol.document(),
                mapOf(
                    "productId" to product.id,
                    "productName" to product.name.trim().take(120).ifBlank { "Item" },
                    "sku" to product.sku.trim().take(60),
                    "unit" to product.unit.trim().take(20).ifBlank { "Pcs" },
                    "changeQty" to roundedDelta,
                    "movementType" to movementType.name,
                    "reason" to cleanReason,
                    "performedByUid" to uid,
                    "performedByName" to userName,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteProduct(productId: String): Result<Unit> {
        val docPath = "products/$productId"
        return try {
            requireSignedInUser()
            require(productId.isNotBlank()) { "Product ID cannot be blank." }
            productsCol.document(productId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- ORDER OPERATIONS (DELEGATED TO OrderRepository) ---

    suspend fun getOrderById(orderId: String): Result<Order?> = orderRepository.getOrderById(orderId)

    suspend fun storeOrder(order: Order): Result<String> = orderRepository.storeOrder(order)

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
    ): Result<String> = orderRepository.createOrderWithStockDeduction(
        orderNumber = orderNumber,
        party = party,
        documentType = documentType,
        orderLocation = orderLocation,
        orderDateMillis = orderDateMillis,
        status = status,
        items = items,
        taxPercent = taxPercent,
        discountAmount = discountAmount,
        advancePaid = advancePaid,
        advancePaymentMode = advancePaymentMode,
        notes = notes,
        photoBase64 = photoBase64
    )

    suspend fun convertEstimateToOrder(estimate: Order, newOrderNumber: String): Result<String> =
        orderRepository.convertEstimateToOrder(estimate, newOrderNumber)

    suspend fun updateOrderStatus(orderId: String, newStatus: OrderStatus): Result<Unit> =
        orderRepository.updateOrderStatus(orderId, newStatus)

    suspend fun updateOrderNotes(orderId: String, notes: String): Result<Unit> =
        orderRepository.updateOrderNotes(orderId, notes)

    suspend fun updateOrderLocation(orderId: String, orderLocation: String): Result<Unit> =
        orderRepository.updateOrderLocation(orderId, orderLocation)

    suspend fun updateOrderPhoto(orderId: String, photoBase64: String): Result<Unit> =
        orderRepository.updateOrderPhoto(orderId, photoBase64)

    suspend fun deleteOrder(orderId: String): Result<Unit> =
        orderRepository.deleteOrder(orderId)

    // --- PAYMENT OPERATIONS ---

    suspend fun recordPayment(
        party: Party,
        amount: Double,
        paymentMode: PaymentMode,
        paymentDateMillis: Long,
        referenceNote: String,
        orderId: String = "",
        orderNumber: String = ""
    ): Result<String> {
        var docPath = "payments"
        return try {
            val (uid, userName) = requireSignedInUser()
            require(party.id.isNotBlank()) { "Please select a valid party." }
            require(amount > 0.0 && amount <= 1_000_000_000.0) { "Payment amount must be greater than ₹0." }
            require(paymentDateMillis > 0L) { "Invalid payment date." }

            val docRef = paymentsCol.document()
            docPath = docRef.path
            docRef.set(
                mapOf(
                    "createdByUid" to uid,
                    "createdByName" to userName,
                    "partyId" to party.id,
                    "partyName" to party.name.trim().take(120),
                    "orderId" to orderId.trim().take(128),
                    "orderNumber" to orderNumber.trim().take(40),
                    "amount" to roundCurrency(amount),
                    "paymentMode" to paymentMode.name,
                    "paymentDateMillis" to paymentDateMillis,
                    "referenceNote" to referenceNote.trim().take(240),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deletePayment(paymentId: String): Result<Unit> {
        val docPath = "payments/$paymentId"
        return try {
            requireSignedInUser()
            require(paymentId.isNotBlank()) { "Payment ID cannot be blank." }
            paymentsCol.document(paymentId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    // --- JSON BACKUP RESTORE (ADMIN ONLY) ---

    suspend fun restoreBackup(backup: BackupPayload): Result<Int> {
        return try {
            val (uid, userName) = requireSignedInUser()
            var restoredCount = 0

            val safePartyIdMap = mutableMapOf<String, String>()
            val partyEntries = backup.parties.map { p ->
                val targetId = sanitizeDocId(p.id) ?: partiesCol.document().id
                if (p.id.isNotBlank()) safePartyIdMap[p.id] = targetId
                targetId to p
            }

            val phase1Ops = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any>>>()
            for ((docId, party) in partyEntries) {
                val name = party.name.trim().take(120)
                if (name.isEmpty()) continue
                phase1Ops.add(
                    partiesCol.document(docId) to mapOf(
                        "createdByUid" to uid,
                        "createdByName" to userName,
                        "name" to name,
                        "mobile" to party.mobile.trim().take(20),
                        "gstOrAddress" to party.gstOrAddress.trim().take(300),
                        "partyType" to party.resolvedType().name,
                        "openingBalance" to roundCurrency(party.openingBalance.coerceIn(-100_000_000.0, 100_000_000.0)),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            for (product in backup.products) {
                val name = product.name.trim().take(120)
                if (name.isEmpty()) continue
                val docId = sanitizeDocId(product.id) ?: productsCol.document().id
                phase1Ops.add(
                    productsCol.document(docId) to mapOf(
                        "createdByUid" to uid,
                        "name" to name,
                        "sku" to product.sku.trim().take(60),
                        "unit" to product.unit.trim().take(20).ifBlank { "Pcs" },
                        "category" to product.category.trim().take(60),
                        "location" to product.location.trim().take(80),
                        "purchaseRate" to roundCurrency(product.purchaseRate.coerceIn(0.0, 100_000_000.0)),
                        "sellingRate" to roundCurrency(product.sellingRate.coerceIn(0.0, 100_000_000.0)),
                        "stockQty" to roundCurrency(product.stockQty.coerceIn(-100_000_000.0, 100_000_000.0)),
                        "minAlertLevel" to roundCurrency(product.minAlertLevel.coerceIn(0.0, 100_000_000.0)),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            for (chunk in phase1Ops.chunked(400)) {
                val batch = db.batch()
                for ((ref, data) in chunk) batch.set(ref, data)
                batch.commit().await()
                restoredCount += chunk.size
            }

            val phase2Ops = mutableListOf<Pair<com.google.firebase.firestore.DocumentReference, Map<String, Any>>>()
            for (order in backup.orders) {
                val mappedPartyId = safePartyIdMap[order.partyId] ?: sanitizeDocId(order.partyId) ?: continue
                if (order.items.isEmpty()) continue
                val docId = sanitizeDocId(order.id) ?: ordersCol.document().id
                phase2Ops.add(
                    ordersCol.document(docId) to mapOf(
                        "createdByUid" to uid,
                        "createdByName" to userName,
                        "orderNumber" to order.orderNumber.trim().take(40).ifBlank { "BD-RESTORED" },
                        "documentType" to order.resolvedDocumentType().name,
                        "partyId" to mappedPartyId,
                        "partyName" to order.partyName.trim().take(120).ifBlank { "Party" },
                        "partyMobile" to order.partyMobile.trim().take(20),
                        "partyGstOrAddress" to order.partyGstOrAddress.trim().take(300),
                        "orderLocation" to order.orderLocation.trim().take(160),
                        "orderDateMillis" to maxOf(1L, order.orderDateMillis),
                        "status" to order.resolvedStatus().name,
                        "items" to order.items.take(50).map { it.toFirestoreMap() },
                        "subtotal" to roundCurrency(maxOf(0.0, order.subtotal)),
                        "taxPercent" to roundCurrency(order.taxPercent.coerceIn(0.0, 100.0)),
                        "taxAmount" to roundCurrency(maxOf(0.0, order.taxAmount)),
                        "discountAmount" to roundCurrency(maxOf(0.0, order.discountAmount)),
                        "grandTotal" to roundCurrency(maxOf(0.0, order.grandTotal)),
                        "advancePaid" to roundCurrency(maxOf(0.0, order.advancePaid)),
                        "notes" to order.notes.trim().take(500),
                        "photoBase64" to order.photoBase64.take(400_000),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            for (payment in backup.payments) {
                val mappedPartyId = safePartyIdMap[payment.partyId] ?: sanitizeDocId(payment.partyId) ?: continue
                if (payment.amount <= 0.0) continue
                val docId = sanitizeDocId(payment.id) ?: paymentsCol.document().id
                phase2Ops.add(
                    paymentsCol.document(docId) to mapOf(
                        "createdByUid" to uid,
                        "createdByName" to userName,
                        "partyId" to mappedPartyId,
                        "partyName" to payment.partyName.trim().take(120).ifBlank { "Party" },
                        "orderId" to payment.orderId.trim().take(128),
                        "orderNumber" to payment.orderNumber.trim().take(40),
                        "amount" to roundCurrency(payment.amount),
                        "paymentMode" to payment.resolvedMode().name,
                        "paymentDateMillis" to maxOf(1L, payment.paymentDateMillis),
                        "referenceNote" to payment.referenceNote.trim().take(240),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            for (chunk in phase2Ops.chunked(400)) {
                val batch = db.batch()
                for ((ref, data) in chunk) batch.set(ref, data)
                batch.commit().await()
                restoredCount += chunk.size
            }

            Result.success(restoredCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "backup_restore")
            Result.failure(e)
        }
    }

    private fun sanitizeDocId(raw: String): String? {
        val cleaned = raw.trim()
        val regex = Regex("^[a-zA-Z0-9_\\-]{1,128}$")
        return if (regex.matches(cleaned)) cleaned else null
    }
}
