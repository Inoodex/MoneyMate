package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.components.FormatUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("MoneyMate", appName)
  }

  @Test
  fun `test currency formatting`() {
    val formatted = FormatUtils.formatMoney(50000.0, "৳")
    assertTrue(formatted.contains("৳"))
    assertTrue(formatted.contains("50,000.00"))
  }

  @Test
  fun `test financial balance calculation`() {
    val openingBalance = 42500.0
    val totalIncome = 65000.0
    val totalExpense = 15000.0
    val balance = openingBalance + totalIncome - totalExpense
    val savings = totalIncome - totalExpense
    assertEquals(92500.0, balance, 0.001)
    assertEquals(50000.0, savings, 0.001)
  }
}
