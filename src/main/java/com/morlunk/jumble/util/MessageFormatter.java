/*
 * Copyright (C) 2013 Andrew Comminos
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
