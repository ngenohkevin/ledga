package com.ledga.app.data.room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ledga.core.derive.RuleAction
import com.ledga.core.derive.RuleField
import com.ledga.core.derive.RuleOrigin
import com.ledga.core.model.CategoryGroup
import com.ledga.core.model.FlowKind
import com.ledga.core.model.TxKind
import java.time.Instant
import java.time.LocalDate

enum class SmsSource { RECEIVER, INBOX, LEGACY, IMPORT }
enum class SmsStatus { PARSED, IGNORED, UNREADABLE }
enum class CategoryOrigin { SYSTEM, USER }

/** Append-only source of truth: every M-Pesa SMS ever seen, deduplicated by [bodyHash] (`SmsText.hash`). */
@Entity(
    tableName = "sms",
    indices = [Index(value = ["bodyHash"], unique = true), Index(value = ["code"]), Index(value = ["lineId"])],
    foreignKeys = [ForeignKey(entity = LineRow::class, parentColumns = ["id"], childColumns = ["lineId"], onDelete = ForeignKey.SET_NULL)],
)
data class SmsRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val body: String,
    val bodyHash: String,
    val receivedAt: Instant,
    val subscriptionId: Int?,
    val lineId: Long?,
    /** The M-Pesa code when [status] is PARSED, else null. */
    val code: String?,
    val source: SmsSource,
    val status: SmsStatus,
    val statusReason: String?,
    val parserVersion: Int,
)

/** Derived, fully rebuildable: one row per M-Pesa code (`DerivedTx` field for field). Refinement R7: [code] is the key. */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["occurredAt"]),
        Index(value = ["flow", "occurredAt"]),
        Index(value = ["categoryKey", "occurredAt"]),
        Index(value = ["lineId"]),
        Index(value = ["counterpartyKey"]),
        Index(value = ["reversesCode"]),
    ],
)
data class TxRow(
    @PrimaryKey val code: String,
    val lineId: Long?,
    val occurredAt: Instant,
    val occurredAtApprox: Boolean,
    val kind: TxKind,
    val flow: FlowKind,
    val amountCents: Long,
    val feeCents: Long,
    val balanceCents: Long?,
    val counterpartyName: String?,
    val counterpartyPhone: String?,
    val counterpartyAccount: String?,
    val counterpartyKey: String?,
    val destinationCountry: String?,
    val reversesCode: String?,
    val isReversed: Boolean,
    val fulizaDrawnCents: Long?,
    val fulizaFeeCents: Long?,
    val fulizaOutstandingCents: Long?,
    val fulizaLimitCents: Long?,
    val fulizaDueDate: LocalDate?,
    val categoryKey: String,
    val note: String?,
    val isHidden: Boolean,
    val searchText: String,
    val smsCount: Int,
)

/** User intent for one code; survives every rebuild. [ownAccount] is tri-state: null = follow the rules. */
@Entity(tableName = "overrides")
data class OverrideRow(
    @PrimaryKey val code: String,
    val categoryKey: String?,
    val note: String?,
    val lineId: Long?,
    val ownAccount: Boolean?,
    val hidden: Boolean,
    val updatedAt: Instant,
)

@Entity(tableName = "rules")
data class RuleRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val field: RuleField,
    val pattern: String,
    val action: RuleAction,
    val categoryKey: String?,
    val origin: RuleOrigin,
    val priority: Int,
    val createdAt: Instant,
    @ColumnInfo(defaultValue = "1") val enabled: Boolean = true,
)

/** [color]/[colorDark] null = the design token for [key] (seeded categories, refinement R9). */
@Entity(tableName = "categories")
data class CategoryRow(
    @PrimaryKey val key: String,
    val name: String,
    val groupKey: CategoryGroup,
    val icon3d: String,
    val color: String?,
    val colorDark: String?,
    val tracked: Boolean,
    val sortOrder: Int,
    val origin: CategoryOrigin,
    val archived: Boolean,
)

@Entity(tableName = "lines", indices = [Index(value = ["subscriptionId"], unique = true)])
data class LineRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subscriptionId: Int?,
    val phoneNumber: String?,
    val displayName: String,
    val color: String,
    val isPrimary: Boolean,
    val createdAt: Instant,
)

/** Notification log + dedupe (Phase 5 writes it; the table ships in schema 6 so no v7 is needed). */
@Entity(tableName = "alerts", indices = [Index(value = ["createdAt"])])
data class AlertRow(
    @PrimaryKey val key: String,
    val type: String,
    val title: String,
    val body: String,
    val targetCode: String?,
    val createdAt: Instant,
    val readAt: Instant?,
)

/** Small key-value store (refinement R8). */
@Entity(tableName = "meta")
data class MetaRow(
    @PrimaryKey val key: String,
    val value: String,
)

object MetaKeys {
    /** The parser/derivation versions the current `transactions` were built with. */
    const val PARSER_VERSION = "parserVersion"
    const val DERIVATION_VERSION = "derivationVersion"
}

/** A line's latest stated wallet balance (query result, not a table). */
data class LineBalance(val lineId: Long?, val balanceCents: Long)

/** One transaction carrying a Fuliza fact (draw, repayment, limit or outstanding). */
data class FulizaReading(
    val code: String,
    val lineId: Long?,
    val kind: TxKind,
    val occurredAt: Instant,
    val amountCents: Long,
    val fulizaOutstandingCents: Long?,
    val fulizaLimitCents: Long?,
    val fulizaDueDate: LocalDate?,
)
