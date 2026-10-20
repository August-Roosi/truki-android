/*
 * Copyright 2026 Truki
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.truki.wear

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

class TrukiFolderExtrasTest {

  private fun ByteArrayOutputStream.varint(value: Long) {
    var v = value
    while (v and 0x7FL.inv() != 0L) {
      write(((v and 0x7F) or 0x80).toInt())
      v = v ushr 7
    }
    write(v.toInt())
  }

  private fun ByteArrayOutputStream.tag(field: Int, wireType: Int) = varint(((field shl 3) or wireType).toLong())

  private fun ByteArrayOutputStream.fixed32(value: Int) {
    for (i in 0 until 4) write((value ushr (8 * i)) and 0xFF)
  }

  @Test
  fun `absent bytes give unset extras`() {
    assertEquals(TrukiFolderExtras(), TrukiFolderExtras.parse(null))
    assertEquals(TrukiFolderExtras(), TrukiFolderExtras.parse(ByteArray(0)))
  }

  @Test
  fun `reads all three Truki fields`() {
    val emoji = "💼".toByteArray(Charsets.UTF_8)
    val bytes = ByteArrayOutputStream().apply {
      tag(20001, 2)
      varint(emoji.size.toLong())
      write(emoji)
      tag(20002, 5)
      fixed32(0xff3a7d44.toInt())
      tag(20003, 0)
      varint(1)
    }.toByteArray()

    assertEquals(TrukiFolderExtras(emoji = "💼", color = 0xff3a7d44.toInt(), hideFromAllChats = true), TrukiFolderExtras.parse(bytes))
  }

  @Test
  fun `skips unrelated unknown fields of every wire type`() {
    val bytes = ByteArrayOutputStream().apply {
      tag(30000, 0)
      varint(300)
      tag(30001, 1)
      write(ByteArray(8))
      tag(30002, 2)
      varint(3)
      write(byteArrayOf(1, 2, 3))
      tag(30003, 5)
      fixed32(7)
      tag(20003, 0)
      varint(1)
    }.toByteArray()

    assertEquals(TrukiFolderExtras(hideFromAllChats = true), TrukiFolderExtras.parse(bytes))
  }

  @Test
  fun `color zero means unset`() {
    val bytes = ByteArrayOutputStream().apply {
      tag(20002, 5)
      fixed32(0)
    }.toByteArray()

    assertEquals(TrukiFolderExtras(), TrukiFolderExtras.parse(bytes))
  }

  @Test
  fun `truncated bytes do not throw`() {
    val bytes = ByteArrayOutputStream().apply {
      tag(20001, 2)
      varint(10)
      write(byteArrayOf(1, 2))
    }.toByteArray()

    assertEquals(TrukiFolderExtras(), TrukiFolderExtras.parse(bytes))
  }
}
