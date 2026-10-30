package com.example.ui.screens.reports

import android.content.Intent
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.CustomBarChart
import com.example.ui.components.CustomDonutChart
import com.example.ui.components.FormatUtils
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.viewmodel.BarChartItem
import com.example.viewmodel.CategorySpend
import com.example.viewmodel.FinanceViewModel
import com.example.viewmodel.ReportPeriod
import java.util.Calendar

@Composable
fun ReportsScreen(
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val transactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val categories by viewModel.allCategories.collectAsStateWithLifecycle()
    val userSettings by viewModel.userSettings.collectAsStateWithLifecycle()
    val selectedPeriod by viewModel.reportPeriod.collectAsStateWithLifecycle()

    // Calculate aggregated metrics based on selected period
    val now = System.currentTimeMillis()
    val calendar = Calendar.getInstance()

    val (startTime, periodLabel) = remember(selectedPeriod) {
        val c = Calendar.getInstance()
        when (selectedPeriod) {
            ReportPeriod.DAILY -> {
                c.add(Calendar.DAY_OF_YEAR, -7)
                c.timeInMillis to "Past 7 Days"
            }
            ReportPeriod.WEEKLY -> {
                c.add(Calendar.WEEK_OF_YEAR, -4)
                c.timeInMillis to "Past 4 Weeks"
            }
            ReportPeriod.MONTHLY -> {
                c.set(Calendar.DAY_OF_MONTH, 1)
                c.set(Calendar.HOUR_OF_DAY, 0)
                c.timeInMillis to "This Month"
            }
            ReportPeriod.YEARLY -> {
                c.set(Calendar.DAY_OF_YEAR, 1)
                c.set(Calendar.HOUR_OF_DAY, 0)
                c.timeInMillis to "This Year"
            }
        }
    }

    val periodTransactions = transactions.filter { it.dateMillis >= startTime }
    val totalIncome = periodTransactions.filter { it.type == "INCOME" }.sumOf { it.amount }
    val totalExpense = periodTransactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    val netSavings = totalIncome - totalExpense
    val savingsRate = if (totalIncome > 0) ((netSavings / totalIncome) * 100).coerceIn(0.0, 100.0) else 0.0

    // Category breakdown
    val categorySpends = remember(periodTransactions, categories, totalExpense) {
        val catMap = mutableMapOf<String, Double>()
        val catCount = mutableMapOf<String, Int>()
        periodTransactions.filter { it.type == "EXPENSE" }.forEach { tx ->
            catMap[tx.categoryName] = (catMap[tx.categoryName] ?: 0.0) + tx.amount
            catCount[tx.categoryName] = (catCount[tx.categoryName] ?: 0) + 1
        }
        val colors = categories.associate { it.name to it.colorHex }

        catMap.map { (name, amount) ->
            val percentage = if (totalExpense > 0) ((amount / totalExpense) * 100).toFloat() else 0f
            CategorySpend(
                categoryName = name,
                totalAmount = amount,
                percentage = percentage,
                colorHex = colors[name] ?: "#3B82F6",
                count = catCount[name] ?: 1
            )
        }.sortedByDescending { it.totalAmount }
    }

    // Chart items for periodic comparison
    val chartItems = remember(transactions, selectedPeriod) {
        val list = mutableListOf<BarChartItem>()
        when (selectedPeriod) {
            ReportPeriod.DAILY -> {
                for (i in 6 downTo 0) {
                    val c = Calendar.getInstance()
                    c.add(Calendar.DAY_OF_YEAR, -i)
                    c.set(Calendar.HOUR_OF_DAY, 0)
                    c.set(Calendar.MINUTE, 0)
                    c.set(Calendar.SECOND, 0)
                    val s = c.timeInMillis
                    c.add(Calendar.DAY_OF_YEAR, 1)
                    val e = c.timeInMillis
                    val label = java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault()).format(java.util.Date(s))
                    val inc = transactions.filter { it.type == "INCOME" && it.dateMillis in s until e }.sumOf { it.amount }
                    val exp = transactions.filter { it.type == "EXPENSE" && it.dateMillis in s until e }.sumOf { it.amount }
                    list.add(BarChartItem(label, inc, exp))
                }
            }
            ReportPeriod.WEEKLY -> {
                for (i in 3 downTo 0) {
                    val c = Calendar.getInstance()
                    c.add(Calendar.WEEK_OF_YEAR, -i)
                    val s = c.timeInMillis - 86400000L * 7
                    val e = c.timeInMillis
                    val label = "Wk ${4 - i}"
                    val inc = transactions.filter { it.type == "INCOME" && it.dateMillis in s until e }.sumOf { it.amount }
                    val exp = transactions.filter { it.type == "EXPENSE" && it.dateMillis in s until e }.sumOf { it.amount }
                    list.add(BarChartItem(label, inc, exp))
                }
            }
            ReportPeriod.MONTHLY -> {
                for (i in 5 downTo 0) {
                    val c = Calendar.getInstance()
                    c.add(Calendar.MONTH, -i)
                    c.set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), 1, 0, 0, 0)
                    val s = c.timeInMillis
                    c.add(Calendar.MONTH, 1)
                    val e = c.timeInMillis
                    val label = java.text.SimpleDateFormat("MMM", java.util.Locale.getDefault()).format(java.util.Date(s))
                    val inc = transactions.filter { it.type == "INCOME" && it.dateMillis in s until e }.sumOf { it.amount }
                    val exp = transactions.filter { it.type == "EXPENSE" && it.dateMillis in s until e }.sumOf { it.amount }
                    list.add(BarChartItem(label, inc, exp))
                }
            }
            ReportPeriod.YEARLY -> {
                for (i in 2 downTo 0) {
                    val c = Calendar.getInstance()
                    c.add(Calendar.YEAR, -i)
                    c.set(c.get(Calendar.YEAR), 0, 1, 0, 0, 0)
                    val s = c.timeInMillis
                    c.add(Calendar.YEAR, 1)
                    val e = c.timeInMillis
                    val label = c.get(Calendar.YEAR).toString()
                    val inc = transactions.filter { it.type == "INCOME" && it.dateMillis in s until e }.sumOf { it.amount }
                    val exp = transactions.filter { it.type == "EXPENSE" && it.dateMillis in s until e }.sumOf { it.amount }
                    list.add(BarChartItem(label, inc, exp))
                }
            }
        }
        list
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Financial Reports",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )

                // Share Button
                IconButton(
                    onClick = {
                        val reportText = """
                            MoneyMate Financial Summary ($periodLabel)
                            ------------------------------------
                            Total Income: ${FormatUtils.formatMoney(totalIncome, userSettings.currencySymbol)}
                            Total Expenses: ${FormatUtils.formatMoney(totalExpense, userSettings.currencySymbol)}
                            Net Savings: ${FormatUtils.formatMoney(netSavings, userSettings.currencySymbol)}
                            Savings Rate: ${String.format("%.1f", savingsRate)}%
                            
                            Top Spending Categories:
                            ${categorySpends.take(5).joinToString("\n") { "- ${it.categoryName}: ${FormatUtils.formatMoney(it.totalAmount, userSettings.currencySymbol)} (${String.format("%.1f", it.percentage)}%)" }}
                        """.trimIndent()

                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, reportText)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Financial Report"))
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share Report",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Period Selector Tabs
        item {
            TabRow(
                selectedTabIndex = selectedPeriod.ordinal,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                ReportPeriod.values().forEach { period ->
                    Tab(
                        selected = selectedPeriod == period,
                        onClick = { viewModel.reportPeriod.value = period },
                        text = {
                            Text(
                                text = period.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    )
                }
            }
        }

        // Summary Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "$periodLabel Overview",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "Total Income", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = FormatUtils.formatMoney(totalIncome, userSettings.currencySymbol),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = IncomeGreen)
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "Total Expenses", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = FormatUtils.formatMoney(totalExpense, userSettings.currencySymbol),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = ExpenseRed)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "Net Savings", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = FormatUtils.formatMoney(netSavings, userSettings.currencySymbol),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (netSavings >= 0) MaterialTheme.colorScheme.primary else ExpenseRed
                                )
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "Savings Rate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${String.format("%.1f", savingsRate)}%",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            )
                        }
                    }
                }
            }
        }

        // Periodic Comparison Bar Chart
        item {
            CustomBarChart(items = chartItems, currencySymbol = userSettings.currencySymbol)
        }

        // Category Breakdown Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Category Distribution",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    CustomDonutChart(
                        spends = categorySpends,
                        currencySymbol = userSettings.currencySymbol
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Ranked Category List
                    categorySpends.forEach { spend ->
                        val catColor = FormatUtils.parseColor(spend.colorHex)
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier.size(10.dp).clip(CircleShape).background(catColor)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = spend.categoryName,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                }
                                Text(
                                    text = FormatUtils.formatMoney(spend.totalAmount, userSettings.currencySymbol),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { spend.percentage / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = catColor,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Export Actions
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Data Export",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Export your full transaction history to standard CSV spreadsheet format.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val csvData = viewModel.getCsvContent()
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, csvData)
                                putExtra(Intent.EXTRA_SUBJECT, "MoneyMate_Transactions_Export.csv")
                                type = "text/csv"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Export Transactions CSV"))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_csv_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Export to CSV")
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(90.dp))
        }
    }
}
