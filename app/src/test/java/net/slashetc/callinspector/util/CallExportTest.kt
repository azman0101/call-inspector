package net.slashetc.callinspector.util

import net.slashetc.callinspector.data.model.ArcepLookupResult
import net.slashetc.callinspector.data.model.ArcepOperator
import net.slashetc.callinspector.data.model.CallLogEntry
import net.slashetc.callinspector.data.model.CallType
import net.slashetc.callinspector.data.model.PhoneNumberType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class CallExportTest {

    private val paris = TimeZone.getTimeZone("Europe/Paris")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int = 0): Long =
        Calendar.getInstance(paris).apply {
            clear()
            set(year, month - 1, day, hour, minute, second)
        }.timeInMillis

    private fun call(
        id: Long,
        number: String,
        timestamp: Long,
        type: CallType = CallType.MISSED,
        duration: Long = 0,
        note: String? = null,
    ) = CallLogEntry(
        id = id,
        rawNumber = number,
        normalizedNumber = number,
        formattedNumber = PhoneNumberFormatter.format(number),
        cachedName = null,
        timestamp = timestamp,
        durationSeconds = duration,
        callType = type,
        lookupResult = ArcepLookupResult(
            queryNumber = number,
            normalizedNumber = number,
            formattedNumber = PhoneNumberFormatter.format(number),
            operator = ArcepOperator(code = "QWLK", name = "Qwalikom"),
            range = null,
            numberType = PhoneNumberType.classify(number),
            isFound = true
        ),
        userNote = note
    )

    // Listed most recent first, like the call history.
    private val calls = listOf(
        call(3, "0159391234", at(2026, 9, 28, 14, 3, 12), CallType.INCOMING, duration = 65, note = "Isolation, rappel \"urgent\""),
        call(2, "0948123456", at(2026, 9, 27, 9, 30, 5)),
        call(1, "0159391234", at(2026, 3, 20, 10, 0, 0), CallType.REJECTED),
    )

    @Test
    fun `numbersIn groups calls by caller number in list order`() {
        val numbers = CallExport.numbersIn(calls)

        assertEquals(listOf("0159391234", "0948123456"), numbers.map { it.normalizedNumber })
        assertEquals(listOf(3L, 1L), numbers[0].calls.map { it.id })
        assertEquals("01 59 39 12 34", numbers[0].formattedNumber)
        assertEquals("Qwalikom", numbers[0].operatorName)
    }

    @Test
    fun `selectedCalls keeps only the selected numbers, oldest first`() {
        val selected = CallExport.selectedCalls(calls, setOf("0159391234"))

        assertEquals(listOf(1L, 3L), selected.map { it.id })
        assertTrue(CallExport.selectedCalls(calls, emptySet()).isEmpty())
    }

    @Test
    fun `csv lists each call with its local date and time`() {
        val csv = CallExport.format(calls, CallExport.Options(CallExportFormat.CSV), paris)

        assertEquals(
            """
            Numéro appelant,Date,Heure,Type,Durée (s),Opérateur
            01 59 39 12 34,20/03/2026,10:00:00,Rejeté,0,Qwalikom
            09 48 12 34 56,27/09/2026,09:30:05,Manqué,0,Qwalikom
            01 59 39 12 34,28/09/2026,14:03:12,Reçu,65,Qwalikom
            """.trimIndent() + "\n",
            csv
        )
    }

    @Test
    fun `csv adds the receiving line and quotes notes holding separators or quotes`() {
        val csv = CallExport.format(
            calls,
            CallExport.Options(CallExportFormat.CSV, includeNotes = true, receivingNumber = " 06 12 34 56 78 "),
            paris
        )
        val lines = csv.trimEnd().lines()

        assertEquals("Numéro appelant,Date,Heure,Type,Durée (s),Opérateur,Ligne appelée,Note", lines[0])
        assertEquals(
            "01 59 39 12 34,28/09/2026,14:03:12,Reçu,65,Qwalikom,06 12 34 56 78,\"Isolation, rappel \"\"urgent\"\"\"",
            lines[3]
        )
        assertTrue(lines[1].endsWith(",06 12 34 56 78,"))
    }

    @Test
    fun `markdown has a summary, the receiving line and one table row per call`() {
        val markdown = CallExport.format(
            calls,
            CallExport.Options(CallExportFormat.MARKDOWN, receivingNumber = "06 12 34 56 78", searchLabel = "QWALIKOM"),
            paris
        )

        assertEquals(
            CallExport.AI_INSTRUCTIONS + "\n\n" + """
            **Appels correspondant à « QWALIKOM »** : 3 appels de 2 numéros

            Ligne ayant reçu les appels : 06 12 34 56 78

            Heures locales du téléphone (Europe/Paris).

            | Numéro appelant | Date | Heure | Type | Durée | Opérateur |
            |---|---|---|---|---|---|
            | 01 59 39 12 34 | 20/03/2026 | 10:00:00 | Rejeté | 0 s | Qwalikom |
            | 09 48 12 34 56 | 27/09/2026 | 09:30:05 | Manqué | 0 s | Qwalikom |
            | 01 59 39 12 34 | 28/09/2026 | 14:03:12 | Reçu | 1m 5s | Qwalikom |
            """.trimIndent() + "\n",
            markdown
        )
    }

    @Test
    fun `markdown notes cannot break the table`() {
        val withNote = listOf(call(9, "0159391234", at(2026, 9, 28, 8, 0), note = "a | b\nc"))

        val markdown = CallExport.format(withNote, CallExport.Options(CallExportFormat.MARKDOWN, includeNotes = true), paris)

        assertTrue(markdown.contains("\n**Appels** : 1 appel de 1 numéro\n"))
        assertTrue(markdown.contains("| Note |"))
        assertTrue(markdown.contains("| Qwalikom | a \\| b c |\n"))
        assertFalse(markdown.contains("Ligne ayant reçu"))
    }

    @Test
    fun `markdown tells the AI assistant to write the mail in plain text, csv has no instructions`() {
        val markdown = CallExport.format(calls, CallExport.Options(CallExportFormat.MARKDOWN), paris)
        val csv = CallExport.format(calls, CallExport.Options(CallExportFormat.CSV), paris)

        assertTrue(markdown.startsWith("> **Consignes pour l'assistant IA"))
        assertTrue(markdown.contains("texte brut, sans aucune mise en forme Markdown"))
        assertFalse(markdown.contains("UTC"))
        assertFalse(csv.contains("assistant"))
    }

    @Test
    fun `notes are left out unless asked for`() {
        val csv = CallExport.format(calls, CallExport.Options(CallExportFormat.CSV), paris)
        val markdown = CallExport.format(calls, CallExport.Options(CallExportFormat.MARKDOWN), paris)

        assertFalse(csv.contains("Isolation"))
        assertFalse(markdown.contains("Isolation"))
    }
}
