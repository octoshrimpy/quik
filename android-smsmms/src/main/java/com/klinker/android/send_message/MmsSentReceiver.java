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

package com.klinker.android.send_message;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Telephony;

import com.google.android.mms.util_alt.SqliteWrapper;
import timber.log.Timber;

import java.io.File;

public class MmsSentReceiver extends StatusUpdatedReceiver {
    public static final String MMS_SENT = "com.klinker.android.messaging.MMS_SENT";
    public static final String EXTRA_CONTENT_URI = "content_uri";
    public static final String EXTRA_FILE_PATH = "file_path";

    @Override
    public void updateInInternalDatabase(Context context, Intent intent, int resultCode) {
        Timber.v("MMS has finished sending, marking it as so, in the database");

        Uri uri = Uri.parse(intent.getStringExtra(EXTRA_CONTENT_URI));
        Timber.v(uri.toString());

        ContentValues values = new ContentValues(1);
        values.put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_SENT);
        SqliteWrapper.update(context, context.getContentResolver(), uri, values,
                null, null);

        String filePath = intent.getStringExtra(EXTRA_FILE_PATH);
        Timber.v(filePath);
        new File(filePath).delete();
    }

    @Override
    public void onMessageStatusUpdated(Context context, Intent intent, int resultCode) {

    }

}
