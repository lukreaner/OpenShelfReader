package org.openshelf.reader

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import org.openshelf.reader.core.OpenShelfCore

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val padding = (24 * resources.displayMetrics.density).toInt()
        setContentView(
            TextView(this).apply {
                text = buildString {
                    append(getString(R.string.app_name))
                    append('\n')
                    append(OpenShelfCore.supportedFormats.joinToString { it.name })
                }
                textSize = 20f
                setPadding(padding, padding, padding, padding)
            },
        )
    }
}
