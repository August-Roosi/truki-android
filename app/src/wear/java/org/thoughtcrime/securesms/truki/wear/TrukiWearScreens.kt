/*
 * Copyright 2026 Truki
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.truki.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.CommunicationActions

private val QUICK_REPLIES = listOf("👍", "OK", "On my way", "Call you soon")

/** Black background keeps the OLED panel mostly dark, which is the cheapest thing the screen can show. */
@Composable
fun TrukiWearTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = darkColorScheme(
      background = Color.Black,
      surface = Color(0xFF1B1B1D),
      primary = Color(0xFF4C8CFF)
    )
  ) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
      content()
    }
  }
}

@Composable
fun TrukiWearChatList(onOpen: (Long) -> Unit) {
  val context = LocalContext.current
  val chats by remember { TrukiWearRepository.chats(context) }.collectAsState(initial = null)
  val listState = rememberLazyListState()

  RoundList(listState = listState) {
    item {
      Text(
        text = stringResource(R.string.truki_wear_app_name),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
      )
    }

    val loaded = chats
    if (loaded != null && loaded.isEmpty()) {
      item {
        Text(
          text = stringResource(R.string.truki_wear_no_chats),
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth()
        )
      }
    }

    items(loaded.orEmpty(), key = { it.threadId }) { chat ->
      ChatRow(chat = chat, onClick = { onOpen(chat.threadId) })
    }
  }
}

@Composable
private fun ChatRow(chat: TrukiWearRepository.Chat, onClick: () -> Unit) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 3.dp)
      .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 8.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(modifier = Modifier.weight(1f)) {
        Text(
          text = chat.name,
          fontWeight = if (chat.unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = chat.snippet,
          fontSize = 12.sp,
          color = Color.LightGray,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(top = 18.dp)
        )
      }
      if (chat.unreadCount > 0) {
        Text(
          text = chat.unreadCount.toString(),
          fontSize = 12.sp,
          color = Color.Black,
          modifier = Modifier
            .padding(start = 6.dp)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 1.dp)
        )
      }
    }
  }
}

@Composable
fun TrukiWearThread(threadId: Long) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val messages by remember(threadId) { TrukiWearRepository.messages(context, threadId) }.collectAsState(initial = emptyList())
  var recipient by remember(threadId) { mutableStateOf<Recipient?>(null) }
  val listState = rememberLazyListState()
  var draft by remember { mutableStateOf("") }

  LaunchedEffect(threadId) {
    recipient = TrukiWearRepository.recipient(threadId)
  }

  LaunchedEffect(messages.size) {
    TrukiWearRepository.markRead(threadId)
    if (messages.isNotEmpty()) {
      listState.scrollToItem(messages.size)
    }
  }

  fun send(text: String) {
    val body = text.trim()
    if (body.isEmpty()) return
    scope.launch { TrukiWearRepository.send(context, threadId, body) }
  }

  RoundList(listState = listState) {
    item {
      val current = recipient
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
      ) {
        Text(
          text = current?.getDisplayName(context).orEmpty(),
          style = MaterialTheme.typography.titleSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false)
        )
        if (current != null && current.isIndividual && !current.isSelf) {
          Text(
            text = "📞",
            modifier = Modifier
              .padding(start = 8.dp)
              .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50))
              .clickable {
                val activity = context as? FragmentActivity ?: return@clickable
                CommunicationActions.startVoiceCall(activity, current) {
                  Toast.makeText(activity, R.string.truki_wear_in_another_call, Toast.LENGTH_SHORT).show()
                }
              }
              .padding(horizontal = 8.dp, vertical = 4.dp)
          )
        }
      }
    }

    items(messages, key = { it.id }) { message ->
      MessageBubble(message)
    }

    item {
      Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
      ) {
        QUICK_REPLIES.take(2).forEach { reply -> QuickReply(reply, Modifier.weight(1f)) { send(reply) } }
      }
      Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
      ) {
        QUICK_REPLIES.drop(2).forEach { reply -> QuickReply(reply, Modifier.weight(1f)) { send(reply) } }
      }
    }

    item {
      OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        placeholder = { Text(stringResource(R.string.truki_wear_reply_hint)) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = {
          send(draft)
          draft = ""
        }),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
      )
    }
  }
}

@Composable
private fun MessageBubble(message: TrukiWearRepository.Message) {
  Box(
    contentAlignment = if (message.isOutgoing) Alignment.CenterEnd else Alignment.CenterStart,
    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
  ) {
    Box(
      modifier = Modifier
        .background(
          if (message.isOutgoing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
          RoundedCornerShape(14.dp)
        )
        .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
      Text(
        text = if (message.author != null) "${message.author}: ${message.body}" else message.body,
        fontSize = 14.sp,
        color = if (message.isOutgoing) Color.Black else Color.White
      )
    }
  }
}

@Composable
private fun QuickReply(text: String, modifier: Modifier, onClick: () -> Unit) {
  Text(
    text = text,
    fontSize = 12.sp,
    textAlign = TextAlign.Center,
    maxLines = 1,
    modifier = modifier
      .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(50))
      .clickable(onClick = onClick)
      .padding(vertical = 6.dp)
  )
}

/**
 * A column sized for a round screen, scrollable with the bezel. The side and
 * end padding keep content inside the circle.
 */
@Composable
private fun RoundList(listState: LazyListState, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
  val scope = rememberCoroutineScope()
  val focusRequester = remember { FocusRequester() }

  LazyColumn(
    state = listState,
    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 48.dp),
    modifier = Modifier
      .fillMaxSize()
      .onRotaryScrollEvent {
        scope.launch { listState.scrollBy(it.verticalScrollPixels) }
        true
      }
      .focusRequester(focusRequester)
      .focusable(),
    content = content
  )

  LaunchedEffect(Unit) { focusRequester.requestFocus() }
}
