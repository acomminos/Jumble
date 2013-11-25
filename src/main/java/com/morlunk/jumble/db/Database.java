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

package com.morlunk.jumble.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Jumble's database is desinged to hold server-specific state information, such as comment blobs.
 * Actual server objects should be stored in the client's DB implementation.
 * Created by andrew on 20/07/13.
 */
public class Database extends SQLiteOpenHelper {

    public static final int DATABASE_VERSION = 1;
    public static final String DATABASE_NAME = "jumble.db";

    public Database(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `comments` (`who` TEXT, `comment` BLOB, `seen` DATE)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `comments_comment` ON `comments`(`who`, `comment`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `comments_seen` ON `comments`(`seen`)");

        db.execSQL("CREATE TABLE IF NOT EXISTS `blobs` (`hash` TEXT, `data` BLOB, `seen` DATE)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `blobs_hash` ON `blobs`(`hash`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `blobs_seen` ON `blobs`(`seen`)");

        db.execSQL("CREATE TABLE IF NOT EXISTS `tokens` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `server` INTEGER, `token` TEXT)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `tokens_host_port` ON `tokens`(`server`)");

        db.execSQL("CREATE TABLE IF NOT EXISTS `friends` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `name` TEXT, `hash` TEXT)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `friends_name` ON `friends`(`name`)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `friends_hash` ON `friends`(`hash`)");

        db.execSQL("CREATE TABLE IF NOT EXISTS `ignored` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `hash` TEXT)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `ignored_hash` ON `ignored`(`hash`)");

        db.execSQL("CREATE TABLE IF NOT EXISTS `muted` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `hash` TEXT)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `muted_hash` ON `muted`(`hash`)");

        db.execSQL("CREATE TABLE IF NOT EXISTS `hidden` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `hash` TEXT)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `hidden_hash` ON `hidden`(`hash`)");

        db.execSQL("CREATE TABLE IF NOT EXISTS `pingcache` (`id` INTEGER PRIMARY KEY AUTOINCREMENT, `hostname` TEXT, `port` INTEGER, `ping` INTEGER)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `pingcache_host_port` ON `pingcache`(`hostname`,`port`)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Nothing, yet!
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        // Clean out old blobs
        db.execSQL("DELETE FROM `comments` WHERE `seen` < datetime('now', '-1 years')");
        db.execSQL("DELETE FROM `blobs` WHERE `seen` < datetime('now', '-1 months')");
    }

    public void setTokens(long server, List<String> tokens) {
        getWritableDatabase().delete("tokens", "server=?", new String[] { Long.toString(server) });

        ContentValues values = new ContentValues();
        values.put("server", server);
        for(String token : tokens) {
            values.put("token", token);
            getWritableDatabase().insert("tokens", null, values);
        }
    }

    public List<String> getTokens(long server) {
        Cursor cursor = getReadableDatabase().query("tokens", new String[] { "token" }, "server=?", new String[] { Long.toString(server) }, null, null, null);
        cursor.moveToFirst();
        List<String> tokens = new ArrayList<String>();
        while(!cursor.isAfterLast()) {
            tokens.add(cursor.getString(0));
            cursor.moveToNext();
        }
        cursor.close();
        return tokens;
    }
}
