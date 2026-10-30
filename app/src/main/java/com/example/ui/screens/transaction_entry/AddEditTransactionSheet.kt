package com.example.ui.screens.transaction_entry

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.AccountEntity
import com.example.data.local.entity.CategoryEntity
import com.example.data.local.entity.TransactionEntity
import com.example.ui.components.FormatUtils
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.TransferBlue
import com.example.viewmodel.FinanceViewModel
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTransactionSheet(
    viewModel: FinanceViewModel,
    existingTransaction: TransactionEntity? = null,
    initialType: String = "EXPENSE",
    categories: List<CategoryEntity>,
    accounts: List<AccountEntity>,
    currencySymbol: String,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    var selectedType by remember { mutableStateOf(existingTransaction?.type ?: initialType) }
    var amountText by remember { mutableStateOf(existingTransaction?.amount?.let { if (it % 1 == 0.0) it.toLong().toString() else it.toString() } ?: "") }
    var description by remember { mutableStateOf(existingTransaction?.description ?: "") }
    var selectedDateMillis by remember { mutableLongStateOf(existingTransaction?.dateMillis ?: System.currentTimeMillis()) }

    val filteredCategories = categories.filter { it.type == selectedType }
    var selectedCategory by remember(selectedType, categories) {
        val match = categories.find { it.id == existingTransaction?.categoryId }
            ?: filteredCategories.firstOrNull()
        mutableStateOf(match)
    }

    var selectedAccount by remember(accounts) {
        val match = accounts.find { it.id == existingTransaction?.accountId } ?: accounts.firstOrNull()
        mutableStateOf(match)
    }

    var selectedToAccount by remember(accounts) {
        val match = accounts.find { it.id == existingTransaction?.toAccountId } ?: accounts.getOrNull(1) ?: accounts.firstOrNull()
        mutableStateOf(match)
    }

    var isRecurring by remember { mutableStateOf(existingTransaction?.isRecurring ?: false) }
    var recurringFrequency by remember { mutableStateOf(existingTransaction?.recurringFrequency ?: "MONTHLY") }
    var showFrequencyDropdown by remember { mutableStateOf(false) }

    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (existingTransaction == null) "New Transaction" else "Edit Transaction",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Type Toggle Selector (Income, Expense, Transfer)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    Triple("EXPENSE", "Expense", ExpenseRed),
                    Triple("INCOME", "Income", IncomeGreen),
                    Triple("TRANSFER", "Transfer", TransferBlue)
                ).forEach { (typeKey, label, color) ->
                    val isSelected = selectedType == typeKey
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                selectedType = typeKey
                                selectedCategory = categories.firstOrNull { it.type == typeKey }
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) color else Color.Transparent
                    ) {
                        Text(
                            text = label,
                            modifier = Modifier.padding(vertical = 10.dp),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Amount Input Field with Currency Prefix
            OutlinedTextField(
                value = amountText,
                onValueChange = {
                    if (it.isEmpty() || it.matches(Regex("""^\d*\.?\d{0,2}$"""))) {
                        amountText = it
                        errorMessage = null
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("amount_input"),
                label = { Text("Amount") },
                prefix = {
                    Text(
                        text = "$currencySymbol ",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )

            // Quick amount increment chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(100, 500, 1000, 5000).forEach { chipVal ->
                    Surface(
                        modifier = Modifier
                            .clickable {
                                val current = amountText.toDoubleOrNull() ?: 0.0
                                val updated = current + chipVal
                                amountText = if (updated % 1 == 0.0) updated.toLong().toString() else updated.toString()
                            },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "+$chipVal",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Category Selection (Hidden for TRANSFER)
            if (selectedType != "TRANSFER") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Category",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                    TextButton(onClick = { showAddCategoryDialog = true }) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Custom", style = MaterialTheme.typography.labelMedium)
                    }
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredCategories) { cat ->
                        val isSelected = selectedCategory?.id == cat.id
                        val catColor = FormatUtils.parseColor(cat.colorHex)
                        Surface(
                            modifier = Modifier
                                .clickable { selectedCategory = cat }
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(catColor.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = FormatUtils.getCategoryIcon(cat.iconName),
                                        contentDescription = null,
                                        tint = catColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = cat.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // Account Selection
            Text(
                text = if (selectedType == "TRANSFER") "Source Account (From)" else "Account / Payment Method",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(accounts) { acc ->
                    val isSelected = selectedAccount?.id == acc.id
                    val accColor = FormatUtils.parseColor(acc.colorHex)
                    Surface(
                        modifier = Modifier
                            .clickable { selectedAccount = acc }
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(12.dp)
                            ),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(accColor.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = FormatUtils.getCategoryIcon(acc.iconName),
                                    contentDescription = null,
                                    tint = accColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = acc.name,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        }
                    }
                }
            }

            // Destination Account for Transfer
            if (selectedType == "TRANSFER") {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Destination Account (To)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(accounts.filter { it.id != selectedAccount?.id }) { acc ->
                        val isSelected = selectedToAccount?.id == acc.id
                        val accColor = FormatUtils.parseColor(acc.colorHex)
                        Surface(
                            modifier = Modifier
                                .clickable { selectedToAccount = acc }
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(accColor.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = FormatUtils.getCategoryIcon(acc.iconName),
                                        contentDescription = null,
                                        tint = accColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = acc.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Date Picker Row
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                val updatedCal = Calendar.getInstance()
                                updatedCal.set(year, month, day)
                                selectedDateMillis = updatedCal.timeInMillis
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Date: ${FormatUtils.formatDate(selectedDateMillis)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        text = "Change",
                        style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.primary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Description / Note
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("description_input"),
                label = { Text("Description / Note (optional)") },
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Recurring Options
            if (selectedType != "TRANSFER") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { isRecurring = !isRecurring }
                ) {
                    Checkbox(
                        checked = isRecurring,
                        onCheckedChange = { isRecurring = it }
                    )
                    Text(text = "Recurring Transaction", style = MaterialTheme.typography.bodyMedium)
                }

                if (isRecurring) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY").forEach { freq ->
                            val isSel = recurringFrequency == freq
                            Surface(
                                modifier = Modifier.clickable { recurringFrequency = freq },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = freq,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            errorMessage?.let { msg ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (existingTransaction != null) {
                    OutlinedButton(
                        onClick = {
                            viewModel.deleteTransaction(existingTransaction)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ExpenseRed),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete")
                    }
                }

                Button(
                    onClick = {
                        val amount = amountText.toDoubleOrNull()
                        if (amount == null || amount <= 0) {
                            errorMessage = "Please enter a valid amount greater than 0"
                            return@Button
                        }
                        val acc = selectedAccount
                        if (acc == null) {
                            errorMessage = "Please select an account"
                            return@Button
                        }

                        if (selectedType == "TRANSFER") {
                            val toAcc = selectedToAccount
                            if (toAcc == null || toAcc.id == acc.id) {
                                errorMessage = "Please select a different destination account"
                                return@Button
                            }
                            if (existingTransaction == null) {
                                viewModel.transferMoney(acc, toAcc, amount, description, selectedDateMillis)
                            } else {
                                viewModel.updateTransaction(
                                    existingTransaction.copy(
                                        type = "TRANSFER",
                                        amount = amount,
                                        accountId = acc.id,
                                        accountName = acc.name,
                                        toAccountId = toAcc.id,
                                        toAccountName = toAcc.name,
                                        dateMillis = selectedDateMillis,
                                        description = description
                                    )
                                )
                            }
                        } else {
                            val cat = selectedCategory ?: CategoryEntity(
                                id = 1,
                                name = if (selectedType == "INCOME") "Salary" else "Other",
                                type = selectedType,
                                iconName = "MoreHoriz",
                                colorHex = "#3B82F6"
                            )

                            if (existingTransaction == null) {
                                viewModel.addTransaction(
                                    type = selectedType,
                                    amount = amount,
                                    category = cat,
                                    account = acc,
                                    dateMillis = selectedDateMillis,
                                    description = description,
                                    isRecurring = isRecurring,
                                    recurringFrequency = if (isRecurring) recurringFrequency else null
                                )
                            } else {
                                viewModel.updateTransaction(
                                    existingTransaction.copy(
                                        type = selectedType,
                                        amount = amount,
                                        categoryId = cat.id,
                                        categoryName = cat.name,
                                        accountId = acc.id,
                                        accountName = acc.name,
                                        toAccountId = null,
                                        toAccountName = null,
                                        dateMillis = selectedDateMillis,
                                        description = description,
                                        isRecurring = isRecurring,
                                        recurringFrequency = if (isRecurring) recurringFrequency else null
                                    )
                                )
                            }
                        }
                        onDismiss()
                    },
                    modifier = Modifier.weight(2f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = if (existingTransaction == null) "Save Transaction" else "Update Transaction",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }

    // Custom Category Dialog
    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = { showAddCategoryDialog = false },
            title = { Text("New Custom Category") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text("Category Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCategoryName.isNotBlank()) {
                            viewModel.addCategory(
                                name = newCategoryName.trim(),
                                type = selectedType,
                                iconName = "MoreHoriz",
                                colorHex = "#3B82F6"
                            )
                            showAddCategoryDialog = false
                            newCategoryName = ""
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCategoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
