/*
 * Copyright (C) 2013 Andrew Comminos
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

package com.morlunk.jumble;

import android.os.Parcel;
import android.os.Parcelable;
import com.morlunk.jumble.model.Server;

/**
 * Parameters for connection to a Mumble server through the Jumble service.
 */
public class JumbleParams implements Parcelable {

    // Connection
    public Server server;

    // General
    public boolean showChatNotifications = true;
    public boolean autoReconnect = true;

    // Authentication
    public String certificatePath;
    public String certificatePassword;

    // Audio
    public int detectionThreshold = 1400;
    public boolean usePushToTalk = false;
    public boolean useOpus = true;

    // Network
    public boolean forceTcp = false;

    // Extra
    public String clientName = "Plumble";

    public static final Parcelable.Creator<JumbleParams> CREATOR = new Parcelable.Creator<JumbleParams>() {

        @Override
        public JumbleParams createFromParcel(Parcel parcel) {
            return new JumbleParams(parcel);
        }

        @Override
        public JumbleParams[] newArray(int i) {
            return new JumbleParams[i];
        }
    };

    public JumbleParams() {

    }

    private JumbleParams(Parcel in) {
        readFromParcel(in);
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeParcelable(server, 0);
        out.writeValue(showChatNotifications);
        out.writeValue(autoReconnect);
        out.writeString(certificatePath);
        out.writeString(certificatePassword);
        out.writeInt(detectionThreshold);
        out.writeValue(usePushToTalk);
        out.writeValue(useOpus);
        out.writeValue(forceTcp);
        out.writeValue(clientName);
    }

    public void readFromParcel(Parcel in) {
        server = in.readParcelable(Server.class.getClassLoader());
        showChatNotifications = (Boolean)in.readValue(Boolean.class.getClassLoader());
        autoReconnect = (Boolean)in.readValue(Boolean.class.getClassLoader());
        certificatePath = in.readString();
        certificatePassword = in.readString();
        detectionThreshold = in.readInt();
        usePushToTalk = (Boolean)in.readValue(Boolean.class.getClassLoader());
        useOpus = (Boolean)in.readValue(Boolean.class.getClassLoader());
        forceTcp = (Boolean)in.readValue(Boolean.class.getClassLoader());
        clientName = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

}
