/*
 * Copyright 2026 Truki
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.truki.phone

/**
 * The phone flavor's versionCode. app/build.gradle.kts repeats this formula
 * inline (Gradle cannot call app code); keep the two in step.
 *
 * Every Truki release must have a higher code than the last, including two
 * releases of the same upstream version, so the revision fills the slots
 * upstream reserves for hotfixes.
 */
fun trukiPhoneVersionCode(canonical: Int, maxHotfix: Int, revision: Int): Int {
  require(revision in 1 until maxHotfix) { "revision must be in 1..${maxHotfix - 1}, was $revision" }
  return canonical * maxHotfix + revision
}
