/*
 * Copyright 2026 Truki
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.truki.wear

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import org.thoughtcrime.securesms.PassphraseRequiredActivity

/**
 * The watch's launcher. [PassphraseRequiredActivity] routes an unlinked watch
 * into upstream's registration flow, which [TrukiWearRedirect] pins to "Link".
 */
class TrukiWearActivity : PassphraseRequiredActivity() {

  companion object {
    private const val EXTRA_THREAD_ID = "truki_wear_thread_id"

    private const val ROUTE_CHATS = "chats"
    private const val ROUTE_FOLDERS = "folders"
    private const val ROUTE_CONTACTS = "contacts"
    private const val ROUTE_THREAD = "thread/{threadId}"
    private const val ROUTE_VOICE = "voice/{threadId}"

    fun intent(context: Context, threadId: Long?): Intent {
      return Intent(context, TrukiWearActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (threadId != null) putExtra(EXTRA_THREAD_ID, threadId)
      }
    }
  }

  /** A thread a notification asked to open; consumed once navigated to. */
  private val pendingThreadId = mutableStateOf<Long?>(null)

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    TrukiWearRepository.ensureWebsocketMode()
    if (savedInstanceState == null) {
      pendingThreadId.value = readThreadId(intent)
    }

    setContent {
      TrukiWearTheme {
        AppScaffold {
          val navController = rememberSwipeDismissableNavController()

          LaunchedEffect(pendingThreadId.value) {
            val threadId = pendingThreadId.value ?: return@LaunchedEffect
            pendingThreadId.value = null
            navController.popBackStack(ROUTE_CHATS, inclusive = false)
            navController.navigate("thread/$threadId")
          }

          SwipeDismissableNavHost(navController = navController, startDestination = ROUTE_CHATS) {
            composable(ROUTE_CHATS) {
              TrukiWearChatsScreen(
                onOpenThread = { navController.navigate("thread/$it") },
                onOpenFolders = { navController.navigate(ROUTE_FOLDERS) },
                onNewChat = { navController.navigate(ROUTE_CONTACTS) }
              )
            }
            composable(ROUTE_CONTACTS) {
              TrukiWearContactsScreen(
                onOpenThread = { threadId ->
                  navController.navigate("thread/$threadId") { popUpTo(ROUTE_CHATS) }
                }
              )
            }
            composable(ROUTE_FOLDERS) {
              TrukiWearFoldersScreen(onPicked = { navController.popBackStack() })
            }
            composable(ROUTE_THREAD, arguments = listOf(navArgument("threadId") { type = NavType.LongType })) { entry ->
              val threadId = entry.arguments?.getLong("threadId") ?: return@composable
              TrukiWearThreadScreen(
                threadId = threadId,
                onRecordVoice = { navController.navigate("voice/$threadId") }
              )
            }
            composable(ROUTE_VOICE, arguments = listOf(navArgument("threadId") { type = NavType.LongType })) { entry ->
              val threadId = entry.arguments?.getLong("threadId") ?: return@composable
              TrukiWearVoiceScreen(threadId = threadId, onDone = { navController.popBackStack() })
            }
          }
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    readThreadId(intent)?.let { pendingThreadId.value = it }
  }

  private fun readThreadId(intent: Intent?): Long? {
    return intent?.getLongExtra(EXTRA_THREAD_ID, -1)?.takeIf { it > 0 }
  }
}
