/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.internal.net;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;
import com.android.internal.util.Preconditions;

import java.util.List;

/**
 * A simple container used to carry information in VpnBuilder, VpnDialogs,
 * and com.android.server.connectivity.Vpn. Internal use only.
 *
 * @hide
 */
public class VpnConfig implements Parcelable {

    public static final String SERVICE_INTERFACE = "android.net.VpnService";

    public static final String DIALOGS_PACKAGE = "com.android.vpndialogs";

    public static final String LEGACY_VPN = "[Legacy VPN]";

    public static Intent getIntentForConfirmation() {
        Intent intent = new Intent();
        intent.setClassName(DIALOGS_PACKAGE, DIALOGS_PACKAGE + ".ConfirmDialog");
        return intent;
    }

    public static PendingIntent getIntentForStatusPanel(Context context, VpnConfig config) {
        Preconditions.checkNotNull(config);

        Intent intent = new Intent();
        intent.setClassName(DIALOGS_PACKAGE, DIALOGS_PACKAGE + ".ManageDialog");
        intent.putExtra("config", config);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY |
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_MUTABLE);
    }

    public String user;
    public String interfaze;
    public String session;
    public int mtu = -1;
    public String addresses;
    public String routes;
    public List<String> dnsServers;
    public List<String> searchDomains;
    public PendingIntent configureIntent;
    public long startTime = -1;
    public boolean legacy;

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(user);
        out.writeString(interfaze);
        out.writeString(session);
        out.writeInt(mtu);
        out.writeString(addresses);
        out.writeString(routes);
        out.writeStringList(dnsServers);
        out.writeStringList(searchDomains);
        out.writeParcelable(configureIntent, flags);
        out.writeLong(startTime);
        out.writeInt(legacy ? 1 : 0);
    }

    public static final Parcelable.Creator<VpnConfig> CREATOR =
            new Parcelable.Creator<VpnConfig>() {
                @Override
                public VpnConfig createFromParcel(Parcel in) {
                    VpnConfig config = new VpnConfig();
                    config.user = in.readString();
                    config.interfaze = in.readString();
                    config.session = in.readString();
                    config.mtu = in.readInt();
                    config.addresses = in.readString();
                    config.routes = in.readString();
                    config.dnsServers = in.createStringArrayList();
                    config.searchDomains = in.createStringArrayList();
                    config.configureIntent = in.readParcelable(null);
                    config.startTime = in.readLong();
                    config.legacy = in.readInt() != 0;
                    return config;
                }

                @Override
                public VpnConfig[] newArray(int size) {
                    return new VpnConfig[size];
                }
            };
}
