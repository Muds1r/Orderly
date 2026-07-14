package com.orderly.app.data.mail

import com.orderly.app.data.parser.OrderParser
import com.orderly.app.data.parser.StoreRegistry
import com.orderly.app.data.parser.CourierRegistry
import com.sun.mail.iap.Argument
import com.sun.mail.iap.ProtocolException
import com.sun.mail.imap.IMAPFolder
import com.sun.mail.imap.protocol.IMAPResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Properties
import javax.mail.FetchProfile
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.search.AndTerm
import javax.mail.search.ComparisonTerm
import javax.mail.search.FromStringTerm
import javax.mail.search.OrTerm
import javax.mail.search.ReceivedDateTerm
import javax.mail.search.SearchTerm

/**
 * Fetches shopping-order emails from Gmail over IMAP using a Google App Password.
 *
 * No OAuth, no Google Cloud project — the app password works until revoked.
 * The folder is opened READ_ONLY, so nothing in the mailbox can be modified.
 */
class ImapClient(private val email: String, private val appPassword: String) {

    suspend fun verifyLogin(): Unit = withContext(Dispatchers.IO) {
        val store = Session.getInstance(Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.ssl.enable", "true")
            put("mail.imaps.connectiontimeout", "20000")
            put("mail.imaps.timeout", "30000")
        }).getStore("imaps")
        store.connect("imap.gmail.com", 993, email, appPassword)
        store.close()
    }

    /** Fetch and parse store order emails received after [sinceEpochMs]. */
    suspend fun fetchOrders(sinceEpochMs: Long): List<OrderParser.ParseResult> =
        withContext(Dispatchers.IO) {
            val props = Properties().apply {
                put("mail.store.protocol", "imaps")
                put("mail.imaps.ssl.enable", "true")
                put("mail.imaps.connectiontimeout", "20000")
                put("mail.imaps.timeout", "60000")
            }
            val store = Session.getInstance(props).getStore("imaps")
            store.connect("imap.gmail.com", 993, email, appPassword)
            try {
                val folder = findAllMailFolder(store)
                folder.open(Folder.READ_ONLY)
                try {
                    val messages = searchStoreMessages(folder, sinceEpochMs)
                    val profile = FetchProfile().apply {
                        add(FetchProfile.Item.ENVELOPE)
                        add("Message-ID")
                    }
                    folder.fetch(messages.toTypedArray(), profile)
                    messages.mapNotNull { parseMessage(it) }
                } finally {
                    folder.close(false)
                }
            } finally {
                store.close()
            }
        }

    private fun findAllMailFolder(store: javax.mail.Store): Folder {
        val gmailRoot = store.defaultFolder.list("[Gmail]*").firstOrNull()
        val allMail = gmailRoot?.list("%")?.firstOrNull { folder ->
            (folder as? IMAPFolder)?.attributes?.contains("\\All") == true
        }
        return allMail ?: store.getFolder("INBOX")
    }

    private fun searchStoreMessages(folder: Folder, sinceEpochMs: Long): List<Message> {
        if (folder is IMAPFolder) {
            try {
                return gmailRawSearch(folder, sinceEpochMs).toList()
            } catch (_: Exception) {
                // Fall through to standard IMAP search.
            }
        }
        return chunkedSearch(folder, sinceEpochMs)
    }

    private fun gmailRawSearch(folder: IMAPFolder, sinceEpochMs: Long): Array<Message> {
        val domains = (StoreRegistry.domainToStore.keys + CourierRegistry.allSearchDomains())
            .joinToString(" OR ")
        val afterDay = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.US)
            .format(Date(sinceEpochMs))
        val query = "from:($domains) after:$afterDay"

        @Suppress("UNCHECKED_CAST")
        val numbers = folder.doCommand { protocol ->
            val args = Argument()
            args.writeAtom("X-GM-RAW")
            args.writeString(query)
            val responses = protocol.command("SEARCH", args)
            if (!responses.last().isOK) {
                throw ProtocolException("X-GM-RAW SEARCH failed: " + responses.last())
            }
            val result = mutableListOf<Int>()
            responses.forEach { r ->
                if (r is IMAPResponse && r.keyEquals("SEARCH")) {
                    r.toString().split(' ').forEach { token ->
                        token.toIntOrNull()?.let(result::add)
                    }
                }
            }
            result
        } as List<Int>

        return folder.getMessages(numbers.toIntArray())
    }

    private fun chunkedSearch(folder: Folder, sinceEpochMs: Long): List<Message> {
        val since: SearchTerm = ReceivedDateTerm(ComparisonTerm.GE, Date(sinceEpochMs))
        val seen = mutableSetOf<Int>()
        val results = mutableListOf<Message>()

        val domains = StoreRegistry.domainToStore.keys + CourierRegistry.allSearchDomains()
        domains.chunked(6).forEach { chunk ->
            val fromTerms = chunk.map { FromStringTerm(it) as SearchTerm }
            val term = if (fromTerms.size == 1) fromTerms[0] else OrTerm(fromTerms.toTypedArray())
            folder.search(AndTerm(since, term)).forEach { message ->
                if (seen.add(message.messageNumber)) results.add(message)
            }
        }
        return results
    }

    private fun parseMessage(message: Message): OrderParser.ParseResult? {
        return try {
            val from = message.from?.firstOrNull()?.toString() ?: return null
            val timestamp = (message.receivedDate ?: message.sentDate)?.time ?: return null
            val subject = message.subject ?: ""
            val id = message.getHeader("Message-ID")?.firstOrNull()
                ?: "$from|$subject|$timestamp".hashCode().toString()

            OrderParser.parse(
                messageId = id,
                fromHeader = from,
                subject = subject,
                body = extractText(message),
                timestamp = timestamp
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun extractText(part: Part): String = try {
        when {
            part.isMimeType("text/plain") -> part.content as? String ?: ""
            part.isMimeType("text/html") -> stripHtml(part.content as? String ?: "")
            part.isMimeType("multipart/*") -> {
                val multipart = part.content as Multipart
                (0 until multipart.count).joinToString("\n") { extractText(multipart.getBodyPart(it)) }
            }
            else -> ""
        }
    } catch (_: Exception) {
        ""
    }

    private fun stripHtml(html: String): String =
        html.replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace(Regex(" {2,}"), " ")

    companion object {
        fun cleanAppPassword(raw: String): String = raw.replace(" ", "").trim()
    }
}
