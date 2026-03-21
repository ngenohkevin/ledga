package com.ledga.app.data.repository

import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.ledga.app.data.db.dao.TransactionDao
import com.ledga.app.data.db.entity.TransactionEntity
import com.ledga.app.data.parser.TransactionDirection
import com.ledga.app.data.parser.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class BackupData(
    val version: Int = 1,
    val backedUpAt: Long = System.currentTimeMillis(),
    val transactions: List<BackupTransaction>
)

@Serializable
data class BackupTransaction(
    val transactionCode: String,
    val type: String,
    val amount: Double,
    val transactionCost: Double,
    val recipientName: String?,
    val recipientPhone: String?,
    val accountNumber: String?,
    val destinationCountry: String?,
    val balance: Double,
    val direction: String,
    val categoryId: Long?,
    val fulizaAmount: Double?,
    val fulizaOutstanding: Double?,
    val reversedTransactionCode: String?,
    val rawSms: String,
    val timestamp: Long
)

@Singleton
class BackupRepository @Inject constructor(
    private val transactionDao: TransactionDao
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val BACKUP_FILENAME = "ledga-backup.json"

    fun getSignInIntent(context: Context): Intent {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .build()
        return GoogleSignIn.getClient(context, gso).signInIntent
    }

    fun isSignedIn(context: Context): Boolean {
        return GoogleSignIn.getLastSignedInAccount(context) != null
    }

    fun getAccountEmail(context: Context): String? {
        return GoogleSignIn.getLastSignedInAccount(context)?.email
    }

    suspend fun performBackup(context: Context): Boolean = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return@withContext false
        val driveService = getDriveService(context, account)

        val transactions = transactionDao.getRecentTransactions(100000).first()
        val backupData = BackupData(
            transactions = transactions.map { it.transaction.toBackup() }
        )
        val content = json.encodeToString(backupData)

        // Find existing backup file
        val existingFileId = findBackupFile(driveService)

        if (existingFileId != null) {
            // Update existing
            val mediaContent = ByteArrayContent.fromString("application/json", content)
            driveService.files().update(existingFileId, null, mediaContent).execute()
        } else {
            // Create new
            val fileMetadata = com.google.api.services.drive.model.File().apply {
                name = BACKUP_FILENAME
                parents = listOf("appDataFolder")
            }
            val mediaContent = ByteArrayContent.fromString("application/json", content)
            driveService.files().create(fileMetadata, mediaContent)
                .setFields("id")
                .execute()
        }

        true
    }

    suspend fun restoreBackup(context: Context): Int = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return@withContext 0
        val driveService = getDriveService(context, account)

        val fileId = findBackupFile(driveService) ?: return@withContext 0

        val outputStream = java.io.ByteArrayOutputStream()
        driveService.files().get(fileId).executeMediaAndDownloadTo(outputStream)
        val content = outputStream.toString("UTF-8")

        val backupData = json.decodeFromString<BackupData>(content)
        var imported = 0
        backupData.transactions.forEach { bt ->
            val entity = bt.toEntity()
            val id = transactionDao.insert(entity)
            if (id != -1L) imported++
        }
        imported
    }

    suspend fun hasBackup(context: Context): Boolean = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return@withContext false
        val driveService = getDriveService(context, account)
        findBackupFile(driveService) != null
    }

    private fun getDriveService(context: Context, account: GoogleSignInAccount): Drive {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_APPDATA)
        )
        credential.selectedAccount = account.account
        return Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("Ledga")
            .build()
    }

    private fun findBackupFile(driveService: Drive): String? {
        val result = driveService.files().list()
            .setSpaces("appDataFolder")
            .setQ("name = '$BACKUP_FILENAME'")
            .setFields("files(id)")
            .execute()
        return result.files?.firstOrNull()?.id
    }

    private fun TransactionEntity.toBackup() = BackupTransaction(
        transactionCode = transactionCode, type = type.name, amount = amount,
        transactionCost = transactionCost, recipientName = recipientName,
        recipientPhone = recipientPhone, accountNumber = accountNumber,
        destinationCountry = destinationCountry, balance = balance,
        direction = direction.name, categoryId = categoryId,
        fulizaAmount = fulizaAmount, fulizaOutstanding = fulizaOutstanding,
        reversedTransactionCode = reversedTransactionCode, rawSms = rawSms,
        timestamp = timestamp
    )

    private fun BackupTransaction.toEntity() = TransactionEntity(
        transactionCode = transactionCode,
        type = try { TransactionType.valueOf(type) } catch (e: Exception) { TransactionType.UNKNOWN },
        amount = amount, transactionCost = transactionCost,
        recipientName = recipientName, recipientPhone = recipientPhone,
        accountNumber = accountNumber, destinationCountry = destinationCountry,
        balance = balance,
        direction = try { TransactionDirection.valueOf(direction) } catch (e: Exception) { TransactionDirection.OUTFLOW },
        categoryId = categoryId, fulizaAmount = fulizaAmount,
        fulizaOutstanding = fulizaOutstanding, reversedTransactionCode = reversedTransactionCode,
        rawSms = rawSms, timestamp = timestamp
    )
}
