package com.sigynvs.phonejanitor.email

import com.sun.mail.iap.Argument
import com.sun.mail.iap.Response
import com.sun.mail.imap.IMAPFolder
import com.sun.mail.imap.protocol.IMAPProtocol
import com.sun.mail.imap.protocol.IMAPResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties
import javax.mail.AuthenticationFailedException
import javax.mail.FetchProfile
import javax.mail.Flags
import javax.mail.Folder
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.Session
import javax.mail.Store
import javax.mail.UIDFolder
import javax.mail.internet.InternetAddress

/** A message matched by a Gmail search — enough to show a review row. */
data class MailSummary(
    val uid: Long,
    val subject: String,
    val from: String,
    val sizeBytes: Long,
    val dateMillis: Long,
)

/** [summaries] is the detailed slice shown for review; [totalMatched] is how many the query hit overall. */
data class MailSearchResult(
    val summaries: List<MailSummary>,
    val totalMatched: Int,
)

sealed class GmailError(message: String) : Exception(message) {
    class Auth : GmailError(
        "Sign-in was rejected. Check the address and app password — the account also needs " +
            "2-Step Verification turned on for app passwords to work.",
    )
    class Network : GmailError("Couldn't reach Gmail. Check your internet connection and try again.")
    class Other(message: String) : GmailError(message)
}

/**
 * Talks to imap.gmail.com:993 over TLS with an app password.
 *
 * [search] runs a Gmail search (the IMAP `X-GM-RAW` extension, so the query is ordinary Gmail
 * search syntax) against "All Mail" and returns lightweight summaries. [moveToTrash] relocates
 * messages by UID into Gmail's Trash — never a permanent expunge; Gmail keeps Trash for 30 days.
 */
class GmailImapClient {

    suspend fun testConnection(address: String, appPassword: String) = withContext(Dispatchers.IO) {
        connect(address, appPassword).close()
    }

    suspend fun search(
        address: String,
        appPassword: String,
        rawGmailQuery: String,
        limit: Int = 1000,
    ): MailSearchResult = withContext(Dispatchers.IO) {
        val store = connect(address, appPassword)
        try {
            val allMail = openFolder(store, "\\All", "[Gmail]/All Mail", Folder.READ_ONLY)
            try {
                val allUids = rawUidSearch(allMail, rawGmailQuery)
                val total = allUids.size
                val uids = allUids.take(limit)
                if (uids.isEmpty()) return@withContext MailSearchResult(emptyList(), total)

                val messages = (allMail as UIDFolder)
                    .getMessagesByUID(uids.toLongArray())
                    .filterNotNull()
                    .toTypedArray()

                // one round trip for envelope + size instead of one per message
                allMail.fetch(
                    messages,
                    FetchProfile().apply {
                        add(FetchProfile.Item.ENVELOPE)
                        add(FetchProfile.Item.SIZE)
                        add(UIDFolder.FetchProfileItem.UID)
                    },
                )

                val summaries = messages.map { msg ->
                    MailSummary(
                        uid = runCatching { (allMail as UIDFolder).getUID(msg) }.getOrDefault(-1L),
                        subject = msg.safeSubject(),
                        from = msg.safeFrom(),
                        sizeBytes = runCatching { msg.size.toLong() }.getOrDefault(0L).coerceAtLeast(0L),
                        dateMillis = runCatching { msg.sentDate?.time ?: msg.receivedDate?.time ?: 0L }
                            .getOrDefault(0L),
                    )
                }.filter { it.uid > 0 }
                MailSearchResult(summaries, total)
            } finally {
                runCatching { allMail.close(false) }
            }
        } catch (e: AuthenticationFailedException) {
            throw GmailError.Auth()
        } catch (e: MessagingException) {
            throw e.toGmailError()
        } finally {
            runCatching { store.close() }
        }
    }

    /**
     * `UID SEARCH X-GM-RAW "<query>"` via the low-level protocol API — the Android build of
     * javax.mail doesn't ship GmailRawSearchTerm.
     */
    private fun rawUidSearch(folder: IMAPFolder, query: String): List<Long> {
        @Suppress("UNCHECKED_CAST")
        return folder.doCommand { protocol: IMAPProtocol ->
            val args = Argument().apply {
                writeAtom("X-GM-RAW")
                writeString(query)
            }
            val responses: Array<Response> = protocol.command("UID SEARCH", args)
            val uids = ArrayList<Long>()
            for (r in responses) {
                if (r is IMAPResponse && r.keyEquals("SEARCH")) {
                    while (true) {
                        val n = r.readLong()
                        if (n == -1L) break
                        uids.add(n)
                    }
                }
            }
            protocol.notifyResponseHandlers(responses)
            protocol.handleResult(responses[responses.size - 1])
            uids
        } as List<Long>
    }

    /** Returns how many messages were actually moved. */
    suspend fun moveToTrash(address: String, appPassword: String, uids: List<Long>): Int =
        withContext(Dispatchers.IO) {
            if (uids.isEmpty()) return@withContext 0
            val store = connect(address, appPassword)
            try {
                val allMail = openFolder(store, "\\All", "[Gmail]/All Mail", Folder.READ_WRITE)
                try {
                    val trash = resolveFolder(store, "\\Trash", "[Gmail]/Trash")
                    var moved = 0
                    // Chunked so one huge UID COPY doesn't blow the IMAP command-length limit.
                    for (chunk in uids.chunked(MOVE_CHUNK)) {
                        val messages = (allMail as UIDFolder)
                            .getMessagesByUID(chunk.toLongArray())
                            .filterNotNull()
                            .toTypedArray()
                        if (messages.isEmpty()) continue
                        // Adding the Trash label removes the message from All Mail on Gmail;
                        // the flag + expunge is belt-and-suspenders and tolerated if already gone.
                        allMail.copyMessages(messages, trash)
                        runCatching {
                            allMail.setFlags(messages, Flags(Flags.Flag.DELETED), true)
                            allMail.expunge()
                        }
                        moved += messages.size
                    }
                    moved
                } finally {
                    runCatching { allMail.close(true) }
                }
            } catch (e: AuthenticationFailedException) {
                throw GmailError.Auth()
            } catch (e: MessagingException) {
                throw e.toGmailError()
            } finally {
                runCatching { store.close() }
            }
        }

    // --- internals ------------------------------------------------------

    private fun connect(address: String, appPassword: String): Store {
        val props = Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.host", HOST)
            put("mail.imaps.port", PORT.toString())
            put("mail.imaps.ssl.enable", "true")
            put("mail.imaps.ssl.protocols", "TLSv1.2 TLSv1.3")
            put("mail.imaps.ssl.checkserveridentity", "true")
            put("mail.imaps.connectiontimeout", "15000")
            put("mail.imaps.timeout", "30000")
            put("mail.imaps.writetimeout", "30000")
        }
        val session = Session.getInstance(props)
        val store = session.getStore("imaps")
        try {
            store.connect(HOST, PORT, address, appPassword)
        } catch (e: AuthenticationFailedException) {
            throw GmailError.Auth()
        } catch (e: MessagingException) {
            throw e.toGmailError()
        }
        return store
    }

    private fun resolveFolder(store: Store, specialUse: String, fallbackName: String): IMAPFolder {
        val byAttr = runCatching {
            store.defaultFolder.list("*").firstOrNull { folder ->
                (folder as? IMAPFolder)?.attributes?.any { it.equals(specialUse, ignoreCase = true) } == true
            } as? IMAPFolder
        }.getOrNull()

        val folder = byAttr ?: (store.getFolder(fallbackName) as IMAPFolder)
        if (!folder.exists()) throw GmailError.Other("Gmail folder \"$fallbackName\" not found.")
        return folder
    }

    private fun openFolder(store: Store, specialUse: String, fallbackName: String, mode: Int): IMAPFolder =
        resolveFolder(store, specialUse, fallbackName).apply { open(mode) }

    private fun Message.safeSubject(): String =
        runCatching { subject?.trim() }.getOrNull()?.takeIf { it.isNotEmpty() } ?: "(no subject)"

    private fun Message.safeFrom(): String = runCatching {
        (from?.firstOrNull() as? InternetAddress)?.let { it.personal ?: it.address }
    }.getOrNull() ?: "(unknown sender)"

    private fun MessagingException.toGmailError(): GmailError {
        val msg = message.orEmpty().lowercase()
        return when {
            "connect" in msg || "timeout" in msg || "unknownhost" in msg || "network" in msg ->
                GmailError.Network()
            else -> GmailError.Other(message ?: "Gmail request failed.")
        }
    }

    private companion object {
        const val HOST = "imap.gmail.com"
        const val PORT = 993
        const val MOVE_CHUNK = 200
    }
}
