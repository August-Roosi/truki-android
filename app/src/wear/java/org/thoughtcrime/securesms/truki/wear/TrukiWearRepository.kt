/*
 * Copyright 2026 Truki
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.truki.wear

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.components.settings.app.chats.folders.ChatFolderRecord
import org.thoughtcrime.securesms.conversationlist.model.ConversationFilter
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.signal.core.util.readToList
import org.signal.core.util.requireObject
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.RecipientTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SettingsValues.ForceWebsocketMode
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.database.model.MessageId
import org.thoughtcrime.securesms.components.voice.VoiceNoteDraft
import org.thoughtcrime.securesms.mms.AudioSlide
import org.thoughtcrime.securesms.mms.OutgoingMessage
import org.thoughtcrime.securesms.mms.QuoteModel
import org.thoughtcrime.securesms.util.MediaUtil
import org.thoughtcrime.securesms.notifications.MarkReadReceiver
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import org.thoughtcrime.securesms.sms.MessageSender
import kotlin.time.Duration.Companion.seconds

/**
 * The watch UI's only door into Signal's core: reads chats, folders and
 * messages from upstream's database and sends through upstream's [MessageSender].
 */
object TrukiWearRepository {

  private const val CHAT_LIMIT = 60L
  private const val MESSAGE_LIMIT = 40L
  private const val PREFS = "truki_wear"
  private const val PREF_FOLDER_ID = "folder_id"

  data class Folder(
    val id: Long,
    val name: String,
    val unreadCount: Int,
    val isAllChats: Boolean,
    /** Truki Desktop's Space icon, if set. */
    val emoji: String? = null,
    /** Truki Desktop's Space accent colour (0xAARRGGBB), if set. */
    val color: Int? = null
  )

  data class Contact(
    val recipientId: RecipientId,
    val name: String
  )

  data class Chat(
    val threadId: Long,
    val recipientId: RecipientId,
    val name: String,
    val snippet: String,
    val unreadCount: Int,
    val date: Long,
    val isPinned: Boolean
  )

  data class Message(
    val id: Long,
    val isOutgoing: Boolean,
    /** Shown above the text in group chats. */
    val author: String?,
    val authorId: RecipientId,
    val body: String,
    val date: Long,
    /** Needed to quote this message in a reply. */
    val dateSent: Long,
    val reactions: List<String>
  )

  private val selectedFolderId = MutableStateFlow<Long?>(null)

  /**
   * A fork cannot receive Signal's FCM pushes, so the watch keeps its own
   * connection open. Uses upstream's existing "stay connected" setting.
   */
  fun ensureWebsocketMode() {
    if (SignalStore.settings.forceWebsocketMode.isEnabled) {
      return
    }
    SignalStore.settings.forceWebsocketMode = ForceWebsocketMode.ENABLED_BY_USER
    AppDependencies.resetNetwork()
    AppDependencies.startNetwork()
  }

  fun selectFolder(context: Context, folderId: Long) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(PREF_FOLDER_ID, folderId).apply()
    selectedFolderId.value = folderId
  }

  fun folders(context: Context): Flow<List<Folder>> {
    return combine(
      changes(
        { AppDependencies.databaseObserver.registerChatFolderObserver(it) },
        { AppDependencies.databaseObserver.registerConversationListObserver(it) }
      ),
      selectedFolderId
    ) { _, _ -> loadFolders(context) }.flowOn(Dispatchers.IO)
  }

  /** The current folder (falls back to All chats) and its chats, pinned first. */
  fun chats(context: Context): Flow<Pair<Folder, List<Chat>>> {
    return combine(
      changes(
        { AppDependencies.databaseObserver.registerChatFolderObserver(it) },
        { AppDependencies.databaseObserver.registerConversationListObserver(it) }
      ),
      selectedFolderId
    ) { _, _ -> loadChats(context) }.flowOn(Dispatchers.IO)
  }

  /** The chats of one folder, pinned first; used by each page of the folder pager. */
  fun chatsIn(context: Context, folderId: Long): Flow<List<Chat>> {
    return changes(
      { AppDependencies.databaseObserver.registerChatFolderObserver(it) },
      { AppDependencies.databaseObserver.registerConversationListObserver(it) }
    ).map {
      val records = SignalDatabase.chatFolders.getCurrentChatFolders()
      val folder = records.firstOrNull { it.id == folderId } ?: return@map emptyList()
      loadChatsFor(context, records, folder)
    }.flowOn(Dispatchers.IO)
  }

  /** The folder last opened, as remembered across restarts. */
  fun lastFolderId(context: Context): Long {
    return selectedFolderId.value ?: context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(PREF_FOLDER_ID, -1L)
  }

  fun messages(context: Context, threadId: Long): Flow<List<Message>> {
    // Reactions are reported as message updates, not conversation changes.
    val reactionChanges = callbackFlow {
      val observer = DatabaseObserver.MessageObserver { trySend(Unit) }
      AppDependencies.databaseObserver.registerMessageUpdateObserver(observer)
      awaitClose { AppDependencies.databaseObserver.unregisterObserver(observer) }
    }
    return merge(
      changes({ AppDependencies.databaseObserver.registerConversationObserver(threadId, it) }),
      reactionChanges
    )
      .conflate()
      .map { loadMessages(context, threadId) }
      .flowOn(Dispatchers.IO)
  }

  /** Everyone you can message on Signal, sorted by name, for starting a new chat. */
  suspend fun contacts(context: Context): List<Contact> = withContext(Dispatchers.IO) {
    SignalDatabase.recipients
      .getSignalContacts(RecipientTable.IncludeSelfMode.Exclude)
      .readToList { it.requireObject(RecipientTable.ID, RecipientId.SERIALIZER) }
      .map { id -> Recipient.resolved(id) }
      .filter { !it.isBlocked }
      .map { Contact(recipientId = it.id, name = it.getDisplayName(context)) }
  }

  /** The chat with this person, created if it does not exist yet. */
  suspend fun threadFor(recipientId: RecipientId): Long = withContext(Dispatchers.IO) {
    SignalDatabase.threads.getOrCreateThreadIdFor(Recipient.resolved(recipientId))
  }

  suspend fun recipient(threadId: Long): Recipient? = withContext(Dispatchers.IO) {
    SignalDatabase.threads.getRecipientForThreadId(threadId)?.resolve()
  }

  suspend fun markRead(threadId: Long) = withContext(Dispatchers.IO) {
    MarkReadReceiver.process(SignalDatabase.threads.setRead(threadId))
    AppDependencies.messageNotifier.updateNotification(AppDependencies.application)
  }

  /** Sends [body], quoting [replyTo] when given (Signal's swipe-to-reply). */
  suspend fun send(context: Context, threadId: Long, body: String, replyTo: Message? = null) = withContext(Dispatchers.IO) {
    val recipient: Recipient = SignalDatabase.threads.getRecipientForThreadId(threadId) ?: return@withContext
    val quote = replyTo?.let {
      QuoteModel(
        id = it.dateSent,
        author = it.authorId,
        text = it.body,
        isOriginalMissing = false,
        attachment = null,
        mentions = emptyList(),
        type = QuoteModel.Type.NORMAL,
        bodyRanges = null
      )
    }
    val message = OutgoingMessage(
      threadRecipient = recipient,
      sentTimeMillis = System.currentTimeMillis(),
      body = body,
      expiresIn = recipient.expiresInSeconds.seconds.inWholeMilliseconds,
      isUrgent = true,
      isSecure = true,
      outgoingQuote = quote
    )
    MessageSender.send(context, message, threadId, MessageSender.SendType.SIGNAL, null, null)
  }

  /** Sends a recorded voice message, as the phone app does (an AAC voice-note slide). */
  suspend fun sendVoiceNote(context: Context, threadId: Long, draft: VoiceNoteDraft) = withContext(Dispatchers.IO) {
    val recipient: Recipient = SignalDatabase.threads.getRecipientForThreadId(threadId) ?: return@withContext
    val slide = AudioSlide(draft.uri, draft.size, MediaUtil.AUDIO_AAC, true)
    val message = OutgoingMessage(
      threadRecipient = recipient,
      sentTimeMillis = System.currentTimeMillis(),
      expiresIn = recipient.expiresInSeconds.seconds.inWholeMilliseconds,
      isUrgent = true,
      isSecure = true,
      attachments = listOf(slide.asAttachment())
    )
    MessageSender.send(context, message, threadId, MessageSender.SendType.SIGNAL, null, null)
  }

  suspend fun react(context: Context, messageId: Long, emoji: String) = withContext(Dispatchers.IO) {
    MessageSender.sendNewReaction(context, MessageId(messageId), emoji)
  }

  private fun currentFolderRecords(context: Context): Pair<List<ChatFolderRecord>, ChatFolderRecord> {
    val records = SignalDatabase.chatFolders.getCurrentChatFolders()
    val wanted = selectedFolderId.value
      ?: context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(PREF_FOLDER_ID, -1L).also { selectedFolderId.value = it }
    val current = records.firstOrNull { it.id == wanted }
      ?: records.firstOrNull { it.folderType == ChatFolderRecord.FolderType.ALL }
      ?: ChatFolderRecord(folderType = ChatFolderRecord.FolderType.ALL)
    return records to current
  }

  private fun ChatFolderRecord.toFolder(context: Context, unreadCount: Int): Folder {
    val isAll = folderType == ChatFolderRecord.FolderType.ALL
    val extras = TrukiFolderExtras.parse(storageServiceProto)
    return Folder(
      id = id,
      // Truki Desktop can rename General (the All chats folder). The name is in
      // generalName; older Truki versions put it in Signal's own name field.
      name = if (isAll) {
        extras.generalName ?: name.ifBlank { context.getString(R.string.ChatFoldersFragment__all_chats) }
      } else {
        name
      },
      unreadCount = unreadCount,
      isAllChats = isAll,
      emoji = extras.emoji,
      color = extras.color
    )
  }

  /**
   * Threads claimed by a Truki Space marked "hide from General". Uses
   * membership only: the folder's unread/muted display filters are ignored,
   * matching Truki Desktop, so a read or muted chat stays hidden too.
   */
  private fun threadsHiddenFromAllChats(records: List<ChatFolderRecord>): Set<Long> {
    val threads = SignalDatabase.threads
    return records
      .filter { it.folderType != ChatFolderRecord.FolderType.ALL && TrukiFolderExtras.parse(it.storageServiceProto).hideFromAllChats }
      .flatMap { record ->
        val membership = record.copy(showUnread = false, showMutedChats = true)
        listOf(true, false).flatMap { pinned ->
          threads.getUnarchivedConversationList(ConversationFilter.OFF, pinned, 0, Long.MAX_VALUE, membership).use { cursor ->
            threads.readerFor(cursor).use { reader -> generateSequence { reader.getNext() }.map { it.threadId }.toList() }
          }
        }
      }
      .toSet()
  }

  private fun loadFolders(context: Context): List<Folder> {
    val (records, _) = currentFolderRecords(context)
    val counts = SignalDatabase.chatFolders.getUnreadCountAndEmptyAndMutedStatusForFolders(records)
    return records.map { it.toFolder(context, counts[it.id]?.first ?: 0) }
  }

  private fun loadChats(context: Context): Pair<Folder, List<Chat>> {
    val (records, folder) = currentFolderRecords(context)
    return folder.toFolder(context, 0) to loadChatsFor(context, records, folder)
  }

  private fun loadChatsFor(context: Context, records: List<ChatFolderRecord>, folder: ChatFolderRecord): List<Chat> {
    val threads = SignalDatabase.threads
    val pinned = readChats(context, threads.getUnarchivedConversationList(ConversationFilter.OFF, true, 0, CHAT_LIMIT, folder), isPinned = true)
    val others = readChats(context, threads.getUnarchivedConversationList(ConversationFilter.OFF, false, 0, CHAT_LIMIT, folder), isPinned = false)
    var chats = pinned + others
    if (folder.folderType == ChatFolderRecord.FolderType.ALL) {
      val hidden = threadsHiddenFromAllChats(records)
      if (hidden.isNotEmpty()) chats = chats.filter { it.threadId !in hidden }
    }
    return chats
  }

  private fun readChats(context: Context, cursor: android.database.Cursor, isPinned: Boolean): List<Chat> {
    return SignalDatabase.threads.readerFor(cursor).use { reader ->
      generateSequence { reader.getNext() }
        .map { thread ->
          Chat(
            threadId = thread.threadId,
            recipientId = thread.recipient.id,
            name = thread.recipient.getDisplayName(context),
            snippet = thread.body,
            unreadCount = thread.unreadCount,
            date = thread.date,
            isPinned = isPinned
          )
        }
        .toList()
    }
  }

  private fun loadMessages(context: Context, threadId: Long): List<Message> {
    val isGroup = SignalDatabase.threads.getRecipientForThreadId(threadId)?.isGroup == true
    val cursor = SignalDatabase.messages.getConversation(threadId, offset = 0, limit = MESSAGE_LIMIT)
    val records = MessageTable.mmsReaderFor(cursor).use { reader -> generateSequence { reader.getNext() }.toList() }
    // The conversation query carries no reactions; upstream attaches them separately too.
    val reactions = SignalDatabase.reactions.getReactionsForMessages(records.map { it.id })
    return records
      .map { record ->
        Message(
          id = record.id,
          isOutgoing = record.isOutgoing,
          author = if (isGroup && !record.isOutgoing) record.fromRecipient.getShortDisplayName(context) else null,
          authorId = if (record.isOutgoing) Recipient.self().id else record.fromRecipient.id,
          body = record.getDisplayBody(context).toString().ifEmpty { "📎" },
          date = record.dateReceived,
          dateSent = record.dateSent,
          reactions = reactions[record.id].orEmpty().map { it.emoji }
        )
      }
      .reversed()
  }

  /** Emits once immediately, then again (conflated) whenever upstream reports a change. */
  private fun changes(vararg registers: (DatabaseObserver.Observer) -> Unit): Flow<Unit> {
    return callbackFlow {
      val observer = DatabaseObserver.Observer { trySend(Unit) }
      trySend(Unit)
      registers.forEach { it(observer) }
      awaitClose { AppDependencies.databaseObserver.unregisterObserver(observer) }
    }.conflate()
  }
}
