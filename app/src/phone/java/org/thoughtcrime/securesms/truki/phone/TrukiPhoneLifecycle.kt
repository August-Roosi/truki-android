/*
 * Copyright 2026 Truki
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.truki.phone

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * Activities are only created after ApplicationContext.onCreate, so
 * SignalStore exists here. Unregisters itself once the default is applied.
 */
object TrukiPhoneLifecycle : Application.ActivityLifecycleCallbacks {

  override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
    if (TrukiPhoneWebsocketDefault.apply(activity)) {
      activity.application.unregisterActivityLifecycleCallbacks(this)
    }
  }

  override fun onActivityStarted(activity: Activity) = Unit
  override fun onActivityResumed(activity: Activity) = Unit
  override fun onActivityPaused(activity: Activity) = Unit
  override fun onActivityStopped(activity: Activity) = Unit
  override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
  override fun onActivityDestroyed(activity: Activity) = Unit
}
