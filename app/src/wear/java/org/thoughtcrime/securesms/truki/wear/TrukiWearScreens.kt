/*
 * Copyright 2026 Truki
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.truki.wear

import android.app.RemoteInput
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonColors
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.FilledTonalIconButton
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
import kotlinx.coroutines.launch
import org.thoughtcrime.securesms.R
import org.thoughtcrime.securesms.avatar.AvatarImage
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.util.CommunicationActions
import java.text.DateFormat
import java.util.Date
import org.signal.core.ui.R as CoreUiR

private const val REPLY_KEY = "truki_wear_reply"

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

@Composable
fun TrukiWearChatListScreen(onOpenThread: (Long) -> Unit, onOpenFolders: () -> Unit, onNewChat: () -> Unit) {
  val context = LocalContext.current
  val data by remember { TrukiWearRepository.chats(context) }.collectAsState(initial = null)
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
      val loaded = data
      if (loaded != null) {
        val (folder, chats) = loaded

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

        if (chats.isEmpty()) {
          item {
            Text(
              text = stringResource(R.string.truki_wear_no_chats),
              textAlign = TextAlign.Center,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.fillMaxWidth().padding(top = 12.dp).transformedHeight(this, spec)
            )
          }
        }

        items(chats, key = { it.threadId }) { chat ->
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
  val current by remember { TrukiWearRepository.chats(context) }.collectAsState(initial = null)
  val listState = rememberTransformingLazyColumnState()
  val spec = rememberTransformationSpec()

  ScreenScaffold(scrollState = listState) { contentPadding ->
    TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
      item {
        ListHeader(modifier = Modifier.transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
          Text(stringResource(R.string.truki_wear_folders))
        }
      }
      items(folders, key = { it.id }) { folder ->
        val selected = current?.first?.id == folder.id
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

@Composable
fun TrukiWearThreadScreen(threadId: Long) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val messages by remember(threadId) { TrukiWearRepository.messages(context, threadId) }.collectAsState(initial = emptyList())
  var recipient by remember(threadId) { mutableStateOf<Recipient?>(null) }
  val listState = rememberTransformingLazyColumnState()
  val spec = rememberTransformationSpec()
  val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
  val quickReplies = stringArrayResourceSafe(R.array.truki_wear_quick_replies)

  val replyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    val text = result.data?.let { RemoteInput.getResultsFromIntent(it) }?.getCharSequence(REPLY_KEY)?.toString()?.trim()
    if (!text.isNullOrEmpty()) {
      scope.launch { TrukiWearRepository.send(context, threadId, text) }
    }
  }

  LaunchedEffect(threadId) {
    recipient = TrukiWearRepository.recipient(threadId)
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
          val input = RemoteInput.Builder(REPLY_KEY)
            .setLabel(context.getString(R.string.truki_wear_reply))
            .setChoices(quickReplies)
            .build()
          val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
          RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(input))
          replyLauncher.launch(intent)
        },
        buttonSize = EdgeButtonSize.Medium
      ) {
        Icon(painterResource(R.drawable.symbol_reply_24), contentDescription = stringResource(R.string.truki_wear_reply))
      }
    }
  ) { contentPadding ->
    TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
      item {
        val current = recipient
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.fillMaxWidth().transformedHeight(this, spec)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (current != null) {
              AvatarImage(recipient = current, modifier = Modifier.size(24.dp).clip(CircleShape))
              Spacer(Modifier.width(6.dp))
            }
            Text(
              text = current?.getDisplayName(context).orEmpty(),
              style = MaterialTheme.typography.titleMedium,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
          if (current != null && current.isIndividual && !current.isSelf) {
            FilledTonalIconButton(
              onClick = {
                val activity = context as? FragmentActivity ?: return@FilledTonalIconButton
                CommunicationActions.startVoiceCall(activity, current) {
                  Toast.makeText(activity, R.string.truki_wear_in_another_call, Toast.LENGTH_SHORT).show()
                }
              },
              modifier = Modifier.padding(top = 4.dp)
            ) {
              Icon(painterResource(CoreUiR.drawable.symbol_phone_24), contentDescription = stringResource(R.string.truki_wear_call))
            }
          }
        }
      }

      items(messages, key = { it.id }) { message ->
        Box(
          contentAlignment = if (message.isOutgoing) Alignment.CenterEnd else Alignment.CenterStart,
          modifier = Modifier.fillMaxWidth().transformedHeight(this, spec)
        ) {
          Card(
            onClick = {},
            transformation = SurfaceTransformation(spec),
            colors = if (message.isOutgoing) {
              CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
              )
            } else {
              CardDefaults.cardColors()
            },
            modifier = Modifier.fillMaxWidth(0.88f)
          ) {
            if (message.author != null) {
              Text(
                text = message.author,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
              )
            }
            Text(text = message.body, style = MaterialTheme.typography.bodyMedium)
            Text(
              text = timeFormat.format(Date(message.date)),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.align(Alignment.End)
            )
          }
        }
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

@Composable
private fun stringArrayResourceSafe(id: Int): Array<CharSequence> {
  val context = LocalContext.current
  return remember(id) { context.resources.getTextArray(id) }
}
