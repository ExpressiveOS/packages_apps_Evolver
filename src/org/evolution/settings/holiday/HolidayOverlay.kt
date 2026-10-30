/*
 * SPDX-FileCopyrightText: ExpressiveOS
 * SPDX-License-Identifier: Apache-2.0
 */

package org.evolution.settings.holiday

import android.content.Context
import android.content.om.FabricatedOverlay
import android.content.om.OverlayIdentifier
import android.content.om.OverlayManager
import android.content.om.OverlayManagerTransaction
import android.util.Log
import android.util.TypedValue

/**
 * Settings' homepage icons in a holiday's colours: a fabricated overlay on Settings itself that
 * replaces the homepage colour families (homepage_<family>_fg and _bg) with the holiday's petals.
 * Fabricated overlays are registered for every user, so each user has its own, by name.
 */
object HolidayOverlay {
    private const val TAG = "HolidayOverlay"

    private const val NAME_PREFIX = "expressiveos_holiday_homepage_u"
    private const val TARGET = "com.android.settings"
    private const val NIGHT = "night"

    /** Each homepage colour family and the petal it takes. Grey (System, About) stays as it is. */
    private val FAMILIES = listOf(
        "blue_variant" to 0,
        "blue" to 1,
        "pink" to 2,
        "orange" to 3,
        "yellow" to 0,
        "green" to 1,
        "cyan" to 2,
        "red" to 3,
        "purple" to 0,
    )

    /** Settings' homepage icons in [holiday]'s petals, registered and enabled for this user. */
    fun apply(context: Context, holiday: Holiday): Boolean {
        return try {
            val overlay = FabricatedOverlay.Builder(context.packageName, name(context), TARGET)
                .build()
            val petals = holiday.colors.petals
            for ((family, index) in FAMILIES) {
                val petal = petals[index]
                val prefix = "$TARGET:color/homepage_$family"
                // fg has no night value in Settings; bg has one for each theme
                putColor(overlay, "${prefix}_fg", petal.homeFg, null)
                putColor(overlay, "${prefix}_bg", petal.homeBgLight, null)
                putColor(overlay, "${prefix}_bg", petal.homeBgDark, NIGHT)
            }
            // Registering the same name again replaces the previous holiday's overlay. It's
            // registered for every user but enabled only for this one
            overlayManager(context).commit(
                OverlayManagerTransaction.Builder()
                    .registerFabricatedOverlay(overlay)
                    .setEnabled(overlay.identifier, true, context.userId)
                    .build()
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply the ${holiday.id} homepage colors", e)
            false
        }
    }

    /** Takes this user's overlay away. False when the overlay manager failed. */
    fun remove(context: Context): Boolean {
        return try {
            overlayManager(context).commit(
                OverlayManagerTransaction.Builder()
                    .unregisterFabricatedOverlay(
                        OverlayIdentifier(context.packageName, name(context))
                    )
                    .build()
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove the holiday homepage colors", e)
            false
        }
    }

    /** This user's overlay: unregistering one removes it for every user. */
    private fun name(context: Context) = NAME_PREFIX + context.userId

    private fun putColor(overlay: FabricatedOverlay, name: String, color: Int, config: String?) =
        overlay.setResourceValue(name, TypedValue.TYPE_INT_COLOR_ARGB8, color, config)

    private fun overlayManager(context: Context): OverlayManager =
        checkNotNull(context.getSystemService(OverlayManager::class.java)) {
            "No OverlayManager"
        }
}
