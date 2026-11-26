/*
 * Copyright 2026 Truki
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.truki.phone

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.thoughtcrime.securesms.keyvalue.SettingsValues.ForceWebsocketMode

class TrukiPhoneWebsocketDefaultTest {

  @Test
  fun `applies the websocket default once`() {
    assertTrue(TrukiPhoneWebsocketDefault.decide(alreadyApplied = false, mode = ForceWebsocketMode.DISABLED_AUTOMATICALLY))
    // The user turned "stay connected" off after it was applied: respect it.
    assertFalse(TrukiPhoneWebsocketDefault.decide(alreadyApplied = true, mode = ForceWebsocketMode.DISABLED_BY_USER))
    // Already on by the user: nothing to do.
    assertFalse(TrukiPhoneWebsocketDefault.decide(alreadyApplied = false, mode = ForceWebsocketMode.ENABLED_BY_USER))
  }

  @Test
  fun `automatic stay connected is made the user's choice`() {
    // FcmRefreshJob turns ENABLED_AUTOMATICALLY off whenever it gets a token.
    assertTrue(TrukiPhoneWebsocketDefault.decide(alreadyApplied = false, mode = ForceWebsocketMode.ENABLED_AUTOMATICALLY))
  }
}
