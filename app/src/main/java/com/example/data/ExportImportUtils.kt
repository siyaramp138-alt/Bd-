package com.example.data

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object BdEntFormatter {

    private val dateDisplayFormat = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
    private val dateTimeDisplayFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.ENGLISH)
    private val dateFileFormat = SimpleDateFormat("yyyyMMdd_HHmm", Locale.ENGLISH)
    private val dateCsvFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)

    /**
     * Formats a number into Indian Rupee notation (e.g. ₹1,24,500.00 or -₹2,500.00).
     */
    fun formatInr(amount: Double, showDecimals: Boolean = true): String {
        val negative = amount < -0.004
        val absVal = abs(amount)
        val rounded = if (showDecimals) {
            String.format(Locale.US, "%.2f", absVal)
        } else {
            String.format(Locale.US, "%.0f", absVal)
        }
        val parts = rounded.split(".")
        val intPart = parts[0]
        val formattedInt = formatIndianGrouping(intPart)
        val prefix = if (negative) "-₹" else "₹"
        return if (showDecimals && parts.size > 1) {
            "$prefix$formattedInt.${parts[1]}"
        } else {
            "$prefix$formattedInt"
        }
    }

    private fun formatIndianGrouping(digits: String): String {
        if (digits.length <= 3) return digits
        val lastThree = digits.takeLast(3)
        val rest = digits.dropLast(3)
        val sb = StringBuilder()
        var i = rest.length
        while (i > 0) {
            val start = maxOf(0, i - 2)
            if (sb.isNotEmpty()) sb.insert(0, ',')
            sb.insert(0, rest.substring(start, i))
            i = start
        }
        return "$sb,$lastThree"
    }

    fun formatQty(qty: Double): String {
        return if (abs(qty - qty.toLong().toDouble()) < 0.001) {
            qty.toLong().toString()
        } else {
            DecimalFormat("0.##").format(qty)
        }
    }

    fun formatDate(millis: Long): String {
        if (millis <= 0L) return "—"
        return dateDisplayFormat.format(Date(millis))
    }

    fun formatDateTime(millis: Long): String {
        if (millis <= 0L) return "Just now"
        return dateTimeDisplayFormat.format(Date(millis))
    }

    fun formatCsvDate(millis: Long): String {
        if (millis <= 0L) return ""
        return dateCsvFormat.format(Date(millis))
    }

    fun fileTimestamp(): String = dateFileFormat.format(Date())
}

object ExportImportUtils {

    private const val UTF8_BOM = "\uFEFF"

    private fun csvEscape(raw: String): String {
        val escaped = raw.replace("\"", "\"\"")
        return if (escaped.contains(',') || escaped.contains('"') || escaped.contains('\n')) {
            "\"$escaped\""
        } else {
            escaped
        }
    }

    fun buildOrdersCsv(orders: List<Order>): String {
        val sb = StringBuilder(UTF8_BOM)
        sb.appendLine("Order Number,Type,Order Date,Party Name,Location,Mobile,Status,Items Count,Subtotal (INR),Discount (INR),Tax %,Tax Amount (INR),Grand Total (INR),Advance Paid (INR),Balance Due (INR),Notes")
        orders.sortedByDescending { it.orderDateMillis }.forEach { o ->
            sb.appendLine(
                listOf(
                    csvEscape(o.orderNumber),
                    csvEscape(o.resolvedDocumentType().label),
                    csvEscape(BdEntFormatter.formatCsvDate(o.orderDateMillis)),
                    csvEscape(o.partyName),
                    csvEscape(o.orderLocation),
                    csvEscape(o.partyMobile),
                    csvEscape(if (o.isEstimate()) "Estimate" else o.resolvedStatus().label),
                    o.items.size.toString(),
                    String.format(Locale.US, "%.2f", o.subtotal),
                    String.format(Locale.US, "%.2f", o.discountAmount),
                    String.format(Locale.US, "%.2f", o.taxPercent),
                    String.format(Locale.US, "%.2f", o.taxAmount),
                    String.format(Locale.US, "%.2f", o.grandTotal),
                    String.format(Locale.US, "%.2f", o.advancePaid),
                    String.format(Locale.US, "%.2f", if (o.isEstimate()) 0.0 else o.balanceAfterAdvance()),
                    csvEscape(o.notes)
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    fun buildStockCsv(products: List<Product>): String {
        val sb = StringBuilder(UTF8_BOM)
        sb.appendLine("Item Name,SKU,Category,Location,Unit,Purchase Rate (INR),Selling Rate (INR),Current Stock,Min Alert Level,Stock Value (INR),Alert Status")
        products.sortedBy { it.name.lowercase(Locale.ROOT) }.forEach { p ->
            val status = when {
                p.outOfStock() -> "OUT OF STOCK"
                p.lowStockAlert() -> "LOW STOCK"
                else -> "OK"
            }
            sb.appendLine(
                listOf(
                    csvEscape(p.name),
                    csvEscape(p.sku),
                    csvEscape(p.category),
                    csvEscape(p.location),
                    csvEscape(p.unit),
                    String.format(Locale.US, "%.2f", p.purchaseRate),
                    String.format(Locale.US, "%.2f", p.sellingRate),
                    BdEntFormatter.formatQty(p.stockQty),
                    BdEntFormatter.formatQty(p.minAlertLevel),
                    String.format(Locale.US, "%.2f", p.stockValue()),
                    status
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    fun buildPartyDuesCsv(summaries: List<PartyLedgerSummary>): String {
        val sb = StringBuilder(UTF8_BOM)
        sb.appendLine("Party Name,Type,Mobile,GST / Address,Opening Balance (INR),Total Orders (INR),Total Paid (INR),Pending Due (INR),Order Count,Payment Count")
        summaries.sortedByDescending { it.pendingDue }.forEach { s ->
            sb.appendLine(
                listOf(
                    csvEscape(s.party.name),
                    csvEscape(s.party.resolvedType().label),
                    csvEscape(s.party.mobile),
                    csvEscape(s.party.gstOrAddress),
                    String.format(Locale.US, "%.2f", s.party.openingBalance),
                    String.format(Locale.US, "%.2f", s.totalOrderAmount),
                    String.format(Locale.US, "%.2f", s.totalPaidAmount),
                    String.format(Locale.US, "%.2f", s.pendingDue),
                    s.orderCount.toString(),
                    s.paymentCount.toString()
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    fun buildJsonBackup(
        parties: List<Party>,
        products: List<Product>,
        orders: List<Order>,
        payments: List<Payment>
    ): String {
        val root = JSONObject().apply {
            put("company", "BD Ent")
            put("schemaVersion", 1)
            put("exportedAtMillis", System.currentTimeMillis())
            put("parties", JSONArray().apply {
                parties.forEach { p ->
                    put(JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("mobile", p.mobile)
                        put("gstOrAddress", p.gstOrAddress)
                        put("partyType", p.partyType)
                        put("openingBalance", p.openingBalance)
                    })
                }
            })
            put("products", JSONArray().apply {
                products.forEach { pr ->
                    put(JSONObject().apply {
                        put("id", pr.id)
                        put("name", pr.name)
                        put("sku", pr.sku)
                        put("unit", pr.unit)
                        put("category", pr.category)
                        put("location", pr.location)
                        put("purchaseRate", pr.purchaseRate)
                        put("sellingRate", pr.sellingRate)
                        put("stockQty", pr.stockQty)
                        put("minAlertLevel", pr.minAlertLevel)
                    })
                }
            })
            put("orders", JSONArray().apply {
                orders.forEach { o ->
                    put(JSONObject().apply {
                        put("id", o.id)
                        put("orderNumber", o.orderNumber)
                        put("documentType", o.documentType)
                        put("partyId", o.partyId)
                        put("partyName", o.partyName)
                        put("partyMobile", o.partyMobile)
                        put("partyGstOrAddress", o.partyGstOrAddress)
                        put("orderLocation", o.orderLocation)
                        put("orderDateMillis", o.orderDateMillis)
                        put("status", o.status)
                        put("subtotal", o.subtotal)
                        put("taxPercent", o.taxPercent)
                        put("taxAmount", o.taxAmount)
                        put("discountAmount", o.discountAmount)
                        put("grandTotal", o.grandTotal)
                        put("advancePaid", o.advancePaid)
                        put("notes", o.notes)
                        put("photoBase64", o.photoBase64)
                        put("items", JSONArray().apply {
                            o.items.forEach { it ->
                                put(JSONObject().apply {
                                    put("productId", it.productId)
                                    put("productName", it.productName)
                                    put("sku", it.sku)
                                    put("unit", it.unit)
                                    put("quantity", it.quantity)
                                    put("unitPrice", it.unitPrice)
                                    put("lineTotal", it.lineTotal)
                                })
                            }
                        })
                    })
                }
            })
            put("payments", JSONArray().apply {
                payments.forEach { pay ->
                    put(JSONObject().apply {
                        put("id", pay.id)
                        put("partyId", pay.partyId)
                        put("partyName", pay.partyName)
                        put("orderId", pay.orderId)
                        put("orderNumber", pay.orderNumber)
                        put("amount", pay.amount)
                        put("paymentMode", pay.paymentMode)
                        put("paymentDateMillis", pay.paymentDateMillis)
                        put("referenceNote", pay.referenceNote)
                    })
                }
            })
        }
        return root.toString(2)
    }

    fun parseJsonBackup(jsonText: String): Result<BackupPayload> = runCatching {
        val root = JSONObject(jsonText)
        val partiesArr = root.optJSONArray("parties") ?: JSONArray()
        val productsArr = root.optJSONArray("products") ?: JSONArray()
        val ordersArr = root.optJSONArray("orders") ?: JSONArray()
        val paymentsArr = root.optJSONArray("payments") ?: JSONArray()

        val parties = buildList {
            for (i in 0 until partiesArr.length()) {
                val o = partiesArr.getJSONObject(i)
                add(
                    Party(
                        id = o.optString("id", ""),
                        name = o.optString("name", ""),
                        mobile = o.optString("mobile", ""),
                        gstOrAddress = o.optString("gstOrAddress", ""),
                        partyType = o.optString("partyType", PartyType.CUSTOMER.name),
                        openingBalance = o.optDouble("openingBalance", 0.0)
                    )
                )
            }
        }

        val products = buildList {
            for (i in 0 until productsArr.length()) {
                val o = productsArr.getJSONObject(i)
                add(
                    Product(
                        id = o.optString("id", ""),
                        name = o.optString("name", ""),
                        sku = o.optString("sku", ""),
                        unit = o.optString("unit", "Pcs"),
                        category = o.optString("category", ""),
                        location = o.optString("location", ""),
                        purchaseRate = o.optDouble("purchaseRate", 0.0),
                        sellingRate = o.optDouble("sellingRate", 0.0),
                        stockQty = o.optDouble("stockQty", 0.0),
                        minAlertLevel = o.optDouble("minAlertLevel", 5.0)
                    )
                )
            }
        }

        val orders = buildList {
            for (i in 0 until ordersArr.length()) {
                val o = ordersArr.getJSONObject(i)
                val itemsArr = o.optJSONArray("items") ?: JSONArray()
                val items = buildList {
                    for (j in 0 until itemsArr.length()) {
                        val itemObj = itemsArr.getJSONObject(j)
                        add(
                            OrderLineItem(
                                productId = itemObj.optString("productId", ""),
                                productName = itemObj.optString("productName", ""),
                                sku = itemObj.optString("sku", ""),
                                unit = itemObj.optString("unit", "Pcs"),
                                quantity = itemObj.optDouble("quantity", 1.0),
                                unitPrice = itemObj.optDouble("unitPrice", 0.0),
                                lineTotal = itemObj.optDouble("lineTotal", 0.0)
                            )
                        )
                    }
                }
                add(
                    Order(
                        id = o.optString("id", ""),
                        orderNumber = o.optString("orderNumber", ""),
                        documentType = o.optString("documentType", OrderDocumentType.ORDER.name),
                        partyId = o.optString("partyId", ""),
                        partyName = o.optString("partyName", ""),
                        partyMobile = o.optString("partyMobile", ""),
                        partyGstOrAddress = o.optString("partyGstOrAddress", ""),
                        orderLocation = o.optString("orderLocation", ""),
                        orderDateMillis = o.optLong("orderDateMillis", System.currentTimeMillis()),
                        status = o.optString("status", OrderStatus.PENDING.name),
                        items = items,
                        subtotal = o.optDouble("subtotal", 0.0),
                        taxPercent = o.optDouble("taxPercent", 0.0),
                        taxAmount = o.optDouble("taxAmount", 0.0),
                        discountAmount = o.optDouble("discountAmount", 0.0),
                        grandTotal = o.optDouble("grandTotal", 0.0),
                        advancePaid = o.optDouble("advancePaid", 0.0),
                        notes = o.optString("notes", ""),
                        photoBase64 = o.optString("photoBase64", "")
                    )
                )
            }
        }

        val payments = buildList {
            for (i in 0 until paymentsArr.length()) {
                val o = paymentsArr.getJSONObject(i)
                add(
                    Payment(
                        id = o.optString("id", ""),
                        partyId = o.optString("partyId", ""),
                        partyName = o.optString("partyName", ""),
                        orderId = o.optString("orderId", ""),
                        orderNumber = o.optString("orderNumber", ""),
                        amount = o.optDouble("amount", 0.0),
                        paymentMode = o.optString("paymentMode", PaymentMode.UPI.name),
                        paymentDateMillis = o.optLong("paymentDateMillis", System.currentTimeMillis()),
                        referenceNote = o.optString("referenceNote", "")
                    )
                )
            }
        }

        BackupPayload(
            exportedAtMillis = root.optLong("exportedAtMillis", System.currentTimeMillis()),
            parties = parties,
            products = products,
            orders = orders,
            payments = payments
        )
    }

    fun writeTextToUri(context: Context, uri: Uri, content: String): Result<Unit> = runCatching {
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(content.toByteArray(Charsets.UTF_8))
        } ?: error("Unable to open output stream for selected file.")
    }

    fun readTextFromUri(context: Context, uri: Uri): Result<String> = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader(Charsets.UTF_8).readText()
        } ?: error("Unable to read selected backup file.")
    }

    fun shareTextFile(
        context: Context,
        fileName: String,
        mimeType: String,
        content: String,
        chooserTitle: String
    ): Result<Unit> = runCatching {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, fileName)
        file.writeText(content, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, chooserTitle)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    fun buildWhatsAppReminderMessage(summary: PartyLedgerSummary): String {
        val dueFormatted = BdEntFormatter.formatInr(summary.pendingDue)
        return buildString {
            append("Hello ${summary.party.name},\n\n")
            append("This is a friendly payment reminder from *BD Ent*.\n")
            append("Your current pending due balance is *$dueFormatted*.\n\n")
            append("Kindly arrange the payment at your earliest convenience via UPI, Bank Transfer, or Cash. ")
            append("Please ignore if already paid.\n\n")
            append("Thank you for your business!\n")
            append("— BD Ent")
        }
    }

    fun launchWhatsAppReminder(context: Context, summary: PartyLedgerSummary): Result<Unit> = runCatching {
        val message = buildWhatsAppReminderMessage(summary)
        val encoded = URLEncoder.encode(message, "UTF-8")
        val digitsOnly = summary.party.mobile.filter { it.isDigit() }
        val normalizedPhone = when {
            digitsOnly.length == 10 -> "91$digitsOnly"
            digitsOnly.length > 10 -> digitsOnly
            else -> ""
        }
        val url = if (normalizedPhone.isNotEmpty()) {
            "https://wa.me/$normalizedPhone?text=$encoded"
        } else {
            "https://wa.me/?text=$encoded"
        }
        val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            context.startActivity(viewIntent)
        } catch (_: Exception) {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Send Payment Reminder"))
        }
    }

    /**
     * Generates a crisp A4 PDF Invoice/Order Slip for BD Ent and shares it via FileProvider.
     */
    fun generateAndShareInvoicePdf(context: Context, order: Order): Result<Unit> = runCatching {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        drawInvoicePage(page.canvas, order)
        pdfDocument.finishPage(page)

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeOrderNo = order.orderNumber.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
        val filePrefix = if (order.isEstimate()) "BD_Ent_Estimate" else "BD_Ent_Invoice"
        val docLabel = if (order.isEstimate()) "Sale Estimate" else "Invoice"
        val pdfFile = File(exportDir, "${filePrefix}_$safeOrderNo.pdf")
        pdfFile.outputStream().use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "BD Ent $docLabel ${order.orderNumber}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share $docLabel PDF"))
    }

    private fun drawInvoicePage(canvas: Canvas, order: Order) {
        val brandBlue = Color.parseColor("#0F3D91")
        val slateDark = Color.parseColor("#0F172A")
        val slateMuted = Color.parseColor("#475569")
        val borderLight = Color.parseColor("#CBD5E1")
        val rowTint = Color.parseColor("#F4F7FB")

        val fillPaint = Paint().apply { style = Paint.Style.FILL; color = brandBlue }
        canvas.drawRect(0f, 0f, 595f, 96f, fillPaint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 24f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DBEAFE")
            textSize = 11f
        }
        val isEst = order.isEstimate()
        canvas.drawText("BD Ent", 36f, 44f, titlePaint)
        canvas.drawText(
            if (isEst) "SALE ESTIMATE / QUOTATION" else "TAX INVOICE / ORDER SLIP",
            36f,
            66f,
            subtitlePaint
        )

        val rightHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 12f
            textAlign = Paint.Align.RIGHT
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText(
            "${if (isEst) "Estimate #" else "Invoice #"}: ${order.orderNumber}",
            559f,
            42f,
            rightHeaderPaint
        )
        rightHeaderPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        canvas.drawText("Date: ${BdEntFormatter.formatDate(order.orderDateMillis)}", 559f, 60f, rightHeaderPaint)
        canvas.drawText(
            if (isEst) "Type: SALE ESTIMATE" else "Status: ${order.resolvedStatus().label.uppercase(Locale.ENGLISH)}",
            559f,
            78f,
            rightHeaderPaint
        )

        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = brandBlue
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = slateDark
            textSize = 11f
        }
        val boldBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = slateDark
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = slateMuted
            textSize = 10f
        }
        val linePaint = Paint().apply {
            color = borderLight
            strokeWidth = 1f
        }

        var y = 124f
        canvas.drawText(if (isEst) "ESTIMATE FOR" else "BILL TO", 36f, y, sectionPaint)
        y += 18f
        canvas.drawText(order.partyName, 36f, y, boldBodyPaint)
        y += 15f
        if (order.partyMobile.isNotBlank()) {
            canvas.drawText("Mobile: ${order.partyMobile}", 36f, y, mutedPaint)
            y += 14f
        }
        if (order.partyGstOrAddress.isNotBlank()) {
            canvas.drawText("GST/Address: ${order.partyGstOrAddress.take(78)}", 36f, y, mutedPaint)
            y += 15f
        }
        if (order.orderLocation.isNotBlank()) {
            canvas.drawText("Location: ${order.orderLocation.take(78)}", 36f, y, mutedPaint)
            y += 16f
        }

        y += 12f
        val tableHeaderBg = Paint().apply { color = rowTint }
        canvas.drawRect(36f, y - 14f, 559f, y + 10f, tableHeaderBg)
        canvas.drawText("#", 42f, y + 2f, boldBodyPaint)
        canvas.drawText("Item", 64f, y + 2f, boldBodyPaint)
        canvas.drawText("Qty", 335f, y + 2f, boldBodyPaint)
        canvas.drawText("Rate", 405f, y + 2f, boldBodyPaint)
        val rightBold = Paint(boldBodyPaint).apply { textAlign = Paint.Align.RIGHT }
        val rightBody = Paint(bodyPaint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("Amount", 553f, y + 2f, rightBold)
        y += 16f
        canvas.drawLine(36f, y, 559f, y, linePaint)

        order.items.take(24).forEachIndexed { idx, item ->
            y += 18f
            canvas.drawText("${idx + 1}", 42f, y, bodyPaint)
            val label = if (item.sku.isNotBlank()) "${item.productName} (${item.sku})" else item.productName
            canvas.drawText(label.take(40), 64f, y, bodyPaint)
            canvas.drawText("${BdEntFormatter.formatQty(item.quantity)} ${item.unit}", 335f, y, bodyPaint)
            canvas.drawText(BdEntFormatter.formatInr(item.unitPrice), 405f, y, bodyPaint)
            canvas.drawText(BdEntFormatter.formatInr(item.lineTotal), 553f, y, rightBody)
            y += 8f
            canvas.drawLine(36f, y, 559f, y, linePaint)
        }

        y += 24f
        fun drawTotalRow(label: String, value: String, bold: Boolean = false) {
            val lp = if (bold) boldBodyPaint else mutedPaint
            val vp = if (bold) rightBold else rightBody
            canvas.drawText(label, 360f, y, lp)
            canvas.drawText(value, 553f, y, vp)
            y += 18f
        }

        drawTotalRow("Subtotal", BdEntFormatter.formatInr(order.subtotal))
        if (order.discountAmount > 0.0) {
            drawTotalRow("Discount", "-${BdEntFormatter.formatInr(order.discountAmount)}")
        }
        if (order.taxAmount > 0.0) {
            drawTotalRow("Tax (${BdEntFormatter.formatQty(order.taxPercent)}%)", BdEntFormatter.formatInr(order.taxAmount))
        }
        canvas.drawLine(360f, y - 10f, 559f, y - 10f, linePaint)
        drawTotalRow(if (isEst) "Estimated Total" else "Grand Total", BdEntFormatter.formatInr(order.grandTotal), bold = true)
        if (!isEst) {
            drawTotalRow("Advance Paid", BdEntFormatter.formatInr(order.advancePaid))
            drawTotalRow("Balance Due", BdEntFormatter.formatInr(order.balanceAfterAdvance()), bold = true)
        }

        if (order.notes.isNotBlank()) {
            y += 14f
            canvas.drawText("Notes: ${order.notes.take(85)}", 36f, y, mutedPaint)
        }

        canvas.drawText("Generated by BD Ent Business Manager", 36f, 810f, mutedPaint)
    }

    /**
     * Triggers Android's native Print / Save as PDF dialog using a styled HTML invoice slip.
     */
    fun printInvoiceHtml(context: Context, order: Order) {
        val webView = WebView(context)
        val rowsHtml = order.items.mapIndexed { idx, item ->
            """
            <tr>
              <td>${idx + 1}</td>
              <td><strong>${escapeHtml(item.productName)}</strong>${if (item.sku.isNotBlank()) "<br/><span class='muted'>${escapeHtml(item.sku)}</span>" else ""}</td>
              <td class="num">${BdEntFormatter.formatQty(item.quantity)} ${escapeHtml(item.unit)}</td>
              <td class="num">${BdEntFormatter.formatInr(item.unitPrice)}</td>
              <td class="num">${BdEntFormatter.formatInr(item.lineTotal)}</td>
            </tr>
            """.trimIndent()
        }.joinToString("\n")

        val isEst = order.isEstimate()
        val photoHtml = if (order.photoBase64.isNotBlank()) {
            """<div class='photo'><strong>Attached Photo:</strong><br/><img src='data:image/jpeg;base64,${order.photoBase64}' style='max-width:240px;max-height:240px;border-radius:8px;margin-top:8px;border:1px solid #CBD5E1;' /></div>"""
        } else {
            ""
        }

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8" />
              <style>
                body { font-family: sans-serif; color: #0F172A; margin: 24px; }
                .header { background: ${if (isEst) "#0F766E" else "#0F3D91"}; color: white; padding: 20px; border-radius: 8px; display: flex; justify-content: space-between; }
                .title { font-size: 24px; font-weight: bold; }
                .sub { font-size: 12px; color: #DBEAFE; margin-top: 4px; }
                .meta { text-align: right; font-size: 13px; line-height: 1.6; }
                .party { margin: 20px 0; padding: 14px; background: #F4F7FB; border-radius: 8px; }
                table { width: 100%; border-collapse: collapse; margin-top: 12px; }
                th { background: #E7EEF8; text-align: left; padding: 10px; font-size: 12px; }
                td { border-bottom: 1px solid #E2E8F0; padding: 10px; font-size: 13px; }
                .num { text-align: right; }
                .muted { color: #64748B; font-size: 11px; }
                .totals { margin-top: 18px; width: 280px; margin-left: auto; }
                .totals tr td { border: none; padding: 5px 8px; }
                .grand { font-weight: bold; font-size: 15px; border-top: 2px solid #0F3D91 !important; color: #0F3D91; }
                .notes { margin-top: 24px; font-size: 12px; color: #475569; }
                .photo { margin-top: 18px; font-size: 12px; color: #475569; }
              </style>
            </head>
            <body>
              <div class="header">
                <div>
                  <div class="title">BD Ent</div>
                  <div class="sub">${if (isEst) "SALE ESTIMATE / QUOTATION" else "INVOICE / ORDER SLIP"}</div>
                </div>
                <div class="meta">
                  <div><strong>${escapeHtml(order.orderNumber)}</strong></div>
                  <div>Date: ${BdEntFormatter.formatDate(order.orderDateMillis)}</div>
                  <div>${if (isEst) "Type: Sale Estimate" else "Status: ${escapeHtml(order.resolvedStatus().label)}"}</div>
                </div>
              </div>
              <div class="party">
                <div class="muted">${if (isEst) "ESTIMATE FOR" else "BILL TO"}</div>
                <div><strong>${escapeHtml(order.partyName)}</strong></div>
                ${if (order.partyMobile.isNotBlank()) "<div>Mobile: ${escapeHtml(order.partyMobile)}</div>" else ""}
                ${if (order.partyGstOrAddress.isNotBlank()) "<div>GST / Address: ${escapeHtml(order.partyGstOrAddress)}</div>" else ""}
                ${if (order.orderLocation.isNotBlank()) "<div>Location: ${escapeHtml(order.orderLocation)}</div>" else ""}
              </div>
              <table>
                <thead>
                  <tr><th>#</th><th>Item</th><th class="num">Qty</th><th class="num">Rate</th><th class="num">Total</th></tr>
                </thead>
                <tbody>$rowsHtml</tbody>
              </table>
              <table class="totals">
                <tr><td>Subtotal</td><td class="num">${BdEntFormatter.formatInr(order.subtotal)}</td></tr>
                ${if (order.discountAmount > 0.0) "<tr><td>Discount</td><td class='num'>-${BdEntFormatter.formatInr(order.discountAmount)}</td></tr>" else ""}
                ${if (order.taxAmount > 0.0) "<tr><td>Tax (${BdEntFormatter.formatQty(order.taxPercent)}%)</td><td class='num'>${BdEntFormatter.formatInr(order.taxAmount)}</td></tr>" else ""}
                <tr class="grand"><td>${if (isEst) "Estimated Total" else "Grand Total"}</td><td class="num">${BdEntFormatter.formatInr(order.grandTotal)}</td></tr>
                ${if (!isEst) "<tr><td>Advance Paid</td><td class='num'>${BdEntFormatter.formatInr(order.advancePaid)}</td></tr><tr><td><strong>Balance Due</strong></td><td class='num'><strong>${BdEntFormatter.formatInr(order.balanceAfterAdvance())}</strong></td></tr>" else ""}
              </table>
              ${if (order.notes.isNotBlank()) "<div class='notes'><strong>Notes:</strong> ${escapeHtml(order.notes)}</div>" else ""}
              $photoHtml
            </body>
            </html>
        """.trimIndent()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false
            override fun onPageFinished(view: WebView?, url: String?) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
                val jobName = "BD_Ent_${if (isEst) "Estimate" else "Invoice"}_${order.orderNumber}"
                val adapter = webView.createPrintDocumentAdapter(jobName)
                printManager.print(jobName, adapter, PrintAttributes.Builder().build())
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/HTML", "UTF-8", null)
    }

    private fun escapeHtml(input: String): String = input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
