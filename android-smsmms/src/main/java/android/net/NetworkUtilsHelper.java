/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.net;

import timber.log.Timber; import android.util.Log; import static com.klinker.android.timberworkarounds.TimberExtensionsKt.Timber_isLoggable; // inserted with sed

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;

/**
 * Native methods for managing network interfaces.
 * <p/>
 * {@hide}
 */
public class NetworkUtilsHelper {
    /**
     * Bring the named network interface up.
     */
    public native static int enableInterface(String interfaceName);

    /**
     * Bring the named network interface down.
     */
    public native static int disableInterface(String interfaceName);

    /**
     * Setting bit 0 indicates reseting of IPv4 addresses required
     */
    public static final int RESET_IPV4_ADDRESSES = 0x01;

    /**
     * Setting bit 1 indicates reseting of IPv4 addresses required
     */
    public static final int RESET_IPV6_ADDRESSES = 0x02;

    /**
     * Reset all addresses
     */
    public static final int RESET_ALL_ADDRESSES = RESET_IPV4_ADDRESSES | RESET_IPV6_ADDRESSES;

    /**
     * Trim leading zeros from IPv4 address strings
     * Our base libraries will interpret that as octel..
     * Must leave non v4 addresses and host names alone.
     * For example, 192.168.000.010 -> 192.168.0.10
     * TODO - fix base libraries and remove this function
     *
     * @param addr a string representing an ip addr
     * @return a string propertly trimmed
     */
    public static String trimV4AddrZeros(String addr) {
        if (addr == null) return null;
        String[] octets = addr.split("\\.");
        if (octets.length != 4) return addr;
        StringBuilder builder = new StringBuilder(16);
        String result = null;
        for (int i = 0; i < 4; i++) {
            try {
                if (octets[i].length() > 3) return addr;
                builder.append(Integer.parseInt(octets[i]));
            } catch (NumberFormatException e) {
                return addr;
            }
            if (i < 3) builder.append('.');
        }
        result = builder.toString();
        return result;
    }

}
