/*
 * Copyright 2026 Truki
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.truki.wear

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import org.thoughtcrime.securesms.PassphraseRequiredActivity

/**
 * The watch's launcher. [PassphraseRequiredActivity] routes an unlinked watch
 * into upstream's registration flow, whose welcome screen offers "Link".
 */
class TrukiWearActivity : PassphraseRequiredActivity() {

  companion object {
    private const val EXTRA_THREAD_ID = "truki_wear_thread_id"

    fun intent(context: Context, threadId: Long?): Intent {
      return Intent(context, TrukiWearActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (threadId != null) putExtra(EXTRA_THREAD_ID, threadId)
      }
    }
  }

  private val openThreadId = mutableStateOf<Long?>(null)

  override fun onCreate(savedInstanceState: Bundle?, ready: Boolean) {
    TrukiWearRepository.ensureWebsocketMode()
    openThreadId.value = savedInstanceState?.getLong(EXTRA_THREAD_ID, -1)?.takeIf { it > 0 } ?: readThreadId(intent)

    setContent {
      TrukiWearTheme {
        val threadId = openThreadId.value
        if (threadId == null) {
          TrukiWearChatList(onOpen = { openThreadId.value = it })
        } else {
          BackHandler { openThreadId.value = null }
          TrukiWearThread(threadId = threadId)
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    readThreadId(intent)?.let { openThreadId.value = it }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    openThreadId.value?.let { outState.putLong(EXTRA_THREAD_ID, it) }
  }

  private fun readThreadId(intent: Intent?): Long? {
    return intent?.getLongExtra(EXTRA_THREAD_ID, -1)?.takeIf { it > 0 }
  }
}
