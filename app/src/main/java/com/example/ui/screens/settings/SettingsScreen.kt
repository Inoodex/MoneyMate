package com.example.ui.screens.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.example.viewmodel.SyncStatus
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.entity.AccountEntity
import com.example.data.preferences.AvailableCurrencies
import com.example.data.preferences.ThemeMode
import com.example.data.repository.RestoreResult
import com.example.ui.components.FormatUtils
import com.example.ui.theme.ExpenseRed
import com.example.util.BackupHelper
import com.example.viewmodel.FinanceViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val userSettings by viewModel.userSettings.collectAsStateWithLifecycle()
    val allAccounts by viewModel.allAccounts.collectAsStateWithLifecycle()
    val allTransactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()

    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showAddAccountDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }

    // Backup & Restore states
    var pendingRestoreContent by remember { mutableStateOf<String?>(null) }
    var restoreResult by remember { mutableStateOf<RestoreResult?>(null) }

    // Storage Access Framework Launcher for Save Document (Google Drive / Files)
    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val json = viewModel.getJsonBackup()
            val success = BackupHelper.writeToUri(context, uri, json)
            if (success) {
                Toast.makeText(context, "Backup saved to Google Drive / Files successfully!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Failed to save backup file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Storage Access Framework Launcher for Open Document (Restore from Google Drive / Files)
    val openFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val jsonContent = BackupHelper.readFromUri(context, uri)
            if (!jsonContent.isNullOrBlank()) {
                pendingRestoreContent = jsonContent
            } else {
                Toast.makeText(context, "Unable to read backup file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Profile & Cloud Account Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = userSettings.userName.trim().take(1).ifBlank { "U" }.uppercase(),
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = userSettings.userName,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = userSettings.userEmail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (currentUser != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CloudDone,
                                            contentDescription = null,
                                            tint = com.example.ui.theme.IncomeGreen,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Cloud Account Active",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = com.example.ui.theme.IncomeGreen
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Offline Guest Mode",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        IconButton(onClick = { showProfileDialog = true }) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit Profile")
                        }
                    }

                    if (currentUser != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(10.dp))

                        // Auto-Sync Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Cloud Auto-Sync",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Text(
                                    text = "Automatically back up changes to cloud",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = userSettings.isAutoSyncEnabled,
                                onCheckedChange = { viewModel.setAutoSyncEnabled(it) }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Cloud Actions: Sync Now, Restore, Sign Out
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.syncToCloudNow { success, msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Sync Now", fontSize = 13.sp)
                            }

                            androidx.compose.material3.OutlinedButton(
                                onClick = {
                                    viewModel.restoreFromCloudNow { success, msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Restore", fontSize = 13.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(
                            onClick = { showSignOutDialog = true },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                tint = ExpenseRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sign Out of Account", color = ExpenseRed, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                viewModel.signOut(context)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Sign In or Create Account")
                        }
                    }
                }
            }
        }

        // Preferences Section (Currency, Theme)
        item {
            Text(
                text = "Preferences",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    SettingsRow(
                        icon = Icons.Default.CurrencyExchange,
                        title = "Currency",
                        subtitle = "${userSettings.currencyCode} (${userSettings.currencySymbol})",
                        onClick = { showCurrencyDialog = true }
                    )

                    SettingsRow(
                        icon = Icons.Default.Brightness4,
                        title = "Theme",
                        subtitle = when (userSettings.themeMode) {
                            ThemeMode.SYSTEM -> "System Default"
                            ThemeMode.LIGHT -> "Light Mode"
                            ThemeMode.DARK -> "Dark Mode"
                        },
                        onClick = { showThemeDialog = true }
                    )
                }
            }
        }

        // Security & Notifications Section
        item {
            Text(
                text = "Security & Notifications",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    // PIN Lock Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showPinDialog = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(text = "App PIN Lock", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                                Text(
                                    text = if (userSettings.isPinLockEnabled) "Enabled • Tap to change" else "Disabled",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = userSettings.isPinLockEnabled,
                            onCheckedChange = { showPinDialog = true }
                        )
                    }

                    // Biometric Unlock Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(text = "Biometric Quick Unlock", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                                Text(text = "Use device fingerprint/face", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = userSettings.isBiometricEnabled,
                            onCheckedChange = { viewModel.setBiometric(it) }
                        )
                    }

                    // Daily Reminder Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(text = "Daily Expense Reminder", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                                Text(text = "Evening reminder to log expenses", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = userSettings.dailyReminderEnabled,
                            onCheckedChange = { viewModel.setDailyReminder(it) }
                        )
                    }
                }
            }
        }

        // Accounts Management Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Payment Accounts",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )

                TextButton(onClick = { showAddAccountDialog = true }) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Add Account")
                }
            }
        }

        items(allAccounts) { acc ->
            val color = FormatUtils.parseColor(acc.colorHex)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(color.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = FormatUtils.getCategoryIcon(acc.iconName),
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = acc.name, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                            Text(
                                text = "${acc.type} • Opening: ${FormatUtils.formatMoney(acc.initialBalance, userSettings.currencySymbol)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            if (allAccounts.size > 1) {
                                viewModel.deleteAccount(acc)
                            } else {
                                Toast.makeText(context, "At least one account required", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.Default.DeleteForever, contentDescription = "Delete Account", tint = ExpenseRed.copy(alpha = 0.6f))
                    }
                }
            }
        }

        // Cloud & Email Backup Section
        item {
            Text(
                text = "Cloud & Email Backup",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    // Send to Gmail / Email
                    SettingsRow(
                        icon = Icons.Default.Email,
                        title = "Backup to Gmail / Email",
                        subtitle = "Send data file to ${userSettings.userEmail.ifBlank { "your email" }}",
                        onClick = {
                            val json = viewModel.getJsonBackup()
                            BackupHelper.sendEmailBackup(
                                context = context,
                                recipientEmail = userSettings.userEmail,
                                jsonContent = json,
                                txCount = allTransactions.size,
                                accountsCount = allAccounts.size
                            )
                        }
                    )

                    // Save to Google Drive / Files
                    SettingsRow(
                        icon = Icons.Default.CloudUpload,
                        title = "Save to Google Drive / Files",
                        subtitle = "Choose Google Drive or device folder",
                        onClick = {
                            val timeStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                            saveFileLauncher.launch("MoneyMate_Backup_$timeStr.json")
                        }
                    )

                    // Restore from Google Drive / Files
                    SettingsRow(
                        icon = Icons.Default.CloudDownload,
                        title = "Restore from Google Drive / File",
                        subtitle = "Select a .json backup file to import",
                        onClick = {
                            openFileLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                        }
                    )

                    // Share Backup via other apps
                    SettingsRow(
                        icon = Icons.Default.Share,
                        title = "Share Backup File",
                        subtitle = "Send via WhatsApp, Telegram, Drive, etc.",
                        onClick = {
                            val json = viewModel.getJsonBackup()
                            BackupHelper.shareBackupFile(context, json)
                        }
                    )

                    // Reset All Data
                    SettingsRow(
                        icon = Icons.Default.DeleteForever,
                        title = "Reset All Data",
                        subtitle = "Restore initial database state",
                        titleColor = ExpenseRed,
                        onClick = { showResetDialog = true }
                    )
                }
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "MoneyMate v1.0 • Offline & Secure",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    // Currency Picker Dialog
    if (showCurrencyDialog) {
        AlertDialog(
            onDismissRequest = { showCurrencyDialog = false },
            title = { Text("Select Currency") },
            text = {
                LazyColumn(modifier = Modifier.height(280.dp)) {
                    items(AvailableCurrencies) { curr ->
                        val isSelected = userSettings.currencyCode == curr.code
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateCurrency(curr.code, curr.symbol)
                                    showCurrencyDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.updateCurrency(curr.code, curr.symbol)
                                    showCurrencyDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${curr.name} (${curr.symbol})",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showCurrencyDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Theme Picker Dialog
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Select App Theme") },
            text = {
                Column {
                    ThemeMode.values().forEach { mode ->
                        val isSelected = userSettings.themeMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.updateThemeMode(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.updateThemeMode(mode)
                                    showThemeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (mode) {
                                    ThemeMode.SYSTEM -> "System Default"
                                    ThemeMode.LIGHT -> "Light Mode"
                                    ThemeMode.DARK -> "Dark Mode"
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // PIN Setup Dialog
    if (showPinDialog) {
        var pinInput by remember { mutableStateOf("") }
        var isEnabling by remember { mutableStateOf(!userSettings.isPinLockEnabled) }

        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text(if (userSettings.isPinLockEnabled) "Change or Disable PIN" else "Set Security PIN") },
            text = {
                Column {
                    Text(
                        text = "Enter a 4-digit numeric PIN to secure MoneyMate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 4 && it.all { char -> char.isDigit() }) pinInput = it },
                        label = { Text("4-digit PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinInput.length == 4) {
                            viewModel.setPin(pinInput, true)
                            showPinDialog = false
                            Toast.makeText(context, "PIN code saved", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Please enter exactly 4 digits", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Save PIN")
                }
            },
            dismissButton = {
                if (userSettings.isPinLockEnabled) {
                    TextButton(
                        onClick = {
                            viewModel.setPin("", false)
                            showPinDialog = false
                            Toast.makeText(context, "PIN lock disabled", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Disable PIN", color = ExpenseRed)
                    }
                } else {
                    TextButton(onClick = { showPinDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    // Profile Dialog
    if (showProfileDialog) {
        var nameInput by remember { mutableStateOf(userSettings.userName) }
        var emailInput by remember { mutableStateOf(userSettings.userEmail) }

        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = { Text("Edit Profile") },
            text = {
                Column {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Your Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Email Address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (nameInput.isNotBlank()) {
                            viewModel.updateProfile(nameInput.trim(), emailInput.trim())
                            showProfileDialog = false
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showProfileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Add Account Dialog
    if (showAddAccountDialog) {
        var accName by remember { mutableStateOf("") }
        var accBalanceText by remember { mutableStateOf("") }
        var accNumber by remember { mutableStateOf("") }
        var accType by remember { mutableStateOf("BANK") }

        AlertDialog(
            onDismissRequest = { showAddAccountDialog = false },
            title = { Text("Add New Account") },
            text = {
                Column {
                    OutlinedTextField(
                        value = accName,
                        onValueChange = { accName = it },
                        label = { Text("Account Name (e.g. Standard Chartered)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = accBalanceText,
                        onValueChange = { accBalanceText = it },
                        label = { Text("Opening Balance (${userSettings.currencySymbol})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = accNumber,
                        onValueChange = { accNumber = it },
                        label = { Text("Account / Card Number (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val bal = accBalanceText.toDoubleOrNull() ?: 0.0
                        if (accName.isNotBlank()) {
                            viewModel.addAccount(
                                name = accName.trim(),
                                type = accType,
                                initialBalance = bal,
                                accountNumber = accNumber,
                                colorHex = "#1E6FD9",
                                iconName = "AccountBalance"
                            )
                            showAddAccountDialog = false
                        }
                    }
                ) {
                    Text("Create Account")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddAccountDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Reset Confirmation Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset All Data?") },
            text = {
                Text("This will restore default categories, sample starter transactions, and reset accounts. This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetData()
                        showResetDialog = false
                        Toast.makeText(context, "Data reset successfully", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                ) {
                    Text("Confirm Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Pending Restore Confirmation Dialog
    pendingRestoreContent?.let { content ->
        AlertDialog(
            onDismissRequest = { pendingRestoreContent = null },
            title = { Text("Confirm Data Restore") },
            text = {
                Text("This will restore transactions, accounts, categories, budgets, and savings goals from your selected backup file. Existing records will be cleanly replaced with the backup.\n\nDo you want to proceed?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val result = viewModel.restoreFromJson(content, clearExisting = true)
                            restoreResult = result
                            pendingRestoreContent = null
                        }
                    }
                ) {
                    Text("Proceed & Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreContent = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Restore Result Dialog
    restoreResult?.let { result ->
        AlertDialog(
            onDismissRequest = { restoreResult = null },
            title = {
                Text(if (result.success) "Restore Successful!" else "Restore Failed")
            },
            text = {
                if (result.success) {
                    Text(
                        "Your MoneyMate data was restored successfully:\n\n" +
                        "• Transactions: ${result.transactionsCount}\n" +
                        "• Accounts: ${result.accountsCount}\n" +
                        "• Categories: ${result.categoriesCount}\n" +
                        "• Budgets: ${result.budgetsCount}\n" +
                        "• Savings Goals: ${result.goalsCount}"
                    )
                } else {
                    Text("Could not restore data from the file: ${result.errorMessage}")
                }
            },
            confirmButton = {
                Button(onClick = { restoreResult = null }) {
                    Text("OK")
                }
            }
        )
    }

    // Sign Out Confirmation Dialog
    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign Out of MoneyMate?") },
            text = {
                Text("You will return to the Login / Sign Up screen. Your data stored on this device remains safe.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutDialog = false
                        viewModel.signOut(context) {
                            Toast.makeText(context, "Signed out successfully", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                ) {
                    Text("Sign Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = titleColor)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
