package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.ExportUtils
import com.example.core.Formatters
import com.example.core.GatewayInitResult
import com.example.core.InstitutionConfig
import com.example.domain.model.Enrolment
import com.example.domain.model.Payment
import com.example.domain.model.PaymentProvider
import com.example.domain.model.PaymentSubmission
import com.example.domain.model.PaymentTxStatus
import com.example.domain.model.SubmissionStatus
import com.example.domain.model.UserAccount
import com.example.domain.model.UserRole
import com.example.ui.components.EmptyStateCard
import com.example.ui.components.KpiStatCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.JyEmerald
import com.example.ui.theme.JyNavy
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning

@Composable
fun PaymentsScreen(
    userAccount: UserAccount?,
    enrolments: List<Enrolment>,
    payments: List<Payment>,
    submissions: List<PaymentSubmission>,
    gatewayInit: GatewayInitResult?,
    onClearGatewayInit: () -> Unit,
    onInitiateOnlinePayment: (Enrolment, Long, PaymentProvider) -> Unit,
    onVerifyOnlinePayment: (Payment) -> Unit,
    onSubmitOpayTransfer: (Enrolment, Long, String, String) -> Unit,
    onAdminVerifyOpay: (PaymentSubmission, Boolean, String) -> Unit,
    onAdminRecordPayment: (Enrolment, Long, PaymentProvider, String) -> Unit,
    onExportFinancialPdf: () -> Unit,
    onExportFinancialExcel: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val isAdmin = userAccount?.userRole == UserRole.ADMINISTRATOR
    var selectedTab by remember { mutableIntStateOf(0) }
    var payDialogEnrolment by remember { mutableStateOf<Enrolment?>(null) }
    var opayDialogEnrolment by remember { mutableStateOf<Enrolment?>(null) }
    var adminRecordEnrolment by remember { mutableStateOf<Enrolment?>(null) }
    var search by remember { mutableStateOf("") }

    val totalCollected = payments.filter { it.status == PaymentTxStatus.SUCCESSFUL.name }.sumOf { it.amountNgn }
    val pendingSubs = submissions.filter { it.status == SubmissionStatus.PENDING_VERIFICATION.name }
    val outstandingTotal = enrolments.sumOf { it.balanceNgn }

    val filteredPayments = remember(payments, search) {
        val q = search.trim().lowercase()
        if (q.isEmpty()) payments else payments.filter {
            it.reference.lowercase().contains(q) ||
                it.studentName.lowercase().contains(q) ||
                it.courseTitle.lowercase().contains(q)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Pay / Balances") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("OPay (${pendingSubs.size})") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Transactions") })
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    KpiStatCard(
                        title = if (isAdmin) "Total Collected" else "Total Paid",
                        value = Formatters.formatNgn(totalCollected),
                        subtitle = "${payments.count { it.status == PaymentTxStatus.SUCCESSFUL.name }} paid",
                        icon = Icons.Default.AccountBalance,
                        accentColor = StatusSuccess,
                        modifier = Modifier.weight(1f)
                    )
                    KpiStatCard(
                        title = "Outstanding",
                        value = Formatters.formatNgn(outstandingTotal),
                        subtitle = "${pendingSubs.size} pending",
                        icon = Icons.Default.Payments,
                        accentColor = StatusWarning,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            when (selectedTab) {
                0 -> {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Official OPay Bank Transfer Account", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text("Bank: ${InstitutionConfig.OPAY_BANK_NAME}")
                                Text("Account Name: ${InstitutionConfig.OPAY_ACCOUNT_NAME}")
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "Account Number: ${InstitutionConfig.OPAY_ACCOUNT_NUMBER}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = {
                                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cb.setPrimaryClip(ClipData.newPlainText("OPay Account", InstitutionConfig.OPAY_ACCOUNT_NUMBER))
                                        onShowMessage("Copied OPay account ${InstitutionConfig.OPAY_ACCOUNT_NUMBER}")
                                    }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy account number")
                                    }
                                }
                            }
                        }
                    }

                    val unpaidEnrolments = enrolments.filter { it.balanceNgn > 0L }
                    if (unpaidEnrolments.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Default.Check,
                                title = "No Outstanding Balances",
                                subtitle = "All active enrolments are settled."
                            )
                        }
                    } else {
                        items(unpaidEnrolments, key = { it.id }) { enr ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(enr.courseTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text("${enr.studentName} • Ref: ${enr.reference}", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        "Balance Due: ${Formatters.formatNgn(enr.balanceNgn)} (of ${Formatters.formatNgn(enr.courseFeeNgn)})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = JyEmerald
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { payDialogEnrolment = enr }) {
                                            Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Paystack / Online")
                                        }
                                        OutlinedButton(onClick = { opayDialogEnrolment = enr }) {
                                            Text("OPay Transfer")
                                        }
                                    }
                                    if (isAdmin) {
                                        TextButton(onClick = { adminRecordEnrolment = enr }) {
                                            Text("Admin: Record Verified Payment")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    if (submissions.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Default.ReceiptLong,
                                title = "No OPay Submissions",
                                subtitle = "Submitted bank transfer proofs and references appear here for verification."
                            )
                        }
                    } else {
                        items(submissions, key = { it.id }) { sub ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(sub.submissionRef, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                        StatusPill(
                                            text = sub.status,
                                            containerColor = when (sub.status) {
                                                SubmissionStatus.VERIFIED.name -> StatusSuccess.copy(alpha = 0.15f)
                                                SubmissionStatus.REJECTED.name -> MaterialTheme.colorScheme.errorContainer
                                                else -> StatusWarning.copy(alpha = 0.15f)
                                            },
                                            contentColor = when (sub.status) {
                                                SubmissionStatus.VERIFIED.name -> StatusSuccess
                                                SubmissionStatus.REJECTED.name -> MaterialTheme.colorScheme.error
                                                else -> StatusWarning
                                            }
                                        )
                                    }
                                    Text("${sub.studentName} • ${sub.courseTitle}", style = MaterialTheme.typography.titleSmall)
                                    Text("Amount: ${Formatters.formatNgn(sub.amountNgn)} • Transfer Ref: ${sub.transferReference}", style = MaterialTheme.typography.bodySmall)
                                    if (sub.reviewerNotes.isNotBlank()) {
                                        Text("Notes: ${sub.reviewerNotes}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (isAdmin && sub.status == SubmissionStatus.PENDING_VERIFICATION.name) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(onClick = { onAdminVerifyOpay(sub, true, "Confirmed in OPay 6118366524") }) {
                                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Verify & Credit")
                                            }
                                            OutlinedButton(onClick = { onAdminVerifyOpay(sub, false, "Transfer not found") }) {
                                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Reject")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    item {
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            label = { Text("Search payment reference, student, or course") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (isAdmin) {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onExportFinancialPdf, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("PDF Report")
                                }
                                OutlinedButton(onClick = onExportFinancialExcel, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Excel CSV")
                                }
                            }
                        }
                    }
                    if (filteredPayments.isEmpty()) {
                        item {
                            EmptyStateCard(
                                icon = Icons.Default.Payments,
                                title = "No Transactions Found",
                                subtitle = "Completed Paystack and verified OPay payments appear here with PDF receipts."
                            )
                        }
                    } else {
                        items(filteredPayments, key = { it.id }) { pay ->
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(Formatters.formatNgn(pay.amountNgn), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = JyNavy)
                                        StatusPill(
                                            text = pay.status,
                                            containerColor = if (pay.status == PaymentTxStatus.SUCCESSFUL.name) StatusSuccess.copy(alpha = 0.15f) else StatusInfo.copy(alpha = 0.15f),
                                            contentColor = if (pay.status == PaymentTxStatus.SUCCESSFUL.name) StatusSuccess else StatusInfo
                                        )
                                    }
                                    Text("${pay.courseTitle} • ${pay.studentName}", style = MaterialTheme.typography.bodyMedium)
                                    Text("Ref: ${pay.reference} • ${pay.provider}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (pay.status == PaymentTxStatus.SUCCESSFUL.name) {
                                            OutlinedButton(onClick = {
                                                val uri = ExportUtils.generateReceiptPdf(context, pay)
                                                ExportUtils.shareExportUri(context, uri, "application/pdf", "Share Payment Receipt")
                                            }) {
                                                Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("PDF Receipt")
                                            }
                                        }
                                        if (pay.status == PaymentTxStatus.PENDING.name && (pay.provider == PaymentProvider.PAYSTACK.name || pay.provider == PaymentProvider.FLUTTERWAVE.name)) {
                                            Button(onClick = { onVerifyOnlinePayment(pay) }) {
                                                Text("Verify on Server")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    payDialogEnrolment?.let { enr ->
        OnlineCheckoutDialog(
            enrolment = enr,
            onDismiss = { payDialogEnrolment = null },
            onProceed = { amount, provider ->
                onInitiateOnlinePayment(enr, amount, provider)
                payDialogEnrolment = null
            }
        )
    }

    opayDialogEnrolment?.let { enr ->
        OpayTransferDialog(
            enrolment = enr,
            onDismiss = { opayDialogEnrolment = null },
            onSubmit = { amount, ref, proof ->
                onSubmitOpayTransfer(enr, amount, ref, proof)
                opayDialogEnrolment = null
            }
        )
    }

    adminRecordEnrolment?.let { enr ->
        AdminRecordPaymentDialog(
            enrolment = enr,
            onDismiss = { adminRecordEnrolment = null },
            onRecord = { amount, provider, ref ->
                onAdminRecordPayment(enr, amount, provider, ref)
                adminRecordEnrolment = null
            }
        )
    }

    gatewayInit?.let { init ->
        AlertDialog(
            onDismissRequest = onClearGatewayInit,
            title = { Text("${init.provider.name} Checkout (${init.reference})") },
            text = { Text(init.message) },
            confirmButton = {
                if (init.authorizationUrl.isNotBlank()) {
                    Button(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(init.authorizationUrl)))
                        onClearGatewayInit()
                    }) {
                        Text("Open Secure Checkout")
                    }
                } else {
                    TextButton(onClick = onClearGatewayInit) { Text("OK") }
                }
            },
            dismissButton = {
                if (init.authorizationUrl.isNotBlank()) {
                    TextButton(onClick = onClearGatewayInit) { Text("Close") }
                }
            }
        )
    }
}

@Composable
private fun OnlineCheckoutDialog(
    enrolment: Enrolment,
    onDismiss: () -> Unit,
    onProceed: (Long, PaymentProvider) -> Unit
) {
    var amountText by remember { mutableStateOf(enrolment.balanceNgn.toString()) }
    var provider by remember { mutableStateOf(PaymentProvider.PAYSTACK) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Online Payment (NGN)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${enrolment.courseTitle} • Balance: ${Formatters.formatNgn(enrolment.balanceNgn)}")
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter(Char::isDigit) },
                    label = { Text("Amount to Pay (NGN)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = provider == PaymentProvider.PAYSTACK,
                        onClick = { provider = PaymentProvider.PAYSTACK },
                        label = { Text("Paystack (Primary)") }
                    )
                    FilterChip(
                        selected = provider == PaymentProvider.FLUTTERWAVE,
                        onClick = { provider = PaymentProvider.FLUTTERWAVE },
                        label = { Text("Flutterwave") }
                    )
                }
                Text(
                    "Transactions are verified strictly against your backend server before updating enrolment balances.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            val amt = amountText.toLongOrNull() ?: 0L
            Button(onClick = { onProceed(amt, provider) }, enabled = amt > 0L) {
                Text("Initialize Checkout")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun OpayTransferDialog(
    enrolment: Enrolment,
    onDismiss: () -> Unit,
    onSubmit: (Long, String, String) -> Unit
) {
    var amountText by remember { mutableStateOf(enrolment.balanceNgn.toString()) }
    var transferRef by remember { mutableStateOf("") }
    var proofUri by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) proofUri = uri.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Submit OPay Bank Transfer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Transfer to: ${InstitutionConfig.OPAY_BANK_NAME} • ${InstitutionConfig.OPAY_ACCOUNT_NUMBER} (${InstitutionConfig.OPAY_ACCOUNT_NAME})", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter(Char::isDigit) },
                    label = { Text("Amount Transferred (NGN)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = transferRef,
                    onValueChange = { transferRef = it },
                    label = { Text("OPay / Bank Session Reference *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (proofUri.isBlank()) "Attach Transfer Receipt (Optional)" else "Receipt Attached")
                }
            }
        },
        confirmButton = {
            val amt = amountText.toLongOrNull() ?: 0L
            Button(
                onClick = { onSubmit(amt, transferRef, proofUri) },
                enabled = amt > 0L && transferRef.trim().length >= 3
            ) {
                Text("Submit for Verification")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AdminRecordPaymentDialog(
    enrolment: Enrolment,
    onDismiss: () -> Unit,
    onRecord: (Long, PaymentProvider, String) -> Unit
) {
    var amountText by remember { mutableStateOf(enrolment.balanceNgn.toString()) }
    var ref by remember { mutableStateOf(Formatters.generateReference("CASH")) }
    var provider by remember { mutableStateOf(PaymentProvider.CASH) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Verified Payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${enrolment.studentName} — ${enrolment.courseTitle}")
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter(Char::isDigit) },
                    label = { Text("Amount (NGN)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ref,
                    onValueChange = { ref = it },
                    label = { Text("Receipt / Transaction Ref") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(PaymentProvider.CASH, PaymentProvider.OPAY_TRANSFER, PaymentProvider.PAYSTACK).forEach { p ->
                        FilterChip(selected = provider == p, onClick = { provider = p }, label = { Text(p.name) })
                    }
                }
            }
        },
        confirmButton = {
            val amt = amountText.toLongOrNull() ?: 0L
            Button(onClick = { onRecord(amt, provider, ref) }, enabled = amt > 0L) {
                Text("Confirm & Credit")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
