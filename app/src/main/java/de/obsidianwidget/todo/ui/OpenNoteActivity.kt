package de.obsidianwidget.todo.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity

class OpenNoteActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val relPath = intent.getStringExtra(EXTRA_REL_PATH).orEmpty()
        val pathWithoutExtension = relPath
            .removeSuffix(".md")
            .removeSuffix(".MD")

        val obsidianUri = if (pathWithoutExtension.isNotBlank()) {
            Uri.parse("obsidian://open?file=${Uri.encode(pathWithoutExtension)}")
        } else {
            Uri.parse("obsidian://open")
        }

        val obsidianIntent = Intent(Intent.ACTION_VIEW, obsidianUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val launched = runCatching {
            startActivity(obsidianIntent)
            true
        }.getOrDefault(false)

        if (!launched) {
            Toast.makeText(
                this,
                "Obsidian konnte nicht geöffnet werden.",
                Toast.LENGTH_SHORT
            ).show()
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }

        finish()
    }

    companion object {
        const val EXTRA_REL_PATH = "extra_rel_path"
    }
}
