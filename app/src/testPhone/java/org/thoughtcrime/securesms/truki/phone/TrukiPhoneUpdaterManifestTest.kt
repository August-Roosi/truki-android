/*
 * Copyright 2026 Truki
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.truki.phone

import android.app.Application
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Upstream's updater needs receivers and permissions that only the website
 * flavor's manifest declares. Without them the phone build never updates.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TrukiPhoneUpdaterManifestTest {

  @Test
  fun `the updater's receivers and permissions are declared`() {
    val context = ApplicationProvider.getApplicationContext<Application>()
    @Suppress("DEPRECATION")
    val info = context.packageManager.getPackageInfo(
      context.packageName,
      PackageManager.GET_RECEIVERS or PackageManager.GET_PERMISSIONS
    )
    val receivers = info.receivers.orEmpty().map { it.name }.toSet()
    val permissions = info.requestedPermissions.orEmpty().toSet()

    for (name in listOf("ApkUpdateRefreshListener", "ApkUpdateDownloadManagerReceiver", "ApkUpdatePackageInstallerReceiver", "ApkUpdateNotificationReceiver")) {
      assertTrue("missing receiver $name", "org.thoughtcrime.securesms.apkupdate.$name" in receivers)
    }
    for (name in listOf("DOWNLOAD_WITHOUT_NOTIFICATION", "REQUEST_INSTALL_PACKAGES", "UPDATE_PACKAGES_WITHOUT_USER_ACTION")) {
      assertTrue("missing permission $name", "android.permission.$name" in permissions)
    }
  }
}
