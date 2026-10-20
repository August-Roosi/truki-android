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
import org.signal.registration.RegistrationActivity
import org.signal.registration.RegistrationRoute
import org.thoughtcrime.securesms.MainActivity
import org.thoughtcrime.securesms.conversation.ConversationIntents
import org.thoughtcrime.securesms.conversation.v2.ConversationActivity

/**
 * Sends upstream's phone screens to the watch UI: [MainActivity] (where
 * upstream lands after linking) and [ConversationActivity] (where upstream's
 * notifications point). Done from the outside so no upstream code changes.
 */
object TrukiWearRedirect : Application.ActivityLifecycleCallbacks {

  /** Mirrors the private key in [RegistrationActivity.createIntent]. */
  private const val REGISTRATION_START_DESTINATION_EXTRA = "start_destination"

  /**
   * A watch must only ever *link*. Registering a phone number here would make
   * the watch the primary device and log out the phone, and the captcha step
   * needs a WebView, which Wear OS does not have. So any registration flow
   * that has no explicit destination opens straight on the link QR screen.
   */
  override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
    if (activity !is RegistrationActivity) return
    val intent = activity.intent ?: return
    if (!intent.hasExtra(REGISTRATION_START_DESTINATION_EXTRA)) {
      intent.putExtra(REGISTRATION_START_DESTINATION_EXTRA, RegistrationRoute.LinkAccount(showCreateAccount = false))
    }
  }

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
