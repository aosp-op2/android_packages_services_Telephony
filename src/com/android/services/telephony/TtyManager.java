/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.services.telephony;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncResult;
import android.os.Handler;
import android.os.Message;
import android.telecomm.TelecommConstants;
import android.telecomm.TelecommManager;

import com.android.internal.telephony.Phone;

final class TtyManager {
    private final static int MSG_SET_TTY_MODE_RESPONSE = 1;
    private final static int MSG_GET_TTY_MODE_RESPONSE = 2;

    private final TtyBroadcastReceiver mReceiver = new TtyBroadcastReceiver();
    private final Phone mPhone;
    private int mTtyMode;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_SET_TTY_MODE_RESPONSE: {
                    Log.v(TtyManager.this, "got setTtyMode response");
                    AsyncResult ar = (AsyncResult) msg.obj;
                    if (ar.exception != null) {
                        Log.d(TtyManager.this, "setTTYMode exception: %s", ar.exception);
                    }
                    mPhone.queryTTYMode(obtainMessage(MSG_GET_TTY_MODE_RESPONSE));
                    break;
                }
                case MSG_GET_TTY_MODE_RESPONSE: {
                    Log.v(TtyManager.this, "got queryTTYMode response");
                    AsyncResult ar = (AsyncResult) msg.obj;
                    if (ar.exception != null) {
                        Log.d(TtyManager.this, "queryTTYMode exception: %s", ar.exception);
                    } else {
                        int ttyMode = phoneModeToTelecommMode(((int[]) ar.result)[0]);
                        if (ttyMode != mTtyMode) {
                            Log.d(TtyManager.this, "setting TTY mode failed, attempted %d, got: %d",
                                    mTtyMode, ttyMode);
                        } else {
                            Log.d(TtyManager.this, "setting TTY mode to %d succeeded", ttyMode);
                        }
                    }
                    break;
                }
            }
        }
    };

    TtyManager(Context context, Phone phone) {
        mPhone = phone;

        IntentFilter intentFilter = new IntentFilter(
                TelecommConstants.ACTION_CURRENT_TTY_MODE_CHANGED);
        context.registerReceiver(mReceiver, intentFilter);

        int ttyMode = TelecommConstants.TTY_MODE_OFF;
        TelecommManager telecommManager = TelecommManager.from(context);
        if (telecommManager != null) {
            ttyMode = telecommManager.getCurrentTtyMode();
        }
        updateTtyMode(ttyMode);
    }

    private void updateTtyMode(int ttyMode) {
        Log.v(this, "updateTtyMode %d -> %d", mTtyMode, ttyMode);
        mTtyMode = ttyMode;
        mPhone.setTTYMode(telecommModeToPhoneMode(ttyMode),
                mHandler.obtainMessage(MSG_SET_TTY_MODE_RESPONSE));
    }

    private final class TtyBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.v(TtyManager.this, "onReceive, action: %s", action);
            if (action.equals(TelecommConstants.ACTION_CURRENT_TTY_MODE_CHANGED)) {
                int ttyMode = intent.getIntExtra(
                        TelecommConstants.EXTRA_CURRENT_TTY_MODE, TelecommConstants.TTY_MODE_OFF);
                updateTtyMode(ttyMode);
            }
        }
    }

    private static int telecommModeToPhoneMode(int telecommMode) {
        switch (telecommMode) {
            case TelecommConstants.TTY_MODE_FULL:
                return Phone.TTY_MODE_FULL;
            case TelecommConstants.TTY_MODE_VCO:
                return Phone.TTY_MODE_VCO;
            case TelecommConstants.TTY_MODE_HCO:
                return Phone.TTY_MODE_HCO;
            case TelecommConstants.TTY_MODE_OFF:
            default:
                return Phone.TTY_MODE_OFF;
        }
    }

    private static int phoneModeToTelecommMode(int phoneMode) {
        switch (phoneMode) {
            case Phone.TTY_MODE_FULL:
                return TelecommConstants.TTY_MODE_FULL;
            case Phone.TTY_MODE_VCO:
                return TelecommConstants.TTY_MODE_VCO;
            case Phone.TTY_MODE_HCO:
                return TelecommConstants.TTY_MODE_HCO;
            case Phone.TTY_MODE_OFF:
            default:
                return TelecommConstants.TTY_MODE_OFF;
        }
    }
}