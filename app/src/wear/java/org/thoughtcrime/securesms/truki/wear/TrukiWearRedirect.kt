/*
 * Copyright 2026 Truki
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.truki.wear

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.conversation.v2.ConversationActivity

/**
 * Sends upstream's phone screens to the watch UI: [MainActivity] (where
 * upstream lands after linking) and [ConversationActivity] (where upstream's
 * notifications point). Done from the outside so no upstream code changes.
 */
object TrukiWearRedirect : Application.ActivityLifecycleCallbacks {

  override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
    val threadId: Long? = when (activity) {
      is ConversationActivity -> readThreadId(activity) ?: return
      is MainActivity -> null
      else -> return
    }
    activity.startActivity(TrukiWearActivity.intent(activity, threadId))
    activity.finish()
  }

  private fun readThreadId(activity: Activity): Long? {
    val intent = activity.intent ?: return null
    val extras = intent.extras ?: return null
    if (!ConversationIntents.isConversationIntent(intent)) return null
    return ConversationIntents.readArgsFromBundle(extras).threadId.takeIf { it > 0 }
  }

  override fun onActivityStarted(activity: Activity) = Unit
  override fun onActivityResumed(activity: Activity) = Unit
  override fun onActivityPaused(activity: Activity) = Unit
  override fun onActivityStopped(activity: Activity) = Unit
  override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
  override fun onActivityDestroyed(activity: Activity) = Unit
}

/** Registers [TrukiWearRedirect] at process start. Holds no data. */
class TrukiWearInitProvider : ContentProvider() {
  override fun onCreate(): Boolean {
    (context?.applicationContext as? Application)?.registerActivityLifecycleCallbacks(TrukiWearRedirect)
    return true
  }

  override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
  override fun getType(uri: Uri): String? = null
  override fun insert(uri: Uri, values: ContentValues?): Uri? = null
  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
  override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
