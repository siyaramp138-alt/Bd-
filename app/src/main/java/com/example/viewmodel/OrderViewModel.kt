package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.Order
import com.example.data.OrderDocumentType
import com.example.data.OrderLineItem
import com.example.data.OrderRepository
import com.example.data.OrderStatus
import com.example.data.Party
import com.example.data.PaymentMode
import com.example.data.StaffRole
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

sealed interface OrderUiState {
    data object Loading : OrderUiState
    data class Ready(
        val orders: List<Order>,
        val totalSalesValue: Double,
        val totalEstimateCount: Int,
        val totalEstimateValue: Double,
        val pendingCount: Int,
        val dispatchedCount: Int,
        val deliveredCount: Int
    ) : OrderUiState
    data class Error(val message: String) : OrderUiState
}

/**
 * ViewModel dedicated to creating, reading, storing, and updating [Order] & Sale Estimate documents
 * in Firebase Firestore via [OrderRepository].
 */
class OrderViewModel(
    private val orderRepository: OrderRepository,
    private val currentRole: StaffRole
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _selectedOrder = MutableStateFlow<Order?>(null)
    val selectedOrder: StateFlow<Order?> = _selectedOrder.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val uiState: StateFlow<OrderUiState> = orderRepository.observeOrders(currentRole)
        .map { rawOrders ->
            val sorted = rawOrders.sortedWith(
                compareByDescending<Order> { it.orderDateMillis }
                    .thenByDescending { it.createdAt ?: Timestamp(0, 0) }
            )
            val confirmedOrders = sorted.filterNot { it.isEstimate() }
            val estimates = sorted.filter { it.isEstimate() }
            OrderUiState.Ready(
                orders = sorted,
                totalSalesValue = confirmedOrders.sumOf { it.grandTotal },
                totalEstimateCount = estimates.size,
                totalEstimateValue = estimates.sumOf { it.grandTotal },
                pendingCount = confirmedOrders.count { it.resolvedStatus() == OrderStatus.PENDING },
                dispatchedCount = confirmedOrders.count { it.resolvedStatus() == OrderStatus.DISPATCHED },
                deliveredCount = confirmedOrders.count { it.resolvedStatus() == OrderStatus.DELIVERED }
            ) as OrderUiState
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing Firestore orders collection", error)
            emit(
                OrderUiState.Error(
                    firestoreError?.message ?: error.message ?: "Failed to load orders from Firebase."
                )
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = OrderUiState.Loading
        )

    fun nextSuggestedOrderNumber(
        existingOrders: List<Order>,
        documentType: OrderDocumentType = OrderDocumentType.ORDER
    ): String {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val count = existingOrders.count { it.resolvedDocumentType() == documentType } + 1
        val prefix = if (documentType == OrderDocumentType.ESTIMATE) "EST" else "BD"
        return String.format(Locale.US, "%s-%d-%04d", prefix, year, count)
    }

    fun loadOrder(orderId: String, onLoaded: (Order?) -> Unit = {}) {
        viewModelScope.launch {
            _busy.value = true
            val result = orderRepository.getOrderById(orderId)
            _busy.value = false
            result.onSuccess { order ->
                _selectedOrder.value = order
                onLoaded(order)
            }.onFailure { err ->
                _messages.tryEmit(err.message ?: "Could not load order")
            }
        }
    }

    fun storeOrder(order: Order, onSuccess: (String) -> Unit = {}) {
        viewModelScope.launch {
            _busy.value = true
            val result = orderRepository.storeOrder(order)
            _busy.value = false
            result.onSuccess { orderId ->
                _messages.tryEmit(
                    if (order.isEstimate()) "Sale Estimate saved to Firebase" else "Order stored in Firebase"
                )
                onSuccess(orderId)
            }.onFailure { err ->
                _messages.tryEmit(err.message ?: "Could not save document")
            }
        }
    }

    fun createOrder(
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
        photoBase64: String = "",
        onSuccess: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _busy.value = true
            val result = orderRepository.createOrderWithStockDeduction(
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
            _busy.value = false
            result.onSuccess { orderId ->
                _messages.tryEmit(
                    when {
                        documentType == OrderDocumentType.ESTIMATE && photoBase64.isNotBlank() ->
                            "Sale Estimate & photo saved to Firebase"
                        documentType == OrderDocumentType.ESTIMATE ->
                            "Sale Estimate created & synced"
                        photoBase64.isNotBlank() ->
                            "Order & photo saved to Firebase"
                        else ->
                            "Order created & synced to Firebase"
                    }
                )
                onSuccess(orderId)
            }.onFailure { err ->
                _messages.tryEmit(
                    err.message ?: if (documentType == OrderDocumentType.ESTIMATE) {
                        "Could not create sale estimate"
                    } else {
                        "Could not create order"
                    }
                )
            }
        }
    }

    fun updateOrder(
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
        photoBase64: String,
        onSuccess: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _busy.value = true
            val result = orderRepository.updateOrder(
                orderId = orderId,
                orderNumber = orderNumber,
                party = party,
                documentType = documentType,
                orderLocation = orderLocation,
                orderDateMillis = orderDateMillis,
                status = status,
                items = items,
                taxPercent = taxPercent,
                discountAmount = discountAmount,
                notes = notes,
                photoBase64 = photoBase64
            )
            _busy.value = false
            result.onSuccess { updatedId ->
                _messages.tryEmit(
                    if (documentType == OrderDocumentType.ESTIMATE) {
                        "Sale Estimate updated & synced"
                    } else {
                        "Order updated & synced"
                    }
                )
                onSuccess(updatedId)
            }.onFailure { err ->
                _messages.tryEmit(
                    err.message ?: if (documentType == OrderDocumentType.ESTIMATE) {
                        "Could not update sale estimate"
                    } else {
                        "Could not update order"
                    }
                )
            }
        }
    }

    fun convertEstimateToOrder(
        estimate: Order,
        existingOrders: List<Order>,
        onSuccess: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _busy.value = true
            val nextNumber = nextSuggestedOrderNumber(existingOrders, OrderDocumentType.ORDER)
            val result = orderRepository.convertEstimateToOrder(estimate, nextNumber)
            _busy.value = false
            result.onSuccess { newOrderId ->
                _messages.tryEmit("Estimate converted to Order $nextNumber")
                onSuccess(newOrderId)
            }.onFailure { err ->
                _messages.tryEmit(err.message ?: "Could not convert estimate to order")
            }
        }
    }

    fun updateOrderStatus(orderId: String, newStatus: OrderStatus) {
        viewModelScope.launch {
            _busy.value = true
            val result = orderRepository.updateOrderStatus(orderId, newStatus)
            _busy.value = false
            result.onSuccess {
                _messages.tryEmit("Order marked ${newStatus.label}")
            }.onFailure { err ->
                _messages.tryEmit(err.message ?: "Could not update order status")
            }
        }
    }

    fun updateOrderNotes(orderId: String, notes: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _busy.value = true
            val result = orderRepository.updateOrderNotes(orderId, notes)
            _busy.value = false
            result.onSuccess {
                _messages.tryEmit("Notes updated")
                onSuccess()
            }.onFailure { err ->
                _messages.tryEmit(err.message ?: "Could not update notes")
            }
        }
    }

    fun updateOrderLocation(orderId: String, orderLocation: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _busy.value = true
            val result = orderRepository.updateOrderLocation(orderId, orderLocation)
            _busy.value = false
            result.onSuccess {
                _messages.tryEmit("Location updated")
                onSuccess()
            }.onFailure { err ->
                _messages.tryEmit(err.message ?: "Could not update location")
            }
        }
    }

    fun updateOrderPhoto(orderId: String, photoBase64: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _busy.value = true
            val result = orderRepository.updateOrderPhoto(orderId, photoBase64)
            _busy.value = false
            result.onSuccess {
                _messages.tryEmit(if (photoBase64.isBlank()) "Photo removed" else "Photo saved to cloud")
                onSuccess()
            }.onFailure { err ->
                _messages.tryEmit(err.message ?: "Could not save photo")
            }
        }
    }

    fun deleteOrder(orderId: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _busy.value = true
            val result = orderRepository.deleteOrder(orderId)
            _busy.value = false
            result.onSuccess {
                _messages.tryEmit("Deleted successfully")
                onSuccess()
            }.onFailure { err ->
                _messages.tryEmit(err.message ?: "Could not delete record")
            }
        }
    }

    private companion object {
        const val TAG = "OrderViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
