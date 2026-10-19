/*
 * Copyright 2026 Truki
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.truki.wear

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.thoughtcrime.securesms.database.DatabaseObserver
import org.thoughtcrime.securesms.database.MessageTable
import org.thoughtcrime.securesms.database.SignalDatabase
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SettingsValues.ForceWebsocketMode
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.messages.IncomingMessageObserver
import org.thoughtcrime.securesms.mms.OutgoingMessage
import org.thoughtcrime.securesms.notifications.MarkReadReceiver
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.sms.MessageSender
import kotlin.time.Duration.Companion.seconds

/**
 * The watch UI's only door into Signal's core: reads chats and messages from
 * upstream's database and sends through upstream's [MessageSender].
 */
object TrukiWearRepository {

  private const val CHAT_LIMIT = 40
  private const val MESSAGE_LIMIT = 30L

  data class Chat(
    val threadId: Long,
    val name: String,
    val snippet: String,
    val unreadCount: Int,
    val date: Long
  )

  data class Message(
    val id: Long,
    val isOutgoing: Boolean,
    val author: String?,
    val body: String,
    val date: Long
  )

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

  fun chats(context: Context): Flow<List<Chat>> {
    return changes { observer -> AppDependencies.databaseObserver.registerConversationListObserver(observer) }
      .map { loadChats(context) }
      .flowOn(Dispatchers.IO)
  }

  fun messages(context: Context, threadId: Long): Flow<List<Message>> {
    return changes { observer -> AppDependencies.databaseObserver.registerConversationObserver(threadId, observer) }
      .map { loadMessages(context, threadId) }
      .flowOn(Dispatchers.IO)
  }

  suspend fun recipient(threadId: Long): Recipient? = withContext(Dispatchers.IO) {
    SignalDatabase.threads.getRecipientForThreadId(threadId)?.resolve()
  }

  suspend fun markRead(threadId: Long) = withContext(Dispatchers.IO) {
    MarkReadReceiver.process(SignalDatabase.threads.setRead(threadId))
    AppDependencies.messageNotifier.updateNotification(AppDependencies.application)
  }

  suspend fun send(context: Context, threadId: Long, body: String) = withContext(Dispatchers.IO) {
    val recipient: Recipient = SignalDatabase.threads.getRecipientForThreadId(threadId) ?: return@withContext
    val message = OutgoingMessage.text(
      threadRecipient = recipient,
      body = body,
      expiresIn = recipient.expiresInSeconds.seconds.inWholeMilliseconds
    )
    MessageSender.send(context, message, threadId, MessageSender.SendType.SIGNAL, null, null)
  }

  private fun loadChats(context: Context): List<Chat> {
    val threads = SignalDatabase.threads
    return threads.readerFor(threads.getRecentConversationList(CHAT_LIMIT, false, true)).use { reader ->
      generateSequence { reader.getNext() }
        .map { thread ->
          Chat(
            threadId = thread.threadId,
            name = thread.recipient.getDisplayName(context),
            snippet = thread.body,
            unreadCount = thread.unreadCount,
            date = thread.date
          )
        }
        .toList()
    }
  }

  private fun loadMessages(context: Context, threadId: Long): List<Message> {
    val cursor = SignalDatabase.messages.getConversation(threadId, offset = 0, limit = MESSAGE_LIMIT)
    return MessageTable.mmsReaderFor(cursor).use { reader ->
      generateSequence { reader.getNext() }
        .map { record ->
          Message(
            id = record.id,
            isOutgoing = record.isOutgoing,
            author = if (record.isOutgoing || !record.fromRecipient.isIndividual) null else record.fromRecipient.getShortDisplayName(context),
            body = record.getDisplayBody(context).toString().ifEmpty { "📎" },
            date = record.dateReceived
          )
        }
        .toList()
        .reversed()
    }
  }

  /** Emits once immediately, then again (conflated) whenever upstream reports a change. */
  private fun changes(register: (DatabaseObserver.Observer) -> Unit): Flow<Unit> {
    return callbackFlow {
      val observer = DatabaseObserver.Observer { trySend(Unit) }
      trySend(Unit)
      register(observer)
      awaitClose { AppDependencies.databaseObserver.unregisterObserver(observer) }
    }.conflate()
  }
}
