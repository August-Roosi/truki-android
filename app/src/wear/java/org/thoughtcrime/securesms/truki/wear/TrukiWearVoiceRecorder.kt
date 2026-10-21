/*
 * Copyright 2026 Truki
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.truki.wear

import android.content.Context
import io.reactivex.rxjava3.core.Single
import org.thoughtcrime.securesms.audio.AudioRecorder
import org.thoughtcrime.securesms.components.voice.VoiceNoteDraft

/**
 * One voice message recording on the watch, using upstream's [AudioRecorder]
 * (the same recorder and AAC format as the phone app's voice notes).
 */
class TrukiWearVoiceRecorder(context: Context) {

  private val recorder = AudioRecorder(context.applicationContext, null)
  private var recording: Single<VoiceNoteDraft>? = null

  fun start() {
    recording = recorder.startRecording()
  }

  /** Stops recording; [onDone] gets the finished draft, or null if recording failed. */
  fun stop(onDone: (VoiceNoteDraft?) -> Unit) {
    val single = recording ?: return onDone(null)
    recording = null
    single.subscribe({ onDone(it) }, { onDone(null) })
    recorder.stopRecording()
  }

  fun cancel() {
    if (recording != null) {
      recording = null
      recorder.discardRecording()
    }
  }
}
