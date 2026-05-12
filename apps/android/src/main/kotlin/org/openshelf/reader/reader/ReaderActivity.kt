package org.openshelf.reader.reader

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import org.openshelf.reader.storage.BookIdentityId
import org.openshelf.reader.storage.PublicationFileId

internal class ReaderActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = FrameLayout(this).apply {
            id = View.generateViewId()
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        }
        setContentView(container)

        if (savedInstanceState == null) {
            val request = fromIntent(intent)
            supportFragmentManager.beginTransaction()
                .replace(
                    container.id,
                    EpubReaderHostFragment.newInstance(request),
                    ReaderFragmentTag,
                )
                .commit()
        }
    }

    internal companion object {
        private const val ReaderFragmentTag = "openshelf-reader-fragment"
        private const val ExtraLocalPath = "org.openshelf.reader.extra.LOCAL_PATH"
        private const val ExtraBookIdentityId = "org.openshelf.reader.extra.BOOK_IDENTITY_ID"
        private const val ExtraPublicationFileId = "org.openshelf.reader.extra.PUBLICATION_FILE_ID"

        fun createIntent(
            context: Context,
            localPath: String,
            bookIdentityId: String,
            publicationFileId: String,
        ): Intent {
            return Intent(context, ReaderActivity::class.java)
                .putExtra(ExtraLocalPath, localPath)
                .putExtra(ExtraBookIdentityId, bookIdentityId)
                .putExtra(ExtraPublicationFileId, publicationFileId)
        }

        fun putArguments(
            bundle: Bundle,
            request: ReaderLaunchRequest,
        ) {
            bundle.putString(ExtraLocalPath, request.localPath)
            bundle.putString(ExtraBookIdentityId, request.bookIdentityId.value)
            bundle.putString(ExtraPublicationFileId, request.publicationFileId.value)
        }

        fun readArguments(bundle: Bundle): ReaderLaunchRequest =
            ReaderLaunchRequest(
                localPath = bundle.getString(ExtraLocalPath).orEmpty(),
                bookIdentityId = BookIdentityId(bundle.getString(ExtraBookIdentityId).orEmpty()),
                publicationFileId = PublicationFileId(bundle.getString(ExtraPublicationFileId).orEmpty()),
            )

        private fun fromIntent(intent: Intent): ReaderLaunchRequest =
            ReaderLaunchRequest(
                localPath = intent.getStringExtra(ExtraLocalPath).orEmpty(),
                bookIdentityId = BookIdentityId(intent.getStringExtra(ExtraBookIdentityId).orEmpty()),
                publicationFileId = PublicationFileId(intent.getStringExtra(ExtraPublicationFileId).orEmpty()),
            )
    }
}

internal data class ReaderLaunchRequest(
    val localPath: String,
    val bookIdentityId: BookIdentityId,
    val publicationFileId: PublicationFileId,
) {
    companion object
}
