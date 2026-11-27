/*
 * Copyright (C) 2023-2024 the risingOS Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.expressiveos.settings.fragments.lockscreen

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.UserHandle
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.widget.Toast

import androidx.preference.Preference

import com.android.internal.logging.nano.MetricsProto

import com.android.settings.R
import com.android.settings.SettingsPreferenceFragment
import com.android.settings.search.BaseSearchIndexProvider
import com.android.settingslib.search.SearchIndexable

import java.io.File

import org.expressiveos.settings.preferences.showConfirmDialog
import org.expressiveos.settings.utils.ImageUtils
import org.expressiveos.settings.utils.WallpaperSubjectExtractorService

@SearchIndexable
class WallpaperDepth : SettingsPreferenceFragment(), Preference.OnPreferenceChangeListener {

    private var mDepthWallpaperCustomImagePicker: Preference? = null
    private var mExtractNowPref: Preference? = null
    private var mClearSubjectPref: Preference? = null
    private var mAutoSubjectPref: Preference? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.wallpaper_depth)

        mDepthWallpaperCustomImagePicker = findPreference("depth_wallpaper_subject_image_uri")
        mExtractNowPref = findPreference("depth_wallpaper_extract_now")
        mClearSubjectPref = findPreference("depth_wallpaper_clear_subject")
        mAutoSubjectPref = findPreference("depth_wallpaper_auto_subject")

        updateClearSubjectState()
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        return false
    }

    override fun getMetricsCategory(): Int {
        return MetricsProto.MetricsEvent.EXPRESSIVE_STUDIO
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        if (preference == mDepthWallpaperCustomImagePicker) {
            launchImagePicker()
            return true
        }
        if (preference == mExtractNowPref) {
            triggerExtraction()
            return true
        }
        if (preference == mClearSubjectPref) {
            confirmClearSubject()
            return true
        }
        return super.onPreferenceTreeClick(preference)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, result: Intent?) {
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == Activity.RESULT_OK && result != null) {
            val imgUri: Uri? = result.data
            if (imgUri != null) {
                val savedImagePath = context?.let { ctx ->
                    ImageUtils.saveImageToInternalStorage(
                        ctx, imgUri, "depthwallpaper", FILE_PREFIX
                    )
                }
                if (savedImagePath != null) {
                    val resolver = context?.contentResolver
                    resolver?.let {
                        Settings.System.putStringForUser(
                            it, "depth_wallpaper_subject_image_uri",
                            savedImagePath, UserHandle.USER_CURRENT
                        )
                    }
                    updateClearSubjectState()
                }
            }
        }
    }

    private fun launchImagePicker() {
        try {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            intent.type = "image/*"
            startActivityForResult(intent, REQUEST_PICK_IMAGE)
        } catch (e: Exception) {
            Toast.makeText(context, R.string.quick_settings_header_needs_gallery, Toast.LENGTH_LONG).show()
        }
    }

    private fun triggerExtraction() {
        startExtractionService()
    }

    private fun startExtractionService() {
        val ctx = context ?: return

        Toast.makeText(ctx, R.string.depthwall_extracting_toast, Toast.LENGTH_SHORT).show()

        try {
            val intent = Intent(ctx, WallpaperSubjectExtractorService::class.java)
            intent.action = WallpaperSubjectExtractorService.ACTION_EXTRACT_NOW
            intent.component = ComponentName(ctx, WallpaperSubjectExtractorService::class.java)
            ctx.startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start subject extractor", e)
            Toast.makeText(
                ctx, R.string.depthwall_extract_failed_toast, Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun confirmClearSubject() {
        val ctx = context ?: return

        showConfirmDialog(
            context = ctx,
            title = ctx.getString(R.string.depthwall_clear_subject_title),
            message = ctx.getString(R.string.depthwall_clear_subject_confirm_message),
            confirmText = ctx.getString(android.R.string.ok),
            dismissText = ctx.getString(android.R.string.cancel),
            onConfirm = { clearSubject() },
        )
    }

    private fun clearSubject() {
        val ctx = context ?: return

        Settings.System.putStringForUser(
            ctx.contentResolver,
            "depth_wallpaper_subject_image_uri",
            null,
            UserHandle.USER_CURRENT
        )

        try {
            val dir = File(Environment.getExternalStorageDirectory(), SAVE_DIR)
            if (dir.exists()) {
                val files = dir.listFiles { _, name ->
                    if (!name.startsWith(FILE_PREFIX)) return@listFiles false
                    when (name.substringAfterLast('.', "").lowercase()) {
                        "png", "jpg", "jpeg", "gif", "webp" -> true
                        else -> false
                    }
                }
                files?.forEach { it.delete() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "clearSubject: failed to delete cached files", e)
        }

        Toast.makeText(ctx, R.string.depthwall_clear_subject_done_toast, Toast.LENGTH_SHORT).show()
        updateClearSubjectState()
    }

    private fun updateClearSubjectState() {
        val clearPref = mClearSubjectPref ?: return
        val ctx = context ?: return
        val uri = Settings.System.getStringForUser(
            ctx.contentResolver,
            "depth_wallpaper_subject_image_uri",
            UserHandle.USER_CURRENT
        )
        clearPref.isEnabled = !uri.isNullOrEmpty()
    }

    companion object {
        const val TAG = "WallpaperDepth"

        private const val REQUEST_PICK_IMAGE = 10001

        private const val FILE_PREFIX = "DEPTH_WALLPAPER_SUBJECT"
        private const val SAVE_DIR = "ExpressiveOS/depthwallpaper"

        /**
         * For search
         */
        @JvmField
        val SEARCH_INDEX_DATA_PROVIDER = object : BaseSearchIndexProvider(R.xml.wallpaper_depth) {
            override fun getNonIndexableKeys(context: Context): List<String> {
                val keys = super.getNonIndexableKeys(context)
                return keys
            }
        }
    }
}
