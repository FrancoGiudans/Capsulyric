package com.example.islandlyrics

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton

/**
 * Dialog to show update availability.
 * Provides 3 options: Download, Ignore Version, Cancel
 */
class UpdateDialogFragment : DialogFragment() {

    private lateinit var releaseInfo: UpdateChecker.ReleaseInfo

    companion object {
        private const val ARG_TAG_NAME = "tag_name"
        private const val ARG_NAME = "name"
        private const val ARG_BODY = "body"
        private const val ARG_HTML_URL = "html_url"
        private const val ARG_PUBLISHED_AT = "published_at"

        fun newInstance(release: UpdateChecker.ReleaseInfo): UpdateDialogFragment {
            val fragment = UpdateDialogFragment()
            val args = Bundle().apply {
                putString(ARG_TAG_NAME, release.tagName)
                putString(ARG_NAME, release.name)
                putString(ARG_BODY, release.body)
                putString(ARG_HTML_URL, release.htmlUrl)
                putString(ARG_PUBLISHED_AT, release.publishedAt)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Reconstruct ReleaseInfo from arguments
        releaseInfo = UpdateChecker.ReleaseInfo(
            tagName = arguments?.getString(ARG_TAG_NAME) ?: "",
            name = arguments?.getString(ARG_NAME) ?: "",
            body = arguments?.getString(ARG_BODY) ?: "",
            htmlUrl = arguments?.getString(ARG_HTML_URL) ?: "",
            publishedAt = arguments?.getString(ARG_PUBLISHED_AT) ?: ""
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_update_available, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set dialog title
        val tvTitle = view.findViewById<TextView>(R.id.tv_update_title)
        tvTitle.text = getString(R.string.update_available_title, releaseInfo.tagName)

        // Show current version
        val tvCurrentVersion = view.findViewById<TextView>(R.id.tv_current_version)
        tvCurrentVersion.text = getString(R.string.update_current_version, BuildConfig.VERSION_NAME)

        // Display changelog (simplified Markdown parsing)
        val tvChangelog = view.findViewById<TextView>(R.id.tv_changelog)
        tvChangelog.text = parseMarkdown(releaseInfo.body)

        // Download button
        val btnDownload = view.findViewById<MaterialButton>(R.id.btn_download)
        btnDownload.setOnClickListener {
            openDownloadUrl()
            dismiss()
        }

        // Ignore version button
        val btnIgnore = view.findViewById<MaterialButton>(R.id.btn_ignore)
        btnIgnore.setOnClickListener {
            context?.let {
                UpdateChecker.setIgnoredVersion(it, releaseInfo.tagName)
                AppLogger.getInstance().log("Update", "Ignored version: ${releaseInfo.tagName}")
            }
            dismiss()
        }

        // Cancel button
        val btnCancel = view.findViewById<MaterialButton>(R.id.btn_cancel)
        btnCancel.setOnClickListener {
            dismiss()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState)
    }

    /**
     * Open GitHub release page in browser.
     */
    private fun openDownloadUrl() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(releaseInfo.htmlUrl))
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Simple Markdown parser for changelog.
     * Converts basic Markdown to plain text with formatting.
     */
    private fun parseMarkdown(markdown: String): String {
        return markdown
            .replace("### ", "\n")
            .replace("## ", "\n")
            .replace("# ", "\n")
            .replace("**", "")
            .replace("__", "")
            .replace("- ", "• ")
            .replace("* ", "• ")
            .trim()
    }
}
