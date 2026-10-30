package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "moneymate_prefs")

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

data class CurrencyOption(
    val code: String,
    val symbol: String,
    val name: String
)

val AvailableCurrencies = listOf(
    CurrencyOption("BDT", "৳", "Bangladeshi Taka"),
    CurrencyOption("USD", "$", "US Dollar"),
    CurrencyOption("EUR", "€", "Euro"),
    CurrencyOption("GBP", "£", "British Pound"),
    CurrencyOption("INR", "₹", "Indian Rupee"),
    CurrencyOption("JPY", "¥", "Japanese Yen"),
    CurrencyOption("CAD", "C$", "Canadian Dollar"),
    CurrencyOption("AUD", "A$", "Australian Dollar"),
    CurrencyOption("SAR", "﷼", "Saudi Riyal"),
    CurrencyOption("AED", "د.إ", "UAE Dirham")
)

data class UserSettings(
    val currencyCode: String = "BDT",
    val currencySymbol: String = "৳",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val isPinLockEnabled: Boolean = false,
    val pinCode: String = "",
    val isBiometricEnabled: Boolean = false,
    val dailyReminderEnabled: Boolean = true,
    val budgetAlertsEnabled: Boolean = true,
    val userName: String = "Mahmud Hasan",
    val userEmail: String = "mahmud@example.com"
)

class UserPreferencesRepository(private val context: Context) {
    private object PreferencesKeys {
        val CURRENCY_CODE = stringPreferencesKey("currency_code")
        val CURRENCY_SYMBOL = stringPreferencesKey("currency_symbol")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val IS_PIN_LOCK_ENABLED = booleanPreferencesKey("is_pin_lock_enabled")
        val PIN_CODE = stringPreferencesKey("pin_code")
        val IS_BIOMETRIC_ENABLED = booleanPreferencesKey("is_biometric_enabled")
        val DAILY_REMINDER_ENABLED = booleanPreferencesKey("daily_reminder_enabled")
        val BUDGET_ALERTS_ENABLED = booleanPreferencesKey("budget_alerts_enabled")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_EMAIL = stringPreferencesKey("user_email")
    }

    val userSettingsFlow: Flow<UserSettings> = context.dataStore.data.map { preferences ->
        val currencyCode = preferences[PreferencesKeys.CURRENCY_CODE] ?: "BDT"
        val currencySymbol = preferences[PreferencesKeys.CURRENCY_SYMBOL] ?: "৳"
        val themeModeStr = preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val themeMode = runCatching { ThemeMode.valueOf(themeModeStr) }.getOrDefault(ThemeMode.SYSTEM)
        val isPinLockEnabled = preferences[PreferencesKeys.IS_PIN_LOCK_ENABLED] ?: false
        val pinCode = preferences[PreferencesKeys.PIN_CODE] ?: ""
        val isBiometricEnabled = preferences[PreferencesKeys.IS_BIOMETRIC_ENABLED] ?: false
        val dailyReminderEnabled = preferences[PreferencesKeys.DAILY_REMINDER_ENABLED] ?: true
        val budgetAlertsEnabled = preferences[PreferencesKeys.BUDGET_ALERTS_ENABLED] ?: true
        val userName = preferences[PreferencesKeys.USER_NAME] ?: "Mahmud Hasan"
        val userEmail = preferences[PreferencesKeys.USER_EMAIL] ?: "mahmud@example.com"

        UserSettings(
            currencyCode = currencyCode,
            currencySymbol = currencySymbol,
            themeMode = themeMode,
            isPinLockEnabled = isPinLockEnabled,
            pinCode = pinCode,
            isBiometricEnabled = isBiometricEnabled,
            dailyReminderEnabled = dailyReminderEnabled,
            budgetAlertsEnabled = budgetAlertsEnabled,
            userName = userName,
            userEmail = userEmail
        )
    }

    suspend fun updateCurrency(code: String, symbol: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CURRENCY_CODE] = code
            preferences[PreferencesKeys.CURRENCY_SYMBOL] = symbol
        }
    }

    suspend fun updateThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.name
        }
    }

    suspend fun setPinCode(pin: String, enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PIN_CODE] = pin
            preferences[PreferencesKeys.IS_PIN_LOCK_ENABLED] = enabled
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_BIOMETRIC_ENABLED] = enabled
        }
    }

    suspend fun setDailyReminder(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DAILY_REMINDER_ENABLED] = enabled
        }
    }

    suspend fun setBudgetAlerts(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BUDGET_ALERTS_ENABLED] = enabled
        }
    }

    suspend fun updateProfile(name: String, email: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.USER_NAME] = name
            preferences[PreferencesKeys.USER_EMAIL] = email
        }
    }
}
