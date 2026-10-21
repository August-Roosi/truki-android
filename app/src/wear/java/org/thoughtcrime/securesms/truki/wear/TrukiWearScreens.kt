/*
 * Copyright 2026 Truki
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.truki.wear

import android.Manifest
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonColors
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.Dialog
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.dynamicColorScheme
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.input.RemoteInputIntentHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.CommunicationActions
import java.text.DateFormat
import java.util.Date
import org.signal.core.ui.R as CoreUiR

private const val REPLY_KEY = "truki_wear_reply"

/** Signal's default reaction set. */
private val REACTIONS = listOf("❤️", "👍", "👎", "😂", "😮", "😢")

/** Follows the watch's system colours when it has them, so the app looks native. */
@Composable
fun TrukiWearTheme(content: @Composable () -> Unit) {
  val context = LocalContext.current
  val scheme = remember { dynamicColorScheme(context) }
  if (scheme != null) {
    MaterialTheme(colorScheme = scheme, content = content)
  } else {
    MaterialTheme(content = content)
  }
}

/**
 * The system reply screen (voice, keyboard, emoji), with quick replies as its
 * suggestions. The typed text comes back through [RemoteInput].
 */
private fun replyIntent(context: Context, choices: Array<CharSequence>): Intent {
  val input = RemoteInput.Builder(REPLY_KEY)
    .setLabel(context.getString(R.string.truki_wear_reply))
    .setChoices(choices)
    .build()
  return RemoteInputIntentHelper.createActionRemoteInputIntent().also {
    RemoteInputIntentHelper.putRemoteInputsExtra(it, listOf(input))
  }
}

private fun replyText(data: Intent?): String? {
  return data?.let { RemoteInput.getResultsFromIntent(it) }?.getCharSequence(REPLY_KEY)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
}

// ---- Chats: one page per chat folder; swipe sideways to change folder ----

@Composable
fun TrukiWearChatsScreen(onOpenThread: (Long) -> Unit, onOpenFolders: () -> Unit, onNewChat: () -> Unit) {
  val context = LocalContext.current
  val folders by remember { TrukiWearRepository.folders(context) }.collectAsState(initial = null)
  val loaded = folders ?: return
  if (loaded.isEmpty()) return

  val initialPage = remember(loaded.size) {
    loaded.indexOfFirst { it.id == TrukiWearRepository.lastFolderId(context) }.coerceAtLeast(0)
  }
  val pagerState = rememberPagerState(initialPage = initialPage) { loaded.size }

  // Coming back from the folder list: jump to the folder picked there.
  LaunchedEffect(Unit) {
    val picked = loaded.indexOfFirst { it.id == TrukiWearRepository.lastFolderId(context) }
    if (picked >= 0 && picked != pagerState.currentPage) pagerState.scrollToPage(picked)
  }

  LaunchedEffect(pagerState.currentPage, loaded) {
    loaded.getOrNull(pagerState.currentPage)?.let { TrukiWearRepository.selectFolder(context, it.id) }
  }

  HorizontalPagerScaffold(pagerState = pagerState) {
    HorizontalPager(state = pagerState, key = { loaded[it].id }) { page ->
      FolderChatsPage(
        folder = loaded[page],
        onOpenThread = onOpenThread,
        onOpenFolders = onOpenFolders,
        onNewChat = onNewChat
      )
    }
  }
}

@Composable
private fun FolderChatsPage(
  folder: TrukiWearRepository.Folder,
  onOpenThread: (Long) -> Unit,
  onOpenFolders: () -> Unit,
  onNewChat: () -> Unit
) {
  val context = LocalContext.current
  val chats by remember(folder.id) { TrukiWearRepository.chatsIn(context, folder.id) }.collectAsState(initial = null)
  val listState = rememberTransformingLazyColumnState()
  val spec = rememberTransformationSpec()

  ScreenScaffold(
    scrollState = listState,
    edgeButton = {
      EdgeButton(onClick = onNewChat, buttonSize = EdgeButtonSize.Small) {
        Icon(painterResource(CoreUiR.drawable.symbol_edit_24), contentDescription = stringResource(R.string.truki_wear_new_chat))
      }
    }
  ) { contentPadding ->
    TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
      item {
        CompactButton(
          onClick = onOpenFolders,
          icon = { FolderIcon(folder) },
          label = { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
          colors = folderButtonColors(folder, selected = true),
          modifier = Modifier.transformedHeight(this, spec),
          transformation = SurfaceTransformation(spec)
        )
      }

      val loaded = chats
      if (loaded != null && loaded.isEmpty()) {
        item {
          Text(
            text = stringResource(R.string.truki_wear_no_chats),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).transformedHeight(this, spec)
          )
        }
      }

      items(loaded.orEmpty(), key = { it.threadId }) { chat ->
        ChatButton(
          chat = chat,
          onClick = { onOpenThread(chat.threadId) },
          modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
          transformation = SurfaceTransformation(spec)
        )
      }
    }
  }
}

@Composable
private fun ChatButton(chat: TrukiWearRepository.Chat, onClick: () -> Unit, modifier: Modifier, transformation: SurfaceTransformation) {
  val unread = chat.unreadCount > 0
  Button(
    onClick = onClick,
    modifier = modifier,
    transformation = transformation,
    colors = if (unread) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
    icon = {
      AvatarImage(
        recipientId = chat.recipientId,
        modifier = Modifier.size(ButtonDefaults.LargeIconSize).clip(CircleShape)
      )
    },
    label = {
      Text(
        text = chat.name,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.weight(1f)
      )
      if (unread) {
        Text(text = chat.unreadCount.toString(), fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
      }
    },
    secondaryLabel = {
      Text(text = chat.snippet, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  )
}

@Composable
fun TrukiWearFoldersScreen(onPicked: () -> Unit) {
  val context = LocalContext.current
  val folders by remember { TrukiWearRepository.folders(context) }.collectAsState(initial = emptyList())
  val listState = rememberTransformingLazyColumnState()
  val spec = rememberTransformationSpec()
  val currentId = remember { TrukiWearRepository.lastFolderId(context) }

  ScreenScaffold(scrollState = listState) { contentPadding ->
    TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
      item {
        ListHeader(modifier = Modifier.transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
          Text(stringResource(R.string.truki_wear_folders))
        }
      }
      items(folders, key = { it.id }) { folder ->
        val selected = folder.id == currentId
        Button(
          onClick = {
            TrukiWearRepository.selectFolder(context, folder.id)
            onPicked()
          },
          modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
          transformation = SurfaceTransformation(spec),
          colors = folderButtonColors(folder, selected),
          icon = { FolderIcon(folder) },
          label = { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
          secondaryLabel = if (folder.unreadCount > 0) {
            { Text(stringResource(R.string.truki_wear_unread_count, folder.unreadCount)) }
          } else {
            null
          }
        )
      }
    }
  }
}

/** A Truki Space's emoji when it has one, otherwise the folder symbol. */
@Composable
private fun FolderIcon(folder: TrukiWearRepository.Folder) {
  if (folder.emoji != null) {
    Text(text = folder.emoji, style = MaterialTheme.typography.titleMedium)
  } else {
    Icon(painterResource(R.drawable.symbol_folder_24), contentDescription = null)
  }
}

/** The selected folder is filled with its Truki Space colour (or the theme's), others are tonal. */
@Composable
private fun folderButtonColors(folder: TrukiWearRepository.Folder, selected: Boolean): ButtonColors {
  val accent = folder.color?.let { Color(it) }
  return when {
    selected && accent != null -> ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White, iconColor = Color.White)
    selected -> ButtonDefaults.buttonColors()
    else -> ButtonDefaults.filledTonalButtonColors()
  }
}

// ---- A chat: messages, and a second page (swipe from the right) for call and voice ----

@Composable
fun TrukiWearThreadScreen(threadId: Long, onRecordVoice: () -> Unit) {
  val context = LocalContext.current
  var recipient by remember(threadId) { mutableStateOf<Recipient?>(null) }
  val pagerState = rememberPagerState { 2 }

  LaunchedEffect(threadId) {
    recipient = TrukiWearRepository.recipient(threadId)
  }

  HorizontalPagerScaffold(pagerState = pagerState) {
    HorizontalPager(state = pagerState) { page ->
      if (page == 0) {
        ThreadMessagesPage(threadId = threadId, recipient = recipient)
      } else {
        ThreadActionsPage(recipient = recipient, onRecordVoice = onRecordVoice)
      }
    }
  }
}

@Composable
private fun ThreadMessagesPage(threadId: Long, recipient: Recipient?) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val messages by remember(threadId) { TrukiWearRepository.messages(context, threadId) }.collectAsState(initial = emptyList())
  val listState = rememberTransformingLazyColumnState()
  val spec = rememberTransformationSpec()
  val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
  val quickReplies = remember { context.resources.getTextArray(R.array.truki_wear_quick_replies) }
  var replyingTo by remember { mutableStateOf<TrukiWearRepository.Message?>(null) }
  var reactingTo by remember { mutableStateOf<TrukiWearRepository.Message?>(null) }

  val replyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    val text = replyText(result.data)
    val quoted = replyingTo
    replyingTo = null
    if (text != null) {
      scope.launch { TrukiWearRepository.send(context, threadId, text, replyTo = quoted) }
    }
  }

  LaunchedEffect(messages.size) {
    TrukiWearRepository.markRead(threadId)
    if (messages.isNotEmpty()) {
      listState.scrollToItem(messages.size)
    }
  }

  ScreenScaffold(
    scrollState = listState,
    edgeButton = {
      EdgeButton(
        onClick = {
          replyingTo = null
          replyLauncher.launch(replyIntent(context, quickReplies))
        },
        buttonSize = EdgeButtonSize.Medium
      ) {
        Icon(painterResource(R.drawable.symbol_reply_24), contentDescription = stringResource(R.string.truki_wear_reply))
      }
    }
  ) { contentPadding ->
    TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
      item {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
          modifier = Modifier.fillMaxWidth().transformedHeight(this, spec)
        ) {
          if (recipient != null) {
            AvatarImage(recipient = recipient, modifier = Modifier.size(24.dp).clip(CircleShape))
            Spacer(Modifier.width(6.dp))
          }
          Text(
            text = recipient?.getDisplayName(context).orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      items(messages, key = { it.id }) { message ->
        MessageBubble(
          message = message,
          time = timeFormat.format(Date(message.date)),
          onReply = {
            replyingTo = message
            replyLauncher.launch(replyIntent(context, quickReplies))
          },
          onReact = { reactingTo = message },
          modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
          transformation = SurfaceTransformation(spec)
        )
      }
    }
  }

  ReactionPicker(
    message = reactingTo,
    onDismiss = { reactingTo = null },
    onPick = { message, emoji ->
      reactingTo = null
      scope.launch { TrukiWearRepository.react(context, message.id, emoji) }
    }
  )
}

/**
 * A message. Swipe it to the right to reply to it (quoted, as in Signal);
 * long-press it to react.
 */
@Composable
private fun MessageBubble(
  message: TrukiWearRepository.Message,
  time: String,
  onReply: () -> Unit,
  onReact: () -> Unit,
  modifier: Modifier,
  transformation: SurfaceTransformation
) {
  val replyThreshold = with(LocalDensity.current) { 56.dp.toPx() }
  var offset by remember { mutableFloatStateOf(0f) }

  Box(
    contentAlignment = if (message.isOutgoing) Alignment.CenterEnd else Alignment.CenterStart,
    modifier = modifier.pointerInput(message.id) {
      awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var dragged = 0f
        // Only rightward drags are ours; leftward ones go to the pager (call page).
        val start = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
          if (over > 0) {
            change.consume()
            dragged = over
          }
        }
        if (start != null) {
          horizontalDrag(start.id) { change ->
            dragged = (dragged + change.positionChange().x).coerceAtLeast(0f)
            offset = dragged.coerceAtMost(replyThreshold * 1.5f)
            change.consume()
          }
          if (dragged >= replyThreshold) onReply()
          offset = 0f
        }
      }
    }
  ) {
    if (offset > 0f) {
      Icon(
        painter = painterResource(R.drawable.symbol_reply_24),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary.copy(alpha = (offset / replyThreshold).coerceAtMost(1f)),
        modifier = Modifier.align(Alignment.CenterStart).size(20.dp)
      )
    }
    Card(
      onClick = {},
      onLongClick = onReact,
      onLongClickLabel = stringResource(R.string.truki_wear_react),
      transformation = transformation,
      colors = if (message.isOutgoing) {
        CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
      } else {
        CardDefaults.cardColors()
      },
      modifier = Modifier.fillMaxWidth(0.88f).graphicsLayer { translationX = offset }
    ) {
      if (message.author != null) {
        Text(text = message.author, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
      }
      Text(text = message.body, style = MaterialTheme.typography.bodyMedium)
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (message.reactions.isNotEmpty()) {
          Text(text = message.reactions.distinct().joinToString(""), style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.weight(1f))
        Text(text = time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}

@Composable
private fun ReactionPicker(
  message: TrukiWearRepository.Message?,
  onDismiss: () -> Unit,
  onPick: (TrukiWearRepository.Message, String) -> Unit
) {
  Dialog(visible = message != null, onDismissRequest = onDismiss) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier.fillMaxSize()
    ) {
      REACTIONS.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
          row.forEach { emoji ->
            Box(
              contentAlignment = Alignment.Center,
              modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .clickable { message?.let { onPick(it, emoji) } }
            ) {
              Text(text = emoji, style = MaterialTheme.typography.titleLarge)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ThreadActionsPage(recipient: Recipient?, onRecordVoice: () -> Unit) {
  val context = LocalContext.current
  val canCall = recipient != null && recipient.isIndividual && !recipient.isSelf

  ScreenScaffold { _ ->
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)
    ) {
      if (recipient != null) {
        AvatarImage(recipient = recipient, modifier = Modifier.size(40.dp).clip(CircleShape))
        Text(
          text = recipient.getDisplayName(context),
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
        )
      }
      if (canCall) {
        Button(
          onClick = {
            val activity = context as? FragmentActivity ?: return@Button
            CommunicationActions.startVoiceCall(activity, recipient!!) {
              Toast.makeText(activity, R.string.truki_wear_in_another_call, Toast.LENGTH_SHORT).show()
            }
          },
          icon = { Icon(painterResource(CoreUiR.drawable.symbol_phone_24), contentDescription = null) },
          label = { Text(stringResource(R.string.truki_wear_call)) },
          modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
      }
      Button(
        onClick = onRecordVoice,
        colors = ButtonDefaults.filledTonalButtonColors(),
        icon = { Icon(painterResource(CoreUiR.drawable.symbol_mic_24), contentDescription = null) },
        label = { Text(stringResource(R.string.truki_wear_voice_message)) },
        modifier = Modifier.fillMaxWidth()
      )
    }
  }
}

// ---- Voice message ----

@Composable
fun TrukiWearVoiceScreen(threadId: Long, onDone: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val recorder = remember { TrukiWearVoiceRecorder(context) }
  var recording by remember { mutableStateOf(false) }
  var seconds by remember { mutableIntStateOf(0) }

  fun hasMic() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

  val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    if (granted) {
      recorder.start()
      recording = true
    } else {
      onDone()
    }
  }

  LaunchedEffect(Unit) {
    if (hasMic()) {
      recorder.start()
      recording = true
    } else {
      permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
  }

  LaunchedEffect(recording) {
    while (recording) {
      delay(1000)
      seconds++
    }
  }

  DisposableEffect(Unit) {
    onDispose { if (recording) recorder.cancel() }
  }

  ScreenScaffold { _ ->
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)
    ) {
      Icon(
        painter = painterResource(CoreUiR.drawable.symbol_mic_24),
        contentDescription = null,
        tint = if (recording) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(36.dp)
      )
      Text(
        text = "%d:%02d".format(seconds / 60, seconds % 60),
        style = MaterialTheme.typography.displaySmall,
        modifier = Modifier.padding(vertical = 8.dp)
      )
      Button(
        onClick = {
          recording = false
          recorder.stop { draft ->
            if (draft != null) {
              scope.launch { TrukiWearRepository.sendVoiceNote(context, threadId, draft) }
            }
          }
          onDone()
        },
        enabled = recording,
        icon = { Icon(painterResource(R.drawable.symbol_send_fill_24), contentDescription = null) },
        label = { Text(stringResource(R.string.truki_wear_send)) },
        modifier = Modifier.fillMaxWidth()
      )
      Spacer(Modifier.height(6.dp))
      Button(
        onClick = {
          recording = false
          recorder.cancel()
          onDone()
        },
        colors = ButtonDefaults.filledTonalButtonColors(),
        label = { Text(stringResource(android.R.string.cancel)) },
        modifier = Modifier.fillMaxWidth()
      )
    }
  }
}

// ---- New chat ----

@Composable
fun TrukiWearContactsScreen(onOpenThread: (Long) -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var contacts by remember { mutableStateOf<List<TrukiWearRepository.Contact>?>(null) }
  val listState = rememberTransformingLazyColumnState()
  val spec = rememberTransformationSpec()

  LaunchedEffect(Unit) {
    contacts = TrukiWearRepository.contacts(context)
  }

  ScreenScaffold(scrollState = listState) { contentPadding ->
    TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
      item {
        ListHeader(modifier = Modifier.transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
          Text(stringResource(R.string.truki_wear_new_chat))
        }
      }
      items(contacts.orEmpty(), key = { it.recipientId.toLong() }) { contact ->
        Button(
          onClick = { scope.launch { onOpenThread(TrukiWearRepository.threadFor(contact.recipientId)) } },
          modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
          transformation = SurfaceTransformation(spec),
          colors = ButtonDefaults.filledTonalButtonColors(),
          icon = {
            AvatarImage(
              recipientId = contact.recipientId,
              modifier = Modifier.size(ButtonDefaults.LargeIconSize).clip(CircleShape)
            )
          },
          label = { Text(contact.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        )
      }
    }
  }
}
