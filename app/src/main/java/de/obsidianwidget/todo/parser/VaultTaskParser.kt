package de.obsidianwidget.todo.parser

import de.obsidianwidget.todo.model.CompletionFormat
import de.obsidianwidget.todo.model.TaskPriority
import de.obsidianwidget.todo.model.TodoItem
import java.security.MessageDigest
import java.time.LocalDate

object VaultTaskParser {

    private val fenceStartRegex = Regex("""^\s*(`{3,}|~{3,})""")
    private val openTaskRegex = Regex("""^(\s*(?:[-*+]|\d+\.)\s+\[)( )(\]\s+)(.+)$""")

    private val tasksDueRegex = Regex("""📅\s*(\d{4}-\d{2}-\d{2})""")
    private val tasksScheduledRegex = Regex("""⏳\s*(\d{4}-\d{2}-\d{2})""")
    private val dataviewDueRegex = Regex(
        """[\[\(](?:due|fällig)::\s*(\d{4}-\d{2}-\d{2})[\]\)]""",
        RegexOption.IGNORE_CASE
    )
    private val dataviewScheduledRegex = Regex(
        """[\[\(]scheduled::\s*(\d{4}-\d{2}-\d{2})[\]\)]""",
        RegexOption.IGNORE_CASE
    )
    private val dataviewPriorityRegex = Regex(
        """[\[\(]priority::\s*(highest|high|medium|low|lowest)[\]\)]""",
        RegexOption.IGNORE_CASE
    )

    private val dataviewBracketFieldRegex = Regex("""\[[a-zA-ZäöüÄÖÜß_-]+::[^\]]*]""")
    private val dataviewParenFieldRegex = Regex("""\([a-zA-ZäöüÄÖÜß_-]+::[^)]*\)""")
    private val tasksDateMetaRegex = Regex("""[📅⏳🛫➕✅]\s*\d{4}-\d{2}-\d{2}""")
    private val tasksRecurrenceRegex = Regex("""🔁\s*[^📅⏳🛫➕✅🔺⏫🔼🔽⏬\[\]\(\)]+""")
    private val tasksPriorityEmojiRegex = Regex("""[🔺⏫🔼🔽⏬]""")

    private val wikiLinkAliasRegex = Regex("""\[\[([^\]|]+)\|([^\]]+)]]""")
    private val wikiLinkSimpleRegex = Regex("""\[\[([^\]]+)]]""")
    private val markdownLinkRegex = Regex("""\[([^\]]+)]\([^)]+\)""")
    private val multiSpaceRegex = Regex("""\s{2,}""")

    /**
     * Parses open Markdown tasks from a note's contents, skipping fenced code blocks and
     * extracting Obsidian Tasks and Dataview metadata.
     */
    fun parseMarkdown(
        content: String,
        documentUri: String,
        relativePath: String
    ): List<TodoItem> {
        if (content.isBlank()) return emptyList()

        val noteName = relativePath
            .substringAfterLast('/')
            .removeSuffix(".md")
            .removeSuffix(".MD")
        val lines = content.split('\n')
        val result = mutableListOf<TodoItem>()

        var activeFenceChar: Char? = null
        var activeFenceLen = 0

        for ((index, rawLineWithCr) in lines.withIndex()) {
            val line = rawLineWithCr.trimEnd('\r')

            val fenceMatch = fenceStartRegex.find(line)
            if (fenceMatch != null) {
                val marker = fenceMatch.groupValues[1]
                val markerChar = marker[0]
                if (activeFenceChar == null) {
                    activeFenceChar = markerChar
                    activeFenceLen = marker.length
                    continue
                } else if (markerChar == activeFenceChar &&
                    marker.length >= activeFenceLen &&
                    line.trim().all { it == markerChar }
                ) {
                    activeFenceChar = null
                    activeFenceLen = 0
                    continue
                }
            }
            if (activeFenceChar != null) continue

            val taskMatch = openTaskRegex.matchEntire(line) ?: continue
            val rawBody = taskMatch.groupValues[4]
            val dueDate = extractDueDate(rawBody)
            val priority = extractPriority(rawBody)
            val cleanTitle = cleanTaskTitle(rawBody)

            result.add(
                TodoItem(
                    id = computeStableId(relativePath, index, line),
                    cleanTitle = cleanTitle,
                    rawLine = line,
                    documentUri = documentUri,
                    relativePath = relativePath,
                    noteName = noteName,
                    lineNumber = index,
                    dueDate = dueDate,
                    priority = priority
                )
            )
        }
        return result
    }

    fun sortTasks(items: List<TodoItem>): List<TodoItem> {
        return items.sortedWith(
            compareBy<TodoItem>(
                { it.dueDate == null },
                { it.dueDate ?: "9999-12-31" },
                { it.priority.rank },
                { it.relativePath.lowercase() },
                { it.lineNumber }
            )
        )
    }

    /**
     * Marks a single open task line as completed and appends the configured completion metadata.
     * Returns null if the target line could not be matched or is already completed.
     */
    fun markLineCompleted(
        fileContent: String,
        lineNumber: Int,
        expectedRawLine: String,
        format: CompletionFormat,
        completionDate: LocalDate = LocalDate.now()
    ): String? {
        val lineEnding = if (fileContent.contains("\r\n")) "\r\n" else "\n"
        val normalizedExpected = expectedRawLine.trimEnd('\r')
        val lines = fileContent.split('\n').map { it.trimEnd('\r') }.toMutableList()

        val targetIndex = when {
            lines.getOrNull(lineNumber) == normalizedExpected -> lineNumber
            else -> lines.indexOfFirst { it == normalizedExpected }
        }
        if (targetIndex < 0) return null

        val targetLine = lines[targetIndex]
        val match = openTaskRegex.matchEntire(targetLine) ?: return null
        val prefix = match.groupValues[1]
        val suffixBracket = match.groupValues[3]
        val body = match.groupValues[4].trimEnd()

        val alreadyHasCompletion = body.contains("✅") ||
            body.contains("completion::", ignoreCase = true)

        val completionSuffix = if (alreadyHasCompletion) {
            ""
        } else {
            when (format) {
                CompletionFormat.OBSIDIAN_TASKS -> " ✅ $completionDate"
                CompletionFormat.DATAVIEW -> " [completion:: $completionDate]"
                CompletionFormat.PLAIN -> ""
            }
        }

        lines[targetIndex] = "$prefix" + "x" + "$suffixBracket$body$completionSuffix"
        return lines.joinToString(lineEnding)
    }

    /**
     * Appends a new open Markdown task cleanly to the end of an existing note.
     */
    fun appendNewTask(
        existingContent: String,
        taskText: String,
        dueDate: String? = null,
        priority: TaskPriority = TaskPriority.NORMAL
    ): String {
        val trimmedText = taskText.trim()
        require(trimmedText.isNotEmpty()) { "Task text must not be empty" }

        val lineEnding = if (existingContent.contains("\r\n")) "\r\n" else "\n"
        val taskLine = buildString {
            append("- [ ] ")
            append(trimmedText)
            if (priority != TaskPriority.NORMAL && priority.symbol.isNotEmpty()) {
                append(' ')
                append(priority.symbol)
            }
            if (!dueDate.isNullOrBlank()) {
                append(" 📅 ")
                append(dueDate.trim())
            }
        }

        if (existingContent.isBlank()) {
            return taskLine + lineEnding
        }

        val cleanedExisting = existingContent.trimEnd('\r', '\n')
        return cleanedExisting + lineEnding + taskLine + lineEnding
    }

    internal fun extractDueDate(rawBody: String): String? {
        return tasksDueRegex.find(rawBody)?.groupValues?.get(1)
            ?: dataviewDueRegex.find(rawBody)?.groupValues?.get(1)
            ?: tasksScheduledRegex.find(rawBody)?.groupValues?.get(1)
            ?: dataviewScheduledRegex.find(rawBody)?.groupValues?.get(1)
    }

    internal fun extractPriority(rawBody: String): TaskPriority {
        for (priority in TaskPriority.entries) {
            if (priority.symbol.isNotEmpty() && rawBody.contains(priority.symbol)) {
                return priority
            }
        }
        val dvMatch = dataviewPriorityRegex.find(rawBody)?.groupValues?.get(1)?.lowercase()
        return when (dvMatch) {
            "highest" -> TaskPriority.HIGHEST
            "high" -> TaskPriority.HIGH
            "medium" -> TaskPriority.MEDIUM
            "low" -> TaskPriority.LOW
            "lowest" -> TaskPriority.LOWEST
            else -> TaskPriority.NORMAL
        }
    }

    internal fun cleanTaskTitle(rawBody: String): String {
        var cleaned = rawBody
        cleaned = dataviewBracketFieldRegex.replace(cleaned, " ")
        cleaned = dataviewParenFieldRegex.replace(cleaned, " ")
        cleaned = tasksDateMetaRegex.replace(cleaned, " ")
        cleaned = tasksRecurrenceRegex.replace(cleaned, " ")
        cleaned = tasksPriorityEmojiRegex.replace(cleaned, " ")
        cleaned = wikiLinkAliasRegex.replace(cleaned, "$2")
        cleaned = wikiLinkSimpleRegex.replace(cleaned, "$1")
        cleaned = markdownLinkRegex.replace(cleaned, "$1")
        cleaned = multiSpaceRegex.replace(cleaned, " ").trim()
        return cleaned.ifEmpty { rawBody.trim() }
    }

    private fun computeStableId(relativePath: String, lineNumber: Int, rawLine: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$relativePath:$lineNumber:$rawLine".toByteArray(Charsets.UTF_8))
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }
}
