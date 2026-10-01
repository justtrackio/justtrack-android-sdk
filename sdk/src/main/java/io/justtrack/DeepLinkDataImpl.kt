package io.justtrack

import android.net.Uri
import io.justtrack.deeplinks.DeepLinkData

internal data class DeepLinkDataImpl(override val uri: Uri) : DeepLinkData
