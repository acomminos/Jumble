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

package com.morlunk.jumble.model;

interface IChannel {
    int getId();
    int getPosition();
    boolean isTemporary();
    IChannel getParent();
    String getName();
    String getDescription();
    byte[] getDescriptionHash();
    List getUsers();
    List getSubchannels();
    List getLinks();
    int getSubchannelUserCount();
}