/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.sip

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.sip.SipAudioCall
import android.net.sip.SipProfile
import android.util.Log

/**
 * Listens for incoming SIP calls, intercepts and hands them off to WalkieTalkieActivity.
 */
class IncomingCallReceiver : BroadcastReceiver() {

    private val tag = "BH_IncomingCallReceiver"

    /**
     * Processes the incoming call, answers it, and hands it over to the
     * WalkieTalkieActivity.
     * @param context The context under which the receiver is running.
     * @param intent The intent being received.
     */
    override fun onReceive(context: Context, intent: Intent) {

        Log.v(tag, ">> onReceive")

        var incomingCall: SipAudioCall? = null
        try {
            val listener: SipAudioCall.Listener = object : SipAudioCall.Listener() {
                override fun onRinging(call: SipAudioCall, caller: SipProfile) {
                    try {
                        call.answerCall(30)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            val wtActivity = context as WalkieTalkieActivity
            incomingCall = wtActivity.manager!!.takeAudioCall(intent, listener)
            incomingCall.answerCall(30)
            incomingCall.startAudio()
            incomingCall.setSpeakerMode(true)
            if (incomingCall.isMuted) {
                incomingCall.toggleMute()
            }
            wtActivity.call = incomingCall
            wtActivity.updateStatus(incomingCall)
        } catch (e: Exception) {
            incomingCall?.close()
        }
    }
}