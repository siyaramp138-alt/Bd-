package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BackupPayload
import com.example.data.BdEntRepository
import com.example.data.DashboardMetrics
import com.example.data.LocationGroupMetric
import com.example.data.Order
import com.example.data.OrderLineItem
import com.example.data.OrderLocationGroup
import com.example.data.OrderStatus
import com.example.data.Party
import com.example.data.PartyLedgerSummary
import com.example.data.PartyType
import com.example.data.Payment
import com.example.data.PaymentMode
import com.example.data.Product
import com.example.data.StaffMember
import com.example.data.StaffRole
import com.example.data.StockMovement
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

sealed interface BdEntUiState {
    data object Loading : BdEntUiState
    data class Ready(
        val parties: List<Party>,
        val products: List<Product>,
        val stockMovements: List<StockMovement>,
        val orders: List<Order>,
        val payments: List<Payment>,
        val staff: List<StaffMember>,
        val partySummaries: List<PartyLedgerSummary>,
        val metrics: DashboardMetrics
    ) : BdEntUiState
    data class Error(val message: String) : BdEntUiState
}

private data class CoreBusinessData(
    val parties: List<Party>,
    val products: List<Product>,
    val orders: List<Order>,
    val payments: List<Payment>
)

class BdEntViewModel(
    private val repository: BdEntRepository,
    private val currentRole: StaffRole
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    // Employees never list /staff to stay strictly compliant with RBAC Firestore rules.
    private val staffFlow = if (currentRole == StaffRole.ADMIN) {
        repository.observeStaff()
    } else {
        flowOf(emptyList())
    }

    private val stockMovementsFlow = if (currentRole == StaffRole.ADMIN) {
        repository.observeStockMovements()
    } else {
        flowOf(emptyList())
    }

    private val coreDataFlow = combine(
        repository.observeParties(),
        repository.observeProducts(),
        repository.observeOrders(currentRole),
        repository.observePayments()
    ) { parties, products, orders, payments ->
        CoreBusinessData(parties, products, orders, payments)
    }

    val uiState: StateFlow<BdEntUiState> = combine(
        coreDataFlow,
        stockMovementsFlow,
        staffFlow
    ) { core, rawMovements, rawStaff ->
        val parties = core.parties.sortedBy { it.name.lowercase(Locale.ROOT) }
        val products = core.products.sortedBy { it.name.lowercase(Locale.ROOT) }
        val stockMovements = rawMovements.sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
        val orders = core.orders.sortedWith(
            compareByDescending<Order> { it.orderDateMillis }
                .thenByDescending { it.createdAt ?: Timestamp(0, 0) }
        )
        val payments = core.payments.sortedWith(
            compareByDescending<Payment> { it.paymentDateMillis }
                .thenByDescending { it.createdAt ?: Timestamp(0, 0) }
        )
        val staff = rawStaff.sortedWith(
            compareByDescending<StaffMember> { it.active }
                .thenBy { if (it.resolvedRole() == StaffRole.ADMIN) 0 else 1 }
                .thenBy { it.name.lowercase(Locale.ROOT) }
        )

        val confirmedOrders = orders.filterNot { it.isEstimate() }
        val estimates = orders.filter { it.isEstimate() }

        val ordersByParty = confirmedOrders.groupBy { it.partyId }
        val paymentsByParty = payments.groupBy { it.partyId }

        val partySummaries = parties.map { party ->
            val pOrders = ordersByParty[party.id].orEmpty()
            val pPayments = paymentsByParty[party.id].orEmpty()
            val totalOrdersAmt = pOrders.sumOf { it.grandTotal }
            val totalPaidAmt = pPayments.sumOf { it.amount }
            val pendingDue = party.openingBalance + totalOrdersAmt - totalPaidAmt
            PartyLedgerSummary(
                party = party,
                totalOrderAmount = totalOrdersAmt,
                totalPaidAmount = totalPaidAmt,
                pendingDue = pendingDue,
                orderCount = pOrders.size,
                paymentCount = pPayments.size
            )
        }

        val totalReceivable = partySummaries.sumOf { maxOf(0.0, it.pendingDue) }
        val totalSalesValue = confirmedOrders.sumOf { it.grandTotal }
        val totalEstimateValue = estimates.sumOf { it.grandTotal }
        val totalCollected = payments.sumOf { it.amount }
        val lowStockCount = products.count { it.lowStockAlert() }
        val outOfStockCount = products.count { it.outOfStock() }
        val totalStockUnits = products.sumOf { maxOf(0.0, it.stockQty) }
        val totalStockVal = products.sumOf { it.stockValue() }

        val locationGroupMetrics = OrderLocationGroup.entries.map { group ->
            val groupRecords = orders.filter { it.resolvedLocationGroup() == group }
            val groupOrders = groupRecords.filterNot { it.isEstimate() }
            val groupEstimates = groupRecords.filter { it.isEstimate() }
            LocationGroupMetric(
                group = group,
                orderCount = groupOrders.size,
                estimateCount = groupEstimates.size,
                totalValue = groupOrders.sumOf { it.grandTotal }
            )
        }

        val metrics = DashboardMetrics(
            totalOrders = confirmedOrders.size,
            totalEstimates = estimates.size,
            totalEstimateValue = totalEstimateValue,
            pendingOrders = confirmedOrders.count { it.resolvedStatus() == OrderStatus.PENDING },
            dispatchedOrders = confirmedOrders.count { it.resolvedStatus() == OrderStatus.DISPATCHED },
            lowStockCount = lowStockCount,
            outOfStockCount = outOfStockCount,
            totalStockUnits = totalStockUnits,
            totalReceivable = totalReceivable,
            totalSalesValue = totalSalesValue,
            totalCollected = totalCollected,
            totalStockValuation = totalStockVal,
            locationGroupMetrics = locationGroupMetrics
        )

        BdEntUiState.Ready(
            parties = parties,
            products = products,
            stockMovements = stockMovements,
            orders = orders,
            payments = payments,
            staff = staff,
            partySummaries = partySummaries,
            metrics = metrics
        ) as BdEntUiState
    }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing BD Ent Firestore collections", error)
            emit(
                BdEntUiState.Error(
                    firestoreError?.message ?: error.message ?: "Failed to sync BD Enterprises data."
                )
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = BdEntUiState.Loading
        )

    fun nextSuggestedOrderNumber(existingOrders: List<Order>): String {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val nextNum = existingOrders.size + 1
        return String.format(Locale.US, "BD-%d-%04d", year, nextNum)
    }

    fun emitMessage(text: String) {
        _messages.tryEmit(text)
    }

    // --- STAFF MANAGEMENT (ADMIN ONLY) ---

    fun saveStaffMember(
        email: String,
        name: String,
        role: StaffRole,
        active: Boolean,
        isUpdate: Boolean,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _busy.value = true
            val result = repository.saveStaffMember(
                emailRaw = email,
                name = name,
                role = role,
                active = active,
                isUpdate = isUpdate
            )
            _busy.value = false
            result.onSuccess {
                emitMessage(if (isUpdate) "Staff access updated" else "${role.label} added successfully")
                onSuccess()
            }.onFailure { err ->
                emitMessage(err.message ?: "Could not save staff member")
            }
        }
    }

    fun toggleStaffActive(email: String, active: Boolean) {
        viewModelScope.launch {
            _busy.value = true
            val result = repository.toggleStaffActive(email, active)
            _busy.value = false
            result.onSuccess {
                emitMessage(if (active) "Staff access activated" else "Staff access suspended")
            }.onFailure { err ->
                emitMessage(err.message ?: "Could not update staff status")
            }
        }
    }

    fun deleteStaffMember(email: String) {
        viewModelScope.launch {
            _busy.value = true
            val result = repository.deleteStaffMember(email)
            _busy.value = false
            result.onSuccess {
                emitMessage("Staff member removed")
            }.onFailure { err ->
                emitMessage(err.message ?: "Could not remove staff member")
            }
        }
    }

    // --- PARTY OPERATIONS ---

    fun saveParty(
        existingId: String?,
        name: String,
        mobile: String,
        gstOrAddress: String,
        partyType: PartyType,
        openingBalance: Double,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _busy.value = true
            val result = repository.saveParty(
                existingId = existingId,
                name = name,
                mobile = mobile,
                gstOrAddress = gstOrAddress,
                partyType = partyType,
                openingBalance = openingBalance
            )
            _busy.value = false
            result.onSuccess {
                emitMessage(if (existingId.isNullOrBlank()) "Party added" else "Party updated")
                onSuccess()
            }.onFailure { err ->
                emitMessage(err.message ?: "Could not save party")
            }
        }
    }

    fun deleteParty(partyId: String) {
        viewModelScope.launch {
            _busy.value = true
            val result = repository.deleteParty(partyId)
            _busy.value = false
            result.onSuccess {
                emitMessage("Party removed")
            }.onFailure { err ->
                emitMessage(err.message ?: "Could not delete party")
            }
        }
    }

    // --- PRODUCT / INVENTORY OPERATIONS ---

    fun saveProduct(
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
        previousStockQty: Double? = null,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _busy.value = true
            val result = repository.saveProduct(
                existingId = existingId,
                name = name,
                sku = sku,
                unit = unit,
                category = category,
                location = location,
                purchaseRate = purchaseRate,
                sellingRate = sellingRate,
                stockQty = stockQty,
                minAlertLevel = minAlertLevel,
                previousStockQty = previousStockQty
            )
            _busy.value = false
            result.onSuccess {
                emitMessage(if (existingId.isNullOrBlank()) "Item added" else "Item updated")
                onSuccess()
            }.onFailure { err ->
                emitMessage(err.message ?: "Could not save item")
            }
        }
    }

    fun adjustStock(
        product: Product,
        deltaQty: Double,
        reason: String = "",
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _busy.value = true
            val result = repository.adjustProductStock(product, deltaQty, reason)
            _busy.value = false
            result.onSuccess {
                emitMessage(if (deltaQty > 0) "Stock added to ${product.name}" else "Stock deducted from ${product.name}")
                onSuccess()
            }.onFailure { err ->
                emitMessage(err.message ?: "Could not adjust stock")
            }
        }
    }

    fun deleteProduct(productId: String) {
        viewModelScope.launch {
            _busy.value = true
            val result = repository.deleteProduct(productId)
            _busy.value = false
            result.onSuccess {
                emitMessage("Item removed")
            }.onFailure { err ->
                emitMessage(err.message ?: "Could not remove item")
            }
        }
    }

    // --- ORDER OPERATIONS ---

    fun createOrder(
        orderNumber: String,
        party: Party,
        orderDateMillis: Long,
        status: OrderStatus,
        items: List<OrderLineItem>,
        taxPercent: Double,
        discountAmount: Double,
        advancePaid: Double,
        advancePaymentMode: PaymentMode,
        notes: String,
        onSuccess: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _busy.value = true
            val result = repository.createOrderWithStockDeduction(
                orderNumber = orderNumber,
                party = party,
                orderDateMillis = orderDateMillis,
                status = status,
                items = items,
                taxPercent = taxPercent,
                discountAmount = discountAmount,
                advancePaid = advancePaid,
                advancePaymentMode = advancePaymentMode,
                notes = notes
            )
            _busy.value = false
            result.onSuccess { orderId ->
                emitMessage("Order created")
                onSuccess(orderId)
            }.onFailure { err ->
                emitMessage(err.message ?: "Could not create order")
            }
        }
    }

    fun updateOrderStatus(orderId: String, newStatus: OrderStatus) {
        viewModelScope.launch {
            _busy.value = true
            val result = repository.updateOrderStatus(orderId, newStatus)
            _busy.value = false
            result.onSuccess {
                emitMessage("Order marked ${newStatus.label}")
            }.onFailure { err ->
                emitMessage(err.message ?: "Could not update status")
            }
        }
    }

    fun deleteOrder(orderId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _busy.value = true
            val result = repository.deleteOrder(orderId)
            _busy.value = false
            result.onSuccess {
                emitMessage("Order deleted")
                onSuccess()
            }.onFailure { err ->
                emitMessage(err.message ?: "Could not delete order")
            }
        }
    }

    // --- PAYMENT OPERATIONS ---

    fun recordPayment(
        party: Party,
        amount: Double,
        paymentMode: PaymentMode,
        paymentDateMillis: Long,
        referenceNote: String,
        orderId: String = "",
        orderNumber: String = "",
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _busy.value = true
            val result = repository.recordPayment(
                party = party,
                amount = amount,
                paymentMode = paymentMode,
                paymentDateMillis = paymentDateMillis,
                referenceNote = referenceNote,
                orderId = orderId,
                orderNumber = orderNumber
            )
            _busy.value = false
            result.onSuccess {
                emitMessage("Payment recorded")
                onSuccess()
            }.onFailure { err ->
                emitMessage(err.message ?: "Could not record payment")
            }
        }
    }

    fun deletePayment(paymentId: String) {
        viewModelScope.launch {
            _busy.value = true
            val result = repository.deletePayment(paymentId)
            _busy.value = false
            result.onSuccess {
                emitMessage("Payment entry deleted")
            }.onFailure { err ->
                emitMessage(err.message ?: "Could not delete payment")
            }
        }
    }

    // --- JSON RESTORE (ADMIN ONLY) ---

    fun restoreBackup(backup: BackupPayload, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _busy.value = true
            val result = repository.restoreBackup(backup)
            _busy.value = false
            result.onSuccess { count ->
                emitMessage("Restored $count records to BD Enterprises cloud")
                onSuccess()
            }.onFailure { err ->
                emitMessage(err.message ?: "Restore failed")
            }
        }
    }

    private companion object {
        const val TAG = "BdEntVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
