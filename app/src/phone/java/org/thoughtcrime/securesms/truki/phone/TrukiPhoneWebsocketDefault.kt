/*
 * Copyright 2026 Truki
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.truki.phone

import android.content.Context
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.dependencies.AppDependencies
import org.thoughtcrime.securesms.keyvalue.SettingsValues.ForceWebsocketMode
import org.thoughtcrime.securesms.keyvalue.SignalStore

/**
 * Truki cannot receive Signal's Google push (FCM is bound to Signal's app ID),
 * so the phone build turns on upstream's "stay connected" once per install.
 * A user who later switches it off is left alone.
 */
object TrukiPhoneWebsocketDefault {

  private val TAG = Log.tag(TrukiPhoneWebsocketDefault::class.java)
  private const val PREFS = "truki_phone"
  private const val PREF_APPLIED = "websocket_default_applied"

  /** True when "stay connected" should be switched on now. */
  fun decide(alreadyApplied: Boolean, enabled: Boolean): Boolean = !alreadyApplied && !enabled

  /**
   * Must run after app init (SignalStore exists). Waits until the account is
   * registered, so the network is started with credentials and a setting
   * restored from a backup is overridden once.
   *
   * @return true once the default has been dealt with for this install.
   */
  fun apply(context: Context): Boolean {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    val applied = prefs.getBoolean(PREF_APPLIED, false)
    if (applied) return true
    if (!SignalStore.account.isRegistered) return false

    if (decide(applied, SignalStore.settings.forceWebsocketMode.isEnabled)) {
      Log.i(TAG, "Turning on stay connected (no Google push for this build)")
      SignalStore.settings.forceWebsocketMode = ForceWebsocketMode.ENABLED_BY_USER
      AppDependencies.resetNetwork()
      AppDependencies.startNetwork()
    }
    prefs.edit().putBoolean(PREF_APPLIED, true).apply()
    return true
  }
}
