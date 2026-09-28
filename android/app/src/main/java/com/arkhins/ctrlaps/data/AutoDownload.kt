package com.arkhins.ctrlaps.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Which kinds of attachment the phone fetches by itself as they arrive (Storage → Automatic downloads).
 * All on to start. A kind that is off downloads only when tapped; a photo shows blurred until then.
 */
class AutoDownload(context: Context) {
    private val prefs = context.getSharedPreferences("ctrlaps_auto_download", Context.MODE_PRIVATE)
    private val _photos = MutableStateFlow(prefs.getBoolean(PHOTOS, true))
    private val _audio = MutableStateFlow(prefs.getBoolean(AUDIO, true))
    private val _documents = MutableStateFlow(prefs.getBoolean(DOCUMENTS, true))

    val photos: StateFlow<Boolean> = _photos
    val audio: StateFlow<Boolean> = _audio
    val documents: StateFlow<Boolean> = _documents

    fun setPhotos(on: Boolean) = save(PHOTOS, on, _photos)
    fun setAudio(on: Boolean) = save(AUDIO, on, _audio)
    fun setDocuments(on: Boolean) = save(DOCUMENTS, on, _documents)

    private fun save(key: String, on: Boolean, flow: MutableStateFlow<Boolean>) {
        prefs.edit().putBoolean(key, on).apply()
        flow.value = on
    }

    private companion object {
        const val PHOTOS = "photos"
        const val AUDIO = "audio"
        const val DOCUMENTS = "documents"
    }
}
