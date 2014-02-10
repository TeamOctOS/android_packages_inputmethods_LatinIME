/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.inputmethod.keyboard.internal;

import android.text.TextUtils;

import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.utils.CollectionUtils;
import com.android.inputmethod.latin.utils.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

/**
 * The more key specification object. The more keys are an array of {@link MoreKeySpec}.
 *
 * The more keys specification is comma separated "key specification" each of which represents one
 * "more key".
 * The key specification might have label or string resource reference in it. These references are
 * expanded before parsing comma.
 * Special character, comma ',' backslash '\' can be escaped by '\' character.
 * Note that the '\' is also parsed by XML parser and {@link MoreKeySpec#splitKeySpecs(String)}
 * as well.
 */
// TODO: Should extend the key specification object.
public final class MoreKeySpec {
    public final int mCode;
    public final String mLabel;
    public final String mOutputText;
    public final int mIconId;

    public MoreKeySpec(final String moreKeySpec, boolean needsToUpperCase, final Locale locale) {
        if (TextUtils.isEmpty(moreKeySpec)) {
            throw new KeySpecParser.KeySpecParserError("Empty more key spec");
        }
        mLabel = StringUtils.toUpperCaseOfStringForLocale(
                KeySpecParser.getLabel(moreKeySpec), needsToUpperCase, locale);
        final int code = StringUtils.toUpperCaseOfCodeForLocale(
                KeySpecParser.getCode(moreKeySpec), needsToUpperCase, locale);
        if (code == Constants.CODE_UNSPECIFIED) {
            // Some letter, for example German Eszett (U+00DF: "ß"), has multiple characters
            // upper case representation ("SS").
            mCode = Constants.CODE_OUTPUT_TEXT;
            mOutputText = mLabel;
        } else {
            mCode = code;
            mOutputText = StringUtils.toUpperCaseOfStringForLocale(
                    KeySpecParser.getOutputText(moreKeySpec), needsToUpperCase, locale);
        }
        mIconId = KeySpecParser.getIconId(moreKeySpec);
    }

    public Key buildKey(final int x, final int y, final int labelFlags,
            final KeyboardParams params) {
        return new Key(mLabel, mIconId, mCode, mOutputText, null /* hintLabel */, labelFlags,
                Key.BACKGROUND_TYPE_NORMAL, x, y, params.mDefaultKeyWidth, params.mDefaultRowHeight,
                params.mHorizontalGap, params.mVerticalGap);
    }

    @Override
    public int hashCode() {
        int hashCode = 1;
        hashCode = 31 + mCode;
        hashCode = hashCode * 31 + mIconId;
        hashCode = hashCode * 31 + (mLabel == null ? 0 : mLabel.hashCode());
        hashCode = hashCode * 31 + (mOutputText == null ? 0 : mOutputText.hashCode());
        return hashCode;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o instanceof MoreKeySpec) {
            final MoreKeySpec other = (MoreKeySpec)o;
            return mCode == other.mCode
                    && mIconId == other.mIconId
                    && TextUtils.equals(mLabel, other.mLabel)
                    && TextUtils.equals(mOutputText, other.mOutputText);
        }
        return false;
    }

    @Override
    public String toString() {
        final String label = (mIconId == KeyboardIconsSet.ICON_UNDEFINED ? mLabel
                : KeyboardIconsSet.PREFIX_ICON + KeyboardIconsSet.getIconName(mIconId));
        final String output = (mCode == Constants.CODE_OUTPUT_TEXT ? mOutputText
                : Constants.printableCode(mCode));
        if (StringUtils.codePointCount(label) == 1 && label.codePointAt(0) == mCode) {
            return output;
        } else {
            return label + "|" + output;
        }
    }

    private static final boolean DEBUG = LatinImeLogger.sDBG;
    // Constants for parsing.
    private static final char COMMA = Constants.CODE_COMMA;
    private static final char BACKSLASH = Constants.CODE_BACKSLASH;
    private static final String ADDITIONAL_MORE_KEY_MARKER =
            StringUtils.newSingleCodePointString(Constants.CODE_PERCENT);

    /**
     * Split the text containing multiple key specifications separated by commas into an array of
     * key specifications.
     * A key specification can contain a character escaped by the backslash character, including a
     * comma character.
     * Note that an empty key specification will be eliminated from the result array.
     *
     * @param text the text containing multiple key specifications.
     * @return an array of key specification text. Null if the specified <code>text</code> is empty
     * or has no key specifications.
     */
    public static String[] splitKeySpecs(final String text) {
        if (TextUtils.isEmpty(text)) {
            return null;
        }
        final int size = text.length();
        // Optimization for one-letter key specification.
        if (size == 1) {
            return text.charAt(0) == COMMA ? null : new String[] { text };
        }

        ArrayList<String> list = null;
        int start = 0;
        // The characters in question in this loop are COMMA and BACKSLASH. These characters never
        // match any high or low surrogate character. So it is OK to iterate through with char
        // index.
        for (int pos = 0; pos < size; pos++) {
            final char c = text.charAt(pos);
            if (c == COMMA) {
                // Skip empty entry.
                if (pos - start > 0) {
                    if (list == null) {
                        list = CollectionUtils.newArrayList();
                    }
                    list.add(text.substring(start, pos));
                }
                // Skip comma
                start = pos + 1;
            } else if (c == BACKSLASH) {
                // Skip escape character and escaped character.
                pos++;
            }
        }
        final String remain = (size - start > 0) ? text.substring(start) : null;
        if (list == null) {
            return remain != null ? new String[] { remain } : null;
        }
        if (remain != null) {
            list.add(remain);
        }
        return list.toArray(new String[list.size()]);
    }

    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    private static String[] filterOutEmptyString(final String[] array) {
        if (array == null) {
            return EMPTY_STRING_ARRAY;
        }
        ArrayList<String> out = null;
        for (int i = 0; i < array.length; i++) {
            final String entry = array[i];
            if (TextUtils.isEmpty(entry)) {
                if (out == null) {
                    out = CollectionUtils.arrayAsList(array, 0, i);
                }
            } else if (out != null) {
                out.add(entry);
            }
        }
        if (out == null) {
            return array;
        }
        return out.toArray(new String[out.size()]);
    }

    public static String[] insertAdditionalMoreKeys(final String[] moreKeySpecs,
            final String[] additionalMoreKeySpecs) {
        final String[] moreKeys = filterOutEmptyString(moreKeySpecs);
        final String[] additionalMoreKeys = filterOutEmptyString(additionalMoreKeySpecs);
        final int moreKeysCount = moreKeys.length;
        final int additionalCount = additionalMoreKeys.length;
        ArrayList<String> out = null;
        int additionalIndex = 0;
        for (int moreKeyIndex = 0; moreKeyIndex < moreKeysCount; moreKeyIndex++) {
            final String moreKeySpec = moreKeys[moreKeyIndex];
            if (moreKeySpec.equals(ADDITIONAL_MORE_KEY_MARKER)) {
                if (additionalIndex < additionalCount) {
                    // Replace '%' marker with additional more key specification.
                    final String additionalMoreKey = additionalMoreKeys[additionalIndex];
                    if (out != null) {
                        out.add(additionalMoreKey);
                    } else {
                        moreKeys[moreKeyIndex] = additionalMoreKey;
                    }
                    additionalIndex++;
                } else {
                    // Filter out excessive '%' marker.
                    if (out == null) {
                        out = CollectionUtils.arrayAsList(moreKeys, 0, moreKeyIndex);
                    }
                }
            } else {
                if (out != null) {
                    out.add(moreKeySpec);
                }
            }
        }
        if (additionalCount > 0 && additionalIndex == 0) {
            // No '%' marker is found in more keys.
            // Insert all additional more keys to the head of more keys.
            if (DEBUG && out != null) {
                throw new RuntimeException("Internal logic error:"
                        + " moreKeys=" + Arrays.toString(moreKeys)
                        + " additionalMoreKeys=" + Arrays.toString(additionalMoreKeys));
            }
            out = CollectionUtils.arrayAsList(additionalMoreKeys, additionalIndex, additionalCount);
            for (int i = 0; i < moreKeysCount; i++) {
                out.add(moreKeys[i]);
            }
        } else if (additionalIndex < additionalCount) {
            // The number of '%' markers are less than additional more keys.
            // Append remained additional more keys to the tail of more keys.
            if (DEBUG && out != null) {
                throw new RuntimeException("Internal logic error:"
                        + " moreKeys=" + Arrays.toString(moreKeys)
                        + " additionalMoreKeys=" + Arrays.toString(additionalMoreKeys));
            }
            out = CollectionUtils.arrayAsList(moreKeys, 0, moreKeysCount);
            for (int i = additionalIndex; i < additionalCount; i++) {
                out.add(additionalMoreKeys[additionalIndex]);
            }
        }
        if (out == null && moreKeysCount > 0) {
            return moreKeys;
        } else if (out != null && out.size() > 0) {
            return out.toArray(new String[out.size()]);
        } else {
            return null;
        }
    }

    public static int getIntValue(final String[] moreKeys, final String key,
            final int defaultValue) {
        if (moreKeys == null) {
            return defaultValue;
        }
        final int keyLen = key.length();
        boolean foundValue = false;
        int value = defaultValue;
        for (int i = 0; i < moreKeys.length; i++) {
            final String moreKeySpec = moreKeys[i];
            if (moreKeySpec == null || !moreKeySpec.startsWith(key)) {
                continue;
            }
            moreKeys[i] = null;
            try {
                if (!foundValue) {
                    value = Integer.parseInt(moreKeySpec.substring(keyLen));
                    foundValue = true;
                }
            } catch (NumberFormatException e) {
                throw new RuntimeException(
                        "integer should follow after " + key + ": " + moreKeySpec);
            }
        }
        return value;
    }

    public static boolean getBooleanValue(final String[] moreKeys, final String key) {
        if (moreKeys == null) {
            return false;
        }
        boolean value = false;
        for (int i = 0; i < moreKeys.length; i++) {
            final String moreKeySpec = moreKeys[i];
            if (moreKeySpec == null || !moreKeySpec.equals(key)) {
                continue;
            }
            moreKeys[i] = null;
            value = true;
        }
        return value;
    }
}
