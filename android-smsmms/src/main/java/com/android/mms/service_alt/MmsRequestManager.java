/*
 * Copyright (C) 2015 Jacob Klinker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.mms.service_alt;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.Telephony;
import android.util.Log; import timber.log.Timber;  // inserted with sed

import com.android.mms.transaction.NotificationTransaction;
import com.google.android.mms.MmsException;
import com.google.android.mms.pdu_alt.PduParser;
import com.google.android.mms.pdu_alt.PduPersister;
import com.google.android.mms.pdu_alt.RetrieveConf;
import com.google.android.mms.util_alt.SqliteWrapper;
import com.klinker.android.send_message.Settings;

public class MmsRequestManager implements MmsRequest.RequestManager {

    private static final String TAG = "MmsRequestManager";

    private Context context;
    private byte[] pduData;

    public MmsRequestManager(Context context) {
        this(context, null);
    }

    public MmsRequestManager(Context context, byte[] pduData) {
        this.context = context;
        this.pduData = pduData;
    }

    @Override
    public void addSimRequest(MmsRequest request) {
        // Do nothing, this will not be invoked ever.
    }

    @Override
    public boolean getAutoPersistingPref() {
        return NotificationTransaction.allowAutoDownload(context);
    }

    @Override
    public byte[] readPduFromContentUri(Uri contentUri, int maxSize) {
        return pduData;
    }

    @Override
    public boolean writePduToContentUri(Uri contentUri, byte[] response) {
        if (response == null || response.length < 1) {
            Timber.e("empty response");
            return false;
        }
        try {
            // Parse M-Retrieve.conf
            RetrieveConf retrieveConf = (RetrieveConf) new PduParser(response).parse();
            if (null == retrieveConf) {
                throw new MmsException("Invalid M-Retrieve.conf PDU.");
            }

            Uri msgUri;
            boolean group;
            int subId = Settings.DEFAULT_SUBSCRIPTION_ID;

            try {
                group = com.klinker.android.send_message.Transaction.settings.getGroup();
                subId = com.klinker.android.send_message.Transaction.settings.getSubscriptionId();
            } catch (Exception e) {
                group = PreferenceManager.getDefaultSharedPreferences(context).getBoolean("group_message", true);
            }

            // Store M-Retrieve.conf into Inbox
            PduPersister persister = PduPersister.getPduPersister(context);
            msgUri = persister.persist(retrieveConf, Telephony.Mms.Inbox.CONTENT_URI, true,
                    group, null, subId);

            // Use local time instead of PDU time
            ContentValues values = new ContentValues();
            values.put(Telephony.Mms.DATE, System.currentTimeMillis() / 1000L);
            values.put(Telephony.Mms.MESSAGE_SIZE, response.length);
            try {
                // Store PDU time as sent time for received message
                values.put(Telephony.Mms.DATE_SENT, retrieveConf.getDate());
            } catch (Exception e) {
            }

            SqliteWrapper.update(context, context.getContentResolver(),
                    msgUri, values, null, null);

            // Send ACK to the Proxy-Relay to indicate we have fetched the
            // MM successfully.
            // Don't mark the transaction as failed if we failed to send it.
            // sendAcknowledgeInd(retrieveConf);
        } catch (Throwable t) {
            Timber.e(TAG, "error", t);
        }

        return false;
    }
}
