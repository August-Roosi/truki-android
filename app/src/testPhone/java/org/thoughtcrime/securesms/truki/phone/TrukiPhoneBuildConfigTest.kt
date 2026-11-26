/*
 * Copyright 2026 Truki
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.truki.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.thoughtcrime.securesms.BuildConfig

class TrukiPhoneBuildConfigTest {

  @Test
  fun `build config identifies the phone flavor`() {
    assertEquals("ee.truki.phone", BuildConfig.APPLICATION_ID)
    assertEquals("phone", BuildConfig.BUILD_DISTRIBUTION_TYPE)
    assertTrue(BuildConfig.MANAGES_APP_UPDATES)
    assertEquals(
      "https://github.com/August-Roosi/truki-android/releases/latest/download/latest.json",
      BuildConfig.APK_UPDATE_MANIFEST_URL
    )
    assertTrue(BuildConfig.LINK_DEVICE_UX_ENABLED)
  }

  @Test
  fun `phone version codes increase across revisions and upstream bumps`() {
    assertTrue(trukiPhoneVersionCode(1768, 100, 2) > trukiPhoneVersionCode(1768, 100, 1))
    assertTrue(trukiPhoneVersionCode(1769, 100, 1) > trukiPhoneVersionCode(1768, 100, 99))
    assertThrows(IllegalArgumentException::class.java) { trukiPhoneVersionCode(1768, 100, 0) }
    assertThrows(IllegalArgumentException::class.java) { trukiPhoneVersionCode(1768, 100, 100) }
  }

  @Test
  fun `versionName carries the truki revision`() {
    assertTrue(BuildConfig.VERSION_NAME, Regex("-truki[1-9][0-9]?$").containsMatchIn(BuildConfig.VERSION_NAME))
  }
}
