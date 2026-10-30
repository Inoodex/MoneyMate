package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // "INCOME", "EXPENSE", "TRANSFER"
    val amount: Double,
    val categoryId: Long,
    val categoryName: String,
    val accountId: Long,
    val accountName: String,
    val toAccountId: Long? = null,
    val toAccountName: String? = null,
    val dateMillis: Long,
    val description: String,
    val isRecurring: Boolean = false,
    val recurringFrequency: String? = null, // "DAILY", "WEEKLY", "MONTHLY", "YEARLY"
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String, // "INCOME", "EXPENSE"
    val iconName: String,
    val colorHex: String,
    val isDefault: Boolean = true
)

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String, // "CASH", "BANK", "MOBILE_BANKING", "SAVINGS", "CREDIT_CARD", "CUSTOM"
    val initialBalance: Double = 0.0,
    val accountNumber: String? = null,
    val colorHex: String,
    val iconName: String
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val categoryName: String,
    val monthlyLimit: Double,
    val monthYear: String // format "yyyy-MM" e.g. "2026-10"
)

@Entity(tableName = "savings_goals")
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val targetAmount: Double,
    val currentSaved: Double = 0.0,
    val targetDateMillis: Long,
    val colorHex: String,
    val iconName: String,
    val notes: String = ""
)
