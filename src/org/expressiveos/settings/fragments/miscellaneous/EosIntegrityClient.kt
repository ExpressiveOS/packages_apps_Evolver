/*
 * SPDX-FileCopyrightText: 2026 The ExpressiveOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.expressiveos.settings.fragments.miscellaneous

import android.os.IBinder
import android.os.Parcel
import android.os.ServiceManager
import android.util.Log
import org.json.JSONObject

/**
 * Binder client for ExpressiveOS 2.0's privileged `eos_integrity` system service
 * (`android.security.integrity.IEosIntegrityManager`).
 *
 * Keeps raw keybox private keys confined inside system_server's 0600 storage
 * (`/data/system/eos_integrity/keybox.xml`) instead of `Settings.Secure`.
 * Exposes metadata-only queries (`getKeyboxSummary`) and write/refresh RPCs.
 *
 * When the `eos_integrity` service is not registered on the device, every call
 * returns `null` / `false` so callers fall back cleanly to the legacy Settings path.
 */
internal object EosIntegrityClient {
    private const val TAG = "EosIntegrityClient"
    private const val SERVICE_NAME = "eos_integrity"
    private const val DESCRIPTOR = "android.security.integrity.IEosIntegrityManager"

    private const val TRANSACTION_GET_KEYBOX_SUMMARY = IBinder.FIRST_CALL_TRANSACTION + 0
    private const val TRANSACTION_IMPORT_USER_KEYBOX = IBinder.FIRST_CALL_TRANSACTION + 1
    private const val TRANSACTION_FETCH_OFFICIAL_KEYBOX = IBinder.FIRST_CALL_TRANSACTION + 2
    private const val TRANSACTION_CHECK_KEYBOX_REVOCATION = IBinder.FIRST_CALL_TRANSACTION + 3
    private const val TRANSACTION_DELETE_KEYBOX = IBinder.FIRST_CALL_TRANSACTION + 4
    private const val TRANSACTION_IMPORT_USER_TARGETS = IBinder.FIRST_CALL_TRANSACTION + 5
    private const val TRANSACTION_REFRESH_CANARY_PIF = IBinder.FIRST_CALL_TRANSACTION + 6
    private const val TRANSACTION_RESET_DEFAULTS = IBinder.FIRST_CALL_TRANSACTION + 7

    /** Metadata-only summary of the active keybox; never contains private key material. */
    data class KeyboxSummary(
        val present: Boolean,
        val source: String,
        val revocationStatus: String,
        val revocationReason: String,
        val lastFetchedMs: Long,
        val lastCheckedMs: Long,
        val leafExpiryMs: Long,
    )

    /** Result of a mutating/network integrity RPC (`ok == true` on success). */
    data class ActionResult(
        val ok: Boolean,
        val code: String,
        val message: String,
    )

    private fun getBinder(): IBinder? = try {
        ServiceManager.checkService(SERVICE_NAME)
    } catch (e: Exception) {
        Log.w(TAG, "Unable to query $SERVICE_NAME service", e)
        null
    }

    fun isAvailable(): Boolean = getBinder() != null

    /**
     * Queries non-secret metadata for the installed keybox.
     * Wire response: JSON string with keys `present`, `source`, `revocation_status`,
     * `revocation_reason`, `last_fetched_ms`, `last_checked_ms`, `leaf_expiry_ms`.
     */
    fun getKeyboxSummary(): KeyboxSummary? {
        val raw = transactString(TRANSACTION_GET_KEYBOX_SUMMARY) ?: return null
        return try {
            val json = JSONObject(raw)
            KeyboxSummary(
                present = json.optBoolean("present", false),
                source = json.optString("source", ""),
                revocationStatus = json.optString("revocation_status", ""),
                revocationReason = json.optString("revocation_reason", ""),
                lastFetchedMs = json.optLong("last_fetched_ms", 0L),
                lastCheckedMs = json.optLong("last_checked_ms", 0L),
                leafExpiryMs = json.optLong("leaf_expiry_ms", 0L),
            )
        } catch (e: Exception) {
            Log.w(TAG, "Malformed keybox summary payload", e)
            null
        }
    }

    fun importUserKeybox(rawXml: String): ActionResult? =
        transactAction(TRANSACTION_IMPORT_USER_KEYBOX) { data ->
            data.writeString(rawXml)
        }

    fun fetchOfficialKeybox(force: Boolean): ActionResult? =
        transactAction(TRANSACTION_FETCH_OFFICIAL_KEYBOX) { data ->
            data.writeInt(if (force) 1 else 0)
        }

    fun checkKeyboxRevocation(): KeyboxSummary? {
        val raw = transactString(TRANSACTION_CHECK_KEYBOX_REVOCATION) ?: return null
        return try {
            val json = JSONObject(raw)
            KeyboxSummary(
                present = json.optBoolean("present", false),
                source = json.optString("source", ""),
                revocationStatus = json.optString("revocation_status", ""),
                revocationReason = json.optString("revocation_reason", ""),
                lastFetchedMs = json.optLong("last_fetched_ms", 0L),
                lastCheckedMs = json.optLong("last_checked_ms", 0L),
                leafExpiryMs = json.optLong("leaf_expiry_ms", 0L),
            )
        } catch (e: Exception) {
            Log.w(TAG, "Malformed revocation summary payload", e)
            null
        }
    }

    fun deleteKeybox(): ActionResult? =
        transactAction(TRANSACTION_DELETE_KEYBOX) {}

    fun importUserTargets(rawText: String): ActionResult? =
        transactAction(TRANSACTION_IMPORT_USER_TARGETS) { data ->
            data.writeString(rawText)
        }

    fun refreshCanaryPif(force: Boolean): ActionResult? =
        transactAction(TRANSACTION_REFRESH_CANARY_PIF) { data ->
            data.writeInt(if (force) 1 else 0)
        }

    fun resetDefaults(): ActionResult? =
        transactAction(TRANSACTION_RESET_DEFAULTS) {}

    private fun transactString(
        code: Int,
        writeArgs: (Parcel) -> Unit = {},
    ): String? {
        val binder = getBinder() ?: return null
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(DESCRIPTOR)
            writeArgs(data)
            if (!binder.transact(code, data, reply, 0)) return null
            reply.readException()
            reply.readString()
        } catch (e: Exception) {
            Log.w(TAG, "Binder call $code failed", e)
            null
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun transactAction(
        code: Int,
        writeArgs: (Parcel) -> Unit,
    ): ActionResult? {
        val raw = transactString(code, writeArgs) ?: return null
        return try {
            val json = JSONObject(raw)
            ActionResult(
                ok = json.optBoolean("ok", false),
                code = json.optString("code", ""),
                message = json.optString("message", ""),
            )
        } catch (e: Exception) {
            Log.w(TAG, "Malformed action payload for code $code", e)
            null
        }
    }
}
