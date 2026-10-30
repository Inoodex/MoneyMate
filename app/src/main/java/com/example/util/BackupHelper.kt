package com.example.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupHelper {

    fun createBackupFile(context: Context, jsonContent: String): Pair<File, Uri> {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "MoneyMate_Backup_$timestamp.json"
        val file = File(context.cacheDir, fileName)

        FileOutputStream(file).use { out ->
            out.write(jsonContent.toByteArray(Charsets.UTF_8))
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Pair(file, uri)
    }

    fun sendEmailBackup(
        context: Context,
        recipientEmail: String,
        jsonContent: String,
        txCount: Int,
        accountsCount: Int
    ) {
        val (file, uri) = createBackupFile(context, jsonContent)
        val formattedDate = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())

        val bodyText = """
            Hello,

            Here is your MoneyMate financial data backup generated on $formattedDate.

            Backup Details:
            • Registered Email: $recipientEmail
            • Accounts: $accountsCount
            • Total Transactions: $txCount
            • File Name: ${file.name}

            How to use this backup:
            1. Save or keep this email in your Gmail/Google account.
            2. To restore on any device, download the attached .json file and open it in MoneyMate under Settings -> Restore from Google Drive / File.

            Stay on track with your finances!
            — MoneyMate App
        """.trimIndent()

        val emailIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            if (recipientEmail.isNotBlank()) {
                putExtra(Intent.EXTRA_EMAIL, arrayOf(recipientEmail))
            }
            putExtra(Intent.EXTRA_SUBJECT, "MoneyMate Backup - $formattedDate")
            putExtra(Intent.EXTRA_TEXT, bodyText)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("MoneyMate Backup", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(emailIntent, "Send Backup via Gmail or Email")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun shareBackupFile(context: Context, jsonContent: String) {
        val (_, uri) = createBackupFile(context, jsonContent)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "MoneyMate Data Backup")
            clipData = ClipData.newRawUri("MoneyMate Backup", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, "Share MoneyMate Backup")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun writeToUri(context: Context, uri: Uri, content: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(content.toByteArray(Charsets.UTF_8))
                output.flush()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun readFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader(Charsets.UTF_8).use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
