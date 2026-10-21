/*
 * Copyright 2026 Truki
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.truki.wear

/**
 * Truki Desktop's Space settings, carried as extra fields on Signal's synced
 * ChatFolderRecord (see truki-desktop protos/SignalStorage.proto):
 *
 * ```
 * optional string emoji    = 20001;
 * fixed32 color            = 20002;  // 0xAARRGGBB, 0 means unset
 * bool hideFromAllChats    = 20003;
 * optional string generalName = 20004;  // General's custom name; Signal's own
 *                                       // ALL-folder name must stay empty
 * ```
 *
 * Signal-Android does not know these fields, but keeps a folder record's
 * unknown fields as raw bytes in ChatFolderRecord.storageServiceProto. This
 * reads them back out of those bytes.
 */
data class TrukiFolderExtras(
  val emoji: String? = null,
  /** 0xAARRGGBB, or null when unset. */
  val color: Int? = null,
  val hideFromAllChats: Boolean = false,
  /** General's (the All chats folder's) custom name, if renamed in Truki Desktop. */
  val generalName: String? = null
) {
  companion object {
    private const val FIELD_EMOJI = 20001
    private const val FIELD_COLOR = 20002
    private const val FIELD_HIDE_FROM_ALL_CHATS = 20003
    private const val FIELD_GENERAL_NAME = 20004

    private const val WIRE_VARINT = 0
    private const val WIRE_FIXED64 = 1
    private const val WIRE_LENGTH_DELIMITED = 2
    private const val WIRE_FIXED32 = 5

    /** Never throws: malformed or absent bytes give default (unset) extras. */
    fun parse(bytes: ByteArray?): TrukiFolderExtras {
      if (bytes == null || bytes.isEmpty()) return TrukiFolderExtras()
      return try {
        Reader(bytes).read()
      } catch (e: IndexOutOfBoundsException) {
        TrukiFolderExtras()
      } catch (e: IllegalStateException) {
        TrukiFolderExtras()
      }
    }
  }

  private class Reader(private val bytes: ByteArray) {
    private var pos = 0

    fun read(): TrukiFolderExtras {
      var extras = TrukiFolderExtras()
      while (pos < bytes.size) {
        val tag = readVarint()
        val field = (tag ushr 3).toInt()
        when (val wireType = (tag and 0x7).toInt()) {
          WIRE_VARINT -> {
            val value = readVarint()
            if (field == FIELD_HIDE_FROM_ALL_CHATS) extras = extras.copy(hideFromAllChats = value != 0L)
          }
          WIRE_FIXED32 -> {
            val value = readFixed32()
            if (field == FIELD_COLOR) extras = extras.copy(color = value.takeIf { it != 0 })
          }
          WIRE_LENGTH_DELIMITED -> {
            val length = readVarint().toInt()
            check(length >= 0 && pos + length <= bytes.size) { "bad length" }
            when (field) {
              FIELD_EMOJI -> extras = extras.copy(emoji = String(bytes, pos, length, Charsets.UTF_8).ifEmpty { null })
              FIELD_GENERAL_NAME -> extras = extras.copy(generalName = String(bytes, pos, length, Charsets.UTF_8).ifEmpty { null })
            }
            pos += length
          }
          WIRE_FIXED64 -> pos += 8
          else -> error("unsupported wire type $wireType")
        }
      }
      return extras
    }

    private fun readVarint(): Long {
      var result = 0L
      var shift = 0
      while (true) {
        val b = bytes[pos++].toInt()
        result = result or ((b and 0x7F).toLong() shl shift)
        if (b and 0x80 == 0) return result
        shift += 7
        check(shift < 64) { "varint too long" }
      }
    }

    private fun readFixed32(): Int {
      val value = (bytes[pos].toInt() and 0xFF) or
        ((bytes[pos + 1].toInt() and 0xFF) shl 8) or
        ((bytes[pos + 2].toInt() and 0xFF) shl 16) or
        ((bytes[pos + 3].toInt() and 0xFF) shl 24)
      pos += 4
      return value
    }
  }
}
