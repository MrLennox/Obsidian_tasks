package de.obsidianwidget.todo.parser

import de.obsidianwidget.todo.model.CompletionFormat
import de.obsidianwidget.todo.model.TaskPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class VaultTaskParserTest {

    @Test
    fun `parses open checkboxes and ignores completed or fenced tasks`() {
        val md = """
            # Aufgaben
            - [ ] Einkaufen gehen
            * [ ] Arzttermin vereinbaren
            1. [ ] Steuererklärung abgeben
              - [ ] Unteraufgabe eingerückt
            - [x] Bereits erledigt
            - [X] Ebenfalls erledigt
            ```markdown
            - [ ] Beispiel im Codeblock ignorieren
            ```
            ~~~
            - [ ] Auch mit Tilde ignorieren
            ~~~
            - [ ] Nach Codeblock wieder aktiv
        """.trimIndent()

        val tasks = VaultTaskParser.parseMarkdown(
            content = md,
            documentUri = "content://vault/doc1",
            relativePath = "Alltag/Notizen.md"
        )

        assertEquals(5, tasks.size)
        assertEquals(
            listOf(
                "Einkaufen gehen",
                "Arzttermin vereinbaren",
                "Steuererklärung abgeben",
                "Unteraufgabe eingerückt",
                "Nach Codeblock wieder aktiv"
            ),
            tasks.map { it.cleanTitle }
        )
        assertEquals("Notizen", tasks.first().noteName)
    }

    @Test
    fun `extracts Obsidian Tasks metadata and cleans title`() {
        val md = """
            - [ ] [[Projekte/Küche|Küche streichen]] ⏫ 📅 2026-11-28 🔁 every week ➕ 2026-11-20
            - [ ] Rechnung prüfen ⏳ 2026-11-29 🔽
        """.trimIndent()

        val tasks = VaultTaskParser.parseMarkdown(md, "content://1", "Inbox.md")

        assertEquals(2, tasks.size)
        assertEquals("Küche streichen", tasks[0].cleanTitle)
        assertEquals("2026-11-28", tasks[0].dueDate)
        assertEquals(TaskPriority.HIGH, tasks[0].priority)

        assertEquals("Rechnung prüfen", tasks[1].cleanTitle)
        assertEquals("2026-11-29", tasks[1].dueDate)
        assertEquals(TaskPriority.LOW, tasks[1].priority)
    }

    @Test
    fun `extracts Dataview inline fields and markdown links`() {
        val md = """
            - [ ] [Handbuch lesen](https://example.com) [due:: 2026-12-01] [priority:: highest]
            - [ ] [[Daily/2026-11-25]] vorbereiten (fällig:: 2026-11-26) (priority:: medium)
        """.trimIndent()

        val tasks = VaultTaskParser.parseMarkdown(md, "content://2", "Work/Plan.md")

        assertEquals(2, tasks.size)
        assertEquals("Handbuch lesen", tasks[0].cleanTitle)
        assertEquals("2026-12-01", tasks[0].dueDate)
        assertEquals(TaskPriority.HIGHEST, tasks[0].priority)

        assertEquals("Daily/2026-11-25 vorbereiten", tasks[1].cleanTitle)
        assertEquals("2026-11-26", tasks[1].dueDate)
        assertEquals(TaskPriority.MEDIUM, tasks[1].priority)
    }

    @Test
    fun `sorts tasks by due date then priority then path`() {
        val md = """
            - [ ] Ohne Datum normal
            - [ ] Ohne Datum hoch ⏫
            - [ ] Später 📅 2026-12-10
            - [ ] Heute normal 📅 2026-11-25
            - [ ] Heute hoch ⏫ 📅 2026-11-25
        """.trimIndent()

        val sorted = VaultTaskParser.sortTasks(
            VaultTaskParser.parseMarkdown(md, "content://3", "Test.md")
        )

        assertEquals(
            listOf(
                "Heute hoch",
                "Heute normal",
                "Später",
                "Ohne Datum hoch",
                "Ohne Datum normal"
            ),
            sorted.map { it.cleanTitle }
        )
    }

    @Test
    fun `markLineCompleted supports all formats and preserves CRLF`() {
        val original = "- [ ] Erster Punkt\r\n- [ ] Zweiter Punkt 📅 2026-11-25\r\n"
        val date = LocalDate.of(2026, 11, 25)

        val updatedTasks = VaultTaskParser.markLineCompleted(
            fileContent = original,
            lineNumber = 1,
            expectedRawLine = "- [ ] Zweiter Punkt 📅 2026-11-25",
            format = CompletionFormat.OBSIDIAN_TASKS,
            completionDate = date
        )
        assertNotNull(updatedTasks)
        assertTrue(updatedTasks!!.contains("\r\n"))
        assertEquals(
            "- [ ] Erster Punkt\r\n- [x] Zweiter Punkt 📅 2026-11-25 ✅ 2026-11-25\r\n",
            updatedTasks
        )

        val updatedDataview = VaultTaskParser.markLineCompleted(
            fileContent = "- [ ] Aufgabe",
            lineNumber = 5, // shifted line number fallback test
            expectedRawLine = "- [ ] Aufgabe",
            format = CompletionFormat.DATAVIEW,
            completionDate = date
        )
        assertEquals("- [x] Aufgabe [completion:: 2026-11-25]", updatedDataview)

        val updatedPlain = VaultTaskParser.markLineCompleted(
            fileContent = "- [ ] Aufgabe",
            lineNumber = 0,
            expectedRawLine = "- [ ] Aufgabe",
            format = CompletionFormat.PLAIN,
            completionDate = date
        )
        assertEquals("- [x] Aufgabe", updatedPlain)

        assertNull(
            VaultTaskParser.markLineCompleted(
                fileContent = "- [x] Schon erledigt",
                lineNumber = 0,
                expectedRawLine = "- [x] Schon erledigt",
                format = CompletionFormat.PLAIN,
                completionDate = date
            )
        )
    }

    @Test
    fun `appendNewTask formats task cleanly`() {
        val appendedEmpty = VaultTaskParser.appendNewTask(
            existingContent = "",
            taskText = "Neues Todo",
            dueDate = "2026-11-25",
            priority = TaskPriority.HIGH
        )
        assertEquals("- [ ] Neues Todo ⏫ 📅 2026-11-25\n", appendedEmpty)

        val appendedExisting = VaultTaskParser.appendNewTask(
            existingContent = "# Inbox\n- [ ] Alt\n\n",
            taskText = "Neu"
        )
        assertEquals("# Inbox\n- [ ] Alt\n- [ ] Neu\n", appendedExisting)
    }
}
