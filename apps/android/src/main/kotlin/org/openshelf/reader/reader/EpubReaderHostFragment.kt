package org.openshelf.reader.reader

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.openshelf.reader.storage.DeviceId
import org.openshelf.reader.storage.OpenShelfDatabase
import org.openshelf.reader.storage.ReadingSessionId
import org.openshelf.reader.storage.SqlDelightReadingPositionRepository
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

internal class EpubReaderHostFragment : Fragment() {
    private lateinit var container: FrameLayout
    private lateinit var request: ReaderLaunchRequest
    private var databaseDriver: AndroidSqliteDriver? = null
    private var publication: Publication? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        request = ReaderActivity.readArguments(requireArguments())
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        this.container = FrameLayout(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            setBackgroundColor(readerBackgroundColor())
        }
        return this.container
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        showLoading()
        viewLifecycleOwner.lifecycleScope.launch {
            openPublication()
        }
    }

    override fun onDestroy() {
        publication?.close()
        databaseDriver?.close()
        super.onDestroy()
    }

    private suspend fun openPublication() {
        val context = requireContext().applicationContext
        val resolved = withContext(Dispatchers.IO) {
            CachedPublicationFileResolver(context).resolveExistingEpub(request.localPath)
        }
        val file = when (resolved) {
            is CachedPublicationFileResolution.Resolved -> resolved.file
            is CachedPublicationFileResolution.InvalidPath -> {
                showError(resolved.message)
                return
            }

            is CachedPublicationFileResolution.Missing -> {
                showError(resolved.message)
                return
            }

            is CachedPublicationFileResolution.UnsupportedFormat -> {
                showError(resolved.message)
                return
            }
        }

        val loadResult = withContext(Dispatchers.IO) {
            ReadiumPublicationLoader(context).open(file)
        }
        val openedPublication = when (loadResult) {
            is ReadiumPublicationLoadResult.Success -> loadResult.publication
            is ReadiumPublicationLoadResult.Failure -> {
                showError(loadResult.message.ifBlank { "Could not open this EPUB." })
                return
            }
        }
        publication = openedPublication

        val positionRepository = createPositionRepository(context)
        val initialLocator = withContext(Dispatchers.IO) {
            positionRepository.getLatestForBook(request.bookIdentityId)
                ?.locatorJson
                ?.toLocatorOrNull()
        }

        showNavigator(
            publication = openedPublication,
            initialLocator = initialLocator,
            positionRepository = positionRepository,
        )
    }

    @OptIn(ExperimentalReadiumApi::class)
    private fun showNavigator(
        publication: Publication,
        initialLocator: Locator?,
        positionRepository: SqlDelightReadingPositionRepository,
    ) {
        val childContainerId = View.generateViewId()
        val childContainer = FrameLayout(requireContext()).apply {
            id = childContainerId
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        }
        container.removeAllViews()
        container.addView(childContainer)

        val factory = EpubNavigatorFactory(publication = publication)
        childFragmentManager.fragmentFactory = factory.createFragmentFactory(initialLocator = initialLocator)
        val navigator = childFragmentManager.fragmentFactory.instantiate(
            requireContext().classLoader,
            EpubNavigatorFragment::class.java.name,
        ) as EpubNavigatorFragment

        childFragmentManager.beginTransaction()
            .replace(childContainerId, navigator, NavigatorTag)
            .commitNow()

        observeLocalPosition(navigator, positionRepository)
    }

    private fun observeLocalPosition(
        navigator: EpubNavigatorFragment,
        positionRepository: SqlDelightReadingPositionRepository,
    ) {
        val mapper = ReaderPositionMapper(clock = { System.currentTimeMillis() })
        val deviceId = localDeviceId(requireContext())
        val sessionId = ReadingSessionId("reader-session:${request.publicationFileId.value}:${System.currentTimeMillis()}")

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                navigator.currentLocator.collect { locator ->
                    val position = mapper.toLocalReadingPosition(
                        locator = locator,
                        bookIdentityId = request.bookIdentityId,
                        deviceId = deviceId,
                        sessionId = sessionId,
                    )
                    withContext(Dispatchers.IO) {
                        positionRepository.upsert(position)
                    }
                }
            }
        }
    }

    private fun createPositionRepository(context: Context): SqlDelightReadingPositionRepository {
        val existingDriver = databaseDriver
        val driver = existingDriver ?: AndroidSqliteDriver(
            schema = OpenShelfDatabase.Schema,
            context = context,
            name = "openshelf-reader.db",
        ).also { databaseDriver = it }

        return SqlDelightReadingPositionRepository(driver)
    }

    private fun showLoading() {
        container.removeAllViews()
        container.addView(
            readerMessageLayout().apply {
                addView(ProgressBar(requireContext()))
                addView(
                    readerTextView("Opening EPUB...").apply {
                        setPadding(0, 24, 0, 0)
                    },
                )
            },
        )
    }

    private fun showError(message: String) {
        container.removeAllViews()
        container.addView(
            readerMessageLayout().apply {
                addView(readerTextView(message))
                addView(
                    Button(requireContext()).apply {
                        text = "Back"
                        setOnClickListener { requireActivity().finish() }
                    },
                )
            },
        )
    }

    private fun readerMessageLayout(): LinearLayout =
        LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
        }

    private fun readerTextView(message: String): TextView =
        TextView(requireContext()).apply {
            text = message
            setTextColor(readerTextColor())
            gravity = Gravity.CENTER
            textSize = 16f
        }

    private fun readerBackgroundColor(): Int =
        if (isNightMode()) Color.rgb(18, 18, 18) else Color.WHITE

    private fun readerTextColor(): Int =
        if (isNightMode()) Color.WHITE else Color.rgb(32, 32, 32)

    private fun isNightMode(): Boolean {
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    private fun String.toLocatorOrNull(): Locator? =
        runCatching { Locator.fromJSON(JSONObject(this)) }.getOrNull()

    private fun localDeviceId(context: Context): DeviceId {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID,
        ).orEmpty()
        return DeviceId("android:${androidId.ifBlank { "local-device" }}")
    }

    internal companion object {
        private const val NavigatorTag = "openshelf-epub-navigator"

        fun newInstance(request: ReaderLaunchRequest): EpubReaderHostFragment =
            EpubReaderHostFragment().apply {
                arguments = Bundle().also { ReaderActivity.putArguments(it, request) }
            }
    }
}
