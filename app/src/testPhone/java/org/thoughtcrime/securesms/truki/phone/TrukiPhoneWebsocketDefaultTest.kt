/*
 * Copyright 2026 Truki
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.truki.phone

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrukiPhoneWebsocketDefaultTest {

  @Test
  fun `applies the websocket default once`() {
    assertTrue(TrukiPhoneWebsocketDefault.decide(alreadyApplied = false, enabled = false))
    // The user turned "stay connected" off after it was applied: respect it.
    assertFalse(TrukiPhoneWebsocketDefault.decide(alreadyApplied = true, enabled = false))
    // Already on: nothing to do.
    assertFalse(TrukiPhoneWebsocketDefault.decide(alreadyApplied = false, enabled = true))
  }
}
