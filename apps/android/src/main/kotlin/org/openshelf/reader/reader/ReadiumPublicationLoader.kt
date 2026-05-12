package org.openshelf.reader.reader

import android.content.Context
import java.io.File
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

internal class ReadiumPublicationLoader(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val httpClient = DefaultHttpClient()
    private val assetRetriever = AssetRetriever(appContext.contentResolver, httpClient)
    private val publicationParser = DefaultPublicationParser(
        context = appContext,
        httpClient = httpClient,
        assetRetriever = assetRetriever,
        pdfFactory = null,
    )
    private val publicationOpener = PublicationOpener(
        publicationParser = publicationParser,
        contentProtections = emptyList(),
    )

    suspend fun open(file: File): ReadiumPublicationLoadResult {
        val asset = assetRetriever
            .retrieve(file, MediaType.EPUB)
            .getOrElse { error ->
                return ReadiumPublicationLoadResult.Failure(error.message)
            }

        val publication = publicationOpener
            .open(asset, allowUserInteraction = false)
            .getOrElse { error ->
                return ReadiumPublicationLoadResult.Failure(error.message)
            }

        return ReadiumPublicationLoadResult.Success(publication)
    }
}

internal sealed interface ReadiumPublicationLoadResult {
    data class Success(val publication: Publication) : ReadiumPublicationLoadResult
    data class Failure(val message: String) : ReadiumPublicationLoadResult
}
