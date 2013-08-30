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

package com.morlunk.jumble.util;

/**
 * Formats strings into HTML.
 * Created by andrew on 24/08/13.
 */
public class MessageFormatter {

    public static final String HIGHLIGHT_COLOR = "33b5e5";

    private static final String HTML_FONT_COLOR_FORMAT = "<font color=\"#%s\">%s</font>";

    /**
     * Highlights the passed string using the service's defined color {@link MessageFormatter#HIGHLIGHT_COLOR}.
     * @param string The string to highlight.
     * @return The passed string enclosed with HTML font tags specifying the color.
     */
    public static String highlightString(String string) {
        return String.format(HTML_FONT_COLOR_FORMAT, HIGHLIGHT_COLOR, string);
    }
}
