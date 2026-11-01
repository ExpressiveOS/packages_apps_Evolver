package org.evolution.settings.fragments.miscellaneous

import android.app.KeyguardManager
import android.content.Context
import android.nfc.NfcAdapter
import android.os.SystemProperties
import android.provider.Settings
import com.android.internal.util.evolution.PixelDeviceRepository
import com.android.settings.R
import org.json.JSONObject

/**
 * Whether the ROM's side of Google Wallet's integrity check is in place: spoofing on, a keybox
 * that was checked recently and isn't revoked, Play services and the Play Store targeted, a valid
 * fingerprint, a screen lock and NFC. "Yes" means only that; Google decides the verdict, so the
 * line never promises that payments work. The first failing check is the reason shown.
 */
internal data class PaymentsReadiness(
    val tone: ExpressiveTone,
    val chip: Int,
    val reason: String,
    /** The reason is fixed on the Play Integrity Fix page rather than TrickyStore's. */
    val opensPif: Boolean = false,
)

private const val TRICKYSTORE_ENABLED_KEY = "spoof_trickystore_enabled"
private const val PIF_ENABLED_KEY = "spoof_pif_enabled"
private const val REVOCATION_CHECK_MAX_AGE_MS = 24L * 60 * 60 * 1000
private val WALLET_TARGETS = listOf("com.google.android.gms", "com.android.vending")

internal fun paymentsReadiness(context: Context): PaymentsReadiness {
    val resolver = context.contentResolver
    fun result(tone: ExpressiveTone, chip: Int, reason: Int, opensPif: Boolean = false) =
        PaymentsReadiness(tone, chip, context.getString(reason), opensPif)
    fun no(reason: Int) = result(ExpressiveTone.Negative, R.string.payments_status_no, reason)
    fun noPif(reason: Int) =
        result(ExpressiveTone.Negative, R.string.payments_status_no, reason, opensPif = true)
    fun check(reason: Int) = result(ExpressiveTone.Neutral, R.string.payments_status_check, reason)

    if (Settings.System.getInt(resolver, TRICKYSTORE_ENABLED_KEY, 1) == 0) {
        return no(R.string.payments_reason_trickystore_off)
    }
    if (Settings.System.getInt(resolver, PIF_ENABLED_KEY, 1) == 0) {
        return noPif(R.string.payments_reason_pif_off)
    }
    if (Settings.Secure.getString(resolver, TrickyStore.KEYBOX_KEY).isNullOrBlank()) {
        return no(R.string.payments_reason_no_keybox)
    }
    when (Settings.Secure.getString(resolver, TrickyStore.LAST_REVOCATION_STATUS_KEY)) {
        "REVOKED", "SUSPENDED", "CHAIN_INVALID" -> return no(R.string.payments_reason_keybox_bad)
        // TrickyStore's own page warns about this one but says it may still pass.
        "UNTRUSTED_ROOT" -> return check(R.string.payments_reason_untrusted_root)
        null, "" -> return check(R.string.payments_reason_unchecked)
    }
    val checkedAt = Settings.Secure.getLong(resolver, TrickyStore.LAST_REVOCATION_CHECK_KEY, 0L)
    if (System.currentTimeMillis() - checkedAt > REVOCATION_CHECK_MAX_AGE_MS) {
        return check(R.string.payments_reason_unchecked)
    }
    // "pkg", "pkg?" and "pkg!" are targeted; "pkg-" is skipped.
    val targets = Settings.Secure.getString(resolver, TrickyStoreAppSettings.TARGET_KEY).orEmpty()
        .lines().map { it.trim() }.filterNot { it.endsWith("-") }.map { it.trimEnd('?', '!') }
    if (!targets.containsAll(WALLET_TARGETS)) {
        return no(R.string.payments_reason_not_targeted)
    }
    val pif = try {
        JSONObject(Settings.Secure.getString(resolver, PlayIntegrityFix.PIF_CONFIG_KEY).orEmpty())
    } catch (_: Exception) {
        null
    }
    if (pif == null || !PixelDeviceRepository.isValidFingerprint(pif.optString("FINGERPRINT"))) {
        return noPif(R.string.payments_reason_no_fingerprint)
    }
    val provider = pif.optString("spoofProvider")
    if (provider == "1" || provider.equals("true", ignoreCase = true)) {
        return noPif(R.string.payments_reason_provider)
    }
    val canaryMonth = pif.optString("_canary_month")
    if (canaryMonth.isNotEmpty()) {
        val daysLeft = PixelDeviceRepository.getDaysUntilExpiry(
            canaryMonth, pif.optString("_canary_release_date").ifEmpty { null })
        if (daysLeft != null && daysLeft < 0) return noPif(R.string.payments_reason_expired)
    }
    if (context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure != true) {
        return no(R.string.payments_reason_no_lock)
    }
    if (NfcAdapter.getDefaultAdapter(context)?.isEnabled == false) {
        return result(ExpressiveTone.Warning, R.string.payments_status_check, R.string.payments_reason_nfc_off)
    }
    // Integrity modules that hide custom ROMs delete the ROM's own props at runtime.
    if (SystemProperties.get("ro.modversion").isEmpty()) {
        return result(ExpressiveTone.Warning, R.string.payments_status_check, R.string.payments_reason_props_hidden)
    }
    return result(ExpressiveTone.Positive, R.string.payments_status_yes, R.string.payments_reason_ready)
}
