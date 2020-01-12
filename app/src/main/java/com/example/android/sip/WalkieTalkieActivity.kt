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

import android.app.AlertDialog
import android.app.Dialog
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.net.sip.*
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.view.*
import android.view.View.OnTouchListener
import android.widget.EditText
import android.widget.TextView
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import java.text.ParseException

/**
 * Handles all calling, receiving calls, and UI interaction in the WalkieTalkie
 * app.
 */
class WalkieTalkieActivity : AppCompatActivity(), OnTouchListener {

    private val tag: String = "BH_WalkieTalkieActivity"// 20200112@BH_Lin

    var sipAddress: String? = null
    var manager: SipManager? = null
    var me: SipProfile? = null
    var call: SipAudioCall? = null
    var callReceiver: IncomingCallReceiver? = null
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.v(tag, ">> onCreate");

        setContentView(R.layout.walkietalkie)
        val pushToTalkButton =
            findViewById<View>(R.id.pushToTalk) as ToggleButton
        pushToTalkButton.setOnTouchListener(this)
        // Set up the intent filter. This will be used to fire an
        // IncomingCallReceiver when someone calls the SIP address used by this
        // application.
        val filter = IntentFilter()
        filter.addAction("android.SipDemo.INCOMING_CALL")
        callReceiver = IncomingCallReceiver()
        this.registerReceiver(callReceiver, filter)
        // "Push to talk" can be a serious pain when the screen keeps turning off.
        // Let's prevent that.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        initializeManager()
    }

    public override fun onStart() {
        super.onStart()
        Log.v(tag, ">> onStart");

        // When we get back from the preference setting Activity, assume
        // settings have changed, and re-login with new auth info.
        initializeManager()
    }

    public override fun onDestroy() {
        super.onDestroy()
        Log.v(tag, ">> onDestroy");

        if (call != null) {
            call!!.close()
        }
        closeLocalProfile()
        if (callReceiver != null) {
            unregisterReceiver(callReceiver)
        }
    }

    private fun initializeManager() {
        Log.v(tag, ">> initializeManager");

        if (manager == null) {
            manager = SipManager.newInstance(this)
        }
        initializeLocalProfile()
    }

    /**
     * Logs you into your SIP provider, registering this device as the location to
     * send SIP calls to for your SIP address.
     */
    private fun initializeLocalProfile() {

        Log.v(tag, "+++ initializeLocalProfile +++ ");

        if (manager == null) {
            return
        }
        if (me != null) {
            closeLocalProfile()
        }
        val prefs =
            PreferenceManager.getDefaultSharedPreferences(baseContext)
        val username = prefs.getString("namePref", "")
        val domain = prefs.getString("domainPref", "")
        val password = prefs.getString("passPref", "")
        if (username!!.length == 0 || domain!!.length == 0 || password!!.length == 0) {
            showDialog(UPDATE_SETTINGS_DIALOG)
            return
        }
        try {
            Log.v(tag, "[[ START ]] Build SipProfile");
            val builder = SipProfile.Builder(username, domain)
            builder.setPassword(password)
            me = builder.build()
            val i = Intent()
            i.action = "android.SipDemo.INCOMING_CALL"
            val pi = PendingIntent.getBroadcast(this, 0, i, Intent.FILL_IN_DATA)
            manager!!.open(me, pi, null)
            // This listener must be added AFTER manager.open is called,
            // Otherwise the methods aren't guaranteed to fire.
            manager!!.setRegistrationListener(me?.getUriString(), object : SipRegistrationListener {
                override fun onRegistering(localProfileUri: String) {
                    updateStatus("Registering with SIP Server...")
                }

                override fun onRegistrationDone(
                    localProfileUri: String,
                    expiryTime: Long
                ) {
                    updateStatus("Ready")
                }

                override fun onRegistrationFailed(
                    localProfileUri: String,
                    errorCode: Int,
                    errorMessage: String
                ) {
                    updateStatus("Registration failed.  Please check settings.")
                }
            })
            Log.v(tag, "[[ END ]] Build SipProfile");
        } catch (pe: ParseException) {
            updateStatus("Connection Error.")
        } catch (se: SipException) {
            updateStatus("Connection error.")
        }

        Log.v(tag, "--- initializeLocalProfile ---");
    }

    /**
     * Closes out your local profile, freeing associated objects into memory and
     * unregistering your device from the server.
     */
    private fun closeLocalProfile() {

        Log.v(tag, "+++ closeLocalProfile +++");

        if (manager == null) {
            return
        }
        try {
            if (me != null) {
                manager!!.close(me!!.uriString)
            }
        } catch (ee: Exception) {
            Log.d(
                tag,
                "onDestroy - Failed to close local profile.",
                ee
            )
        }

        Log.v(tag, "--- closeLocalProfile ---");
    }

    /**
     * Make an outgoing call.
     */
    private fun initiateCall() {

        Log.v(tag, ">> initiateCall");

        updateStatus(sipAddress)
        try {
            val listener: SipAudioCall.Listener = object : SipAudioCall.Listener() {
                // Much of the client's interaction with the SIP Stack will
                // happen via listeners. Even making an outgoing call, don't
                // forget to set up a listener to set things up once the call is established.
                override fun onCallEstablished(call: SipAudioCall) {
                    call.startAudio()
                    call.setSpeakerMode(true)
                    call.toggleMute()
                    updateStatus(call)
                }

                override fun onCallEnded(call: SipAudioCall) {
                    updateStatus("Ready.")
                }
            }
            call = manager!!.makeAudioCall(me!!.uriString, sipAddress, listener, 30)
        } catch (e: Exception) {
            Log.i(
                tag,
                "InitiateCall - Error when trying to close manager.",
                e
            )
            if (me != null) {
                try {
                    manager!!.close(me!!.uriString)
                } catch (ee: Exception) {
                    Log.i(
                        tag,
                        "InitiateCall - Error when trying to close manager.",
                        ee
                    )
                    ee.printStackTrace()
                }
            }
            if (call != null) {
                call!!.close()
            }
        }
    }

    /**
     * Updates the status box at the top of the UI with a messege of your choice.
     *
     * @param status The String to display in the status box.
     */
    fun updateStatus(status: String?) { // Be a good citizen. Make sure UI changes fire on the UI thread.

        Log.v(tag, ">> updateStatus: $status");

        runOnUiThread {
            val labelView = findViewById<View>(R.id.sipLabel) as TextView
            labelView.text = status
        }
    }

    /**
     * Updates the status box with the SIP address of the current call.
     *
     * @param call The current, active call.
     */
    fun updateStatus(call: SipAudioCall) {

        var useName = call.peerProfile.displayName
        if (useName == null) {
            useName = call.peerProfile.userName
        }
        updateStatus(useName + "@" + call.peerProfile.sipDomain)
    }

    /**
     * Updates whether or not the user's voice is muted, depending on whether the
     * button is pressed.
     *
     * @param v     The View where the touch event is being fired.
     * @param event The motion to act on.
     * @return boolean Returns false to indicate that the parent view should handle
     * the touch event as it normally would.
     */
    override fun onTouch(v: View, event: MotionEvent): Boolean {

        Log.v(tag, ">> onTouch");

        if (call == null) {
            return false
        } else if (event.action == MotionEvent.ACTION_DOWN && call != null && call!!.isMuted) {
            call!!.toggleMute()
        } else if (event.action == MotionEvent.ACTION_UP && !call!!.isMuted) {
            call!!.toggleMute()
        }
        return false
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {

        Log.v(tag, ">> onCreateOptionsMenu");

        menu.add(0, CALL_ADDRESS, 0, "Call someone")
        menu.add(0, SET_AUTH_INFO, 0, "Edit your SIP Info.")
        menu.add(0, HANG_UP, 0, "End Current Call.")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        Log.v(tag, ">> onOptionsItemSelected");

        when (item.itemId) {
            CALL_ADDRESS -> showDialog(CALL_ADDRESS)
            SET_AUTH_INFO -> updatePreferences()
            HANG_UP -> if (call != null) {
                try {
                    call!!.endCall()
                } catch (se: SipException) {
                    Log.d(
                        tag,
                        "onOptionsItemSelected - Error ending call.",
                        se
                    )
                }
                call!!.close()
            }
        }
        return true
    }

    override fun onCreateDialog(id: Int): Dialog? {

        Log.v(tag, ">> onCreateDialog");

        when (id) {
            CALL_ADDRESS -> {
                val factory = LayoutInflater.from(this)
                val textBoxView: View =
                    factory.inflate(R.layout.call_address_dialog, null)
                return AlertDialog.Builder(this).setTitle("Call Someone.")
                    .setView(textBoxView)
                    .setPositiveButton(
                        android.R.string.ok
                    ) { dialog, whichButton ->
                        val textField =
                            textBoxView.findViewById<View>(R.id.calladdress_edit) as EditText
                        sipAddress = textField.text.toString()
                        initiateCall()
                    }.setNegativeButton(
                        android.R.string.cancel
                    ) { dialog, whichButton ->
                        // Noop.
                    }.create()
            }
            UPDATE_SETTINGS_DIALOG -> return AlertDialog.Builder(
                this
            ).setMessage("Please update your SIP Account Settings.")
                .setPositiveButton(
                    android.R.string.ok
                ) { dialog, whichButton -> updatePreferences() }.setNegativeButton(
                    android.R.string.cancel
                ) { dialog, whichButton ->
                    // Noop.
                }.create()
        }
        return null
    }

    private fun updatePreferences() {

        Log.v(tag, ">> updatePreferences");

        val settingsActivity = Intent(baseContext, SipSettings::class.java)
        startActivity(settingsActivity)
    }

    companion object {
        private const val CALL_ADDRESS = 1
        private const val SET_AUTH_INFO = 2
        private const val UPDATE_SETTINGS_DIALOG = 3
        private const val HANG_UP = 4
    }
}