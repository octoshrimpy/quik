/*
 * Copyright (C) 2007 Esmertec AG.
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.mms.dom.smil;

import java.util.ArrayList;

import org.w3c.dom.smil.Time;
import org.w3c.dom.smil.TimeList;

public class TimeListImpl implements TimeList {
    private final ArrayList<Time> mTimes;

    /*
     * Internal Interface
     */
    TimeListImpl(ArrayList<Time> times) {
        mTimes = times;
    }

    /*
     * TimeList Interface
     */

    public int getLength() {
        return mTimes.size();
    }

    public Time item(int index) {
        Time time = null;
        try {
            time = mTimes.get(index);
        } catch (IndexOutOfBoundsException e) {
            // Do nothing and return null
        }
        return time;
    }

}
