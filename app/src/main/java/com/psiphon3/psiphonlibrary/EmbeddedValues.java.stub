/*
 * Copyright (c) 2012, Psiphon Inc.
 * All rights reserved.
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
 *
 */

package com.psiphon3.psiphonlibrary;

import android.content.Context;
import net.grandcentrix.tray.AppPreferences;
import net.grandcentrix.tray.core.ItemNotFoundException;
import net.grandcentrix.tray.core.SharedPreferencesImport;

public class EmbeddedValues
{
/*[[[cog
import cog
import utils
cog.outl('public static final String CLIENT_VERSION = "%s";' % utils.get_embedded_value(buildname, 'CLIENT_VERSION'))
cog.outl('public static final String EMBEDDED_SERVER_LIST[] = {"%s"};' % utils.get_embedded_value(buildname, 'EMBEDDED_SERVER_LIST'))
cog.outl('public static final boolean IGNORE_NON_EMBEDDED_SERVER_ENTRIES = %s;' % ('true' if utils.get_embedded_value(buildname, 'IGNORE_NON_EMBEDDED_SERVER_ENTRIES') else 'false'))
cog.outl('public static final String FEEDBACK_ENCRYPTION_PUBLIC_KEY = "%s";' % utils.get_embedded_value(buildname, 'FEEDBACK_ENCRYPTION_PUBLIC_KEY'))
cog.outl('public static final String FEEDBACK_DIAGNOSTIC_INFO_UPLOAD_SERVER = "%s";' % utils.get_embedded_value(buildname, 'FEEDBACK_DIAGNOSTIC_INFO_UPLOAD_SERVER'))
cog.outl('public static final String FEEDBACK_DIAGNOSTIC_INFO_UPLOAD_PATH = "%s";' % utils.get_embedded_value(buildname, 'FEEDBACK_DIAGNOSTIC_INFO_UPLOAD_PATH'))
cog.outl('public static final String FEEDBACK_DIAGNOSTIC_INFO_UPLOAD_SERVER_HEADERS = "%s";' % utils.get_embedded_value(buildname, 'FEEDBACK_DIAGNOSTIC_INFO_UPLOAD_SERVER_HEADERS'))
cog.outl('public static final String PROXIED_WEB_APP_HTTP_AUTH_USERNAME = "%s";' % utils.get_embedded_value(buildname, 'PROXIED_WEB_APP_HTTP_AUTH_USERNAME'))
cog.outl('public static final String PROXIED_WEB_APP_HTTP_AUTH_PASSWORD = "%s";' % utils.get_embedded_value(buildname, 'PROXIED_WEB_APP_HTTP_AUTH_PASSWORD'))
cog.outl('public static final boolean IS_PLAY_STORE_BUILD = %s;' % ('true' if utils.get_embedded_value(buildname, 'IS_PLAY_STORE_BUILD') else 'false'))
cog.outl('public static final String HOME_TAB_URL_EXCLUSIONS[] = {"%s"};' % utils.get_embedded_value(buildname, 'HOME_TAB_URL_EXCLUSIONS'))
cog.outl('public static final String PROPAGATION_CHANNEL_ID = "%s";' % utils.get_embedded_value(buildname, 'PROPAGATION_CHANNEL_ID'))
cog.outl('public static final String REMOTE_SERVER_LIST_URLS_JSON = "%s";' % utils.get_embedded_value(buildname, 'REMOTE_SERVER_LIST_URLS_JSON'))
cog.outl('public static final String OBFUSCATED_SERVER_LIST_ROOT_URLS_JSON = "%s";' % utils.get_embedded_value(buildname, 'OBFUSCATED_SERVER_LIST_ROOT_URLS_JSON'))
cog.outl('public static final String REMOTE_SERVER_LIST_SIGNATURE_PUBLIC_KEY = "%s";' % utils.get_embedded_value(buildname, 'REMOTE_SERVER_LIST_SIGNATURE_PUBLIC_KEY'))
cog.outl('public static final String UPGRADE_URLS_JSON = "%s";' % utils.get_embedded_value(buildname, 'UPGRADE_URLS_JSON'))
cog.outl('public static final String UPGRADE_SIGNATURE_PUBLIC_KEY = "%s";' % utils.get_embedded_value(buildname, 'UPGRADE_SIGNATURE_PUBLIC_KEY'))
cog.outl('public static String SPONSOR_ID = "%s";' % utils.get_embedded_value(buildname, 'SPONSOR_ID'))
cog.outl('public static String INFO_LINK_URL = "%s";' % utils.get_embedded_value(buildname, 'INFO_LINK_URL'))
cog.outl('public static String GET_NEW_VERSION_URL = "%s";' % utils.get_embedded_value(buildname, 'GET_NEW_VERSION_URL'))
cog.outl('public static String GET_NEW_VERSION_EMAIL = "%s";' % utils.get_embedded_value(buildname, 'GET_NEW_VERSION_EMAIL'))
cog.outl('public static String FAQ_URL = "%s";' % utils.get_embedded_value(buildname, 'FAQ_URL'))
cog.outl('public static String DATA_COLLECTION_INFO_URL = "%s";' % utils.get_embedded_value(buildname, 'DATA_COLLECTION_INFO_URL'))
]]]*/
    public static final String CLIENT_VERSION = "1";

    public static final String[] EMBEDDED_SERVER_LIST = {""};

    public static final boolean IGNORE_NON_EMBEDDED_SERVER_ENTRIES = false;

    public static final String PROXIED_WEB_APP_HTTP_AUTH_USERNAME = "";

    public static final String PROXIED_WEB_APP_HTTP_AUTH_PASSWORD = "";

    public static final boolean IS_PLAY_STORE_BUILD = false;
    
    public static final String[] HOME_TAB_URL_EXCLUSIONS = {""};

    // These values are used when uploading diagnostic info
    public static final String FEEDBACK_ENCRYPTION_PUBLIC_KEY = "";
    public static final String FEEDBACK_DIAGNOSTIC_INFO_UPLOAD_SERVER = "";
    public static final String FEEDBACK_DIAGNOSTIC_INFO_UPLOAD_PATH = "";
    public static final String FEEDBACK_DIAGNOSTIC_INFO_UPLOAD_SERVER_HEADERS = "";

    public static final String PROPAGATION_CHANNEL_ID = "";

    public static final String REMOTE_SERVER_LIST_URLS_JSON = "[]";
    public static final String REMOTE_SERVER_LIST_SIGNATURE_PUBLIC_KEY = "";
    public static final String OBFUSCATED_SERVER_LIST_ROOT_URLS_JSON = "[]";

    public static final String UPGRADE_URLS_JSON = "[]";
    public static final String UPGRADE_SIGNATURE_PUBLIC_KEY = "";

    // Following embedded values may be overridden with values from Preferences. This is to prevent
    // Play Store Build instances from overriding embedded values from previously installed APKS.
    // Non-Play Store Build instances always write their values to persistent Preferences, and
    // Play Store Build instances always use persistent Preference values, if they exist.
    //
    // Propagation Channel ID and associated URLs are excluded from this persistence.
    // - The intention is to always use the latest embedded URLs, as these will be updated and
    //   changed from time to time.
    // - OSL URLs are tied to Propagation Channel ID, so these values must change in lock step.
    // - When an install shift from side-loaded to Google Play, its existing SLOKs earned
    //   under its old Propagation Channel ID will become obsolete.

    public static String SPONSOR_ID = "";

    // NOTE: Info link may be opened when not tunneled
    public static String INFO_LINK_URL = "https://sites.google.com/a/psiphon3.com/psiphon3/";


    // To be shown to the user in the feedback page.
    public static String GET_NEW_VERSION_URL = "https://s3.amazonaws.com/invalid_bucket_name/en/index.html";
    public static String GET_NEW_VERSION_EMAIL = "get@example.com";
    public static String FAQ_URL = "https://s3.amazonaws.com/invalid_bucket_name/en/faq.html";
    public static String DATA_COLLECTION_INFO_URL = "https://s3.amazonaws.com/invalid_bucket_name/en/faq.html#information-collected";
    
//[[[end]]]

    private static final String SPONSOR_ID_PREFERENCE = "sponsorIdPreference";
    private static final String INFO_LINK_URL_PREFERENCE = "infoLinkUrlPreference";
    private static final String GET_NEW_VERSION_URL_PREFERENCE = "getNewVersionUrlPreference";
    private static final String GET_NEW_VERSION_EMAIL_PREREFENCE = "getNewVersionEmailPreference";
    private static final String FAQ_URL_PREFERENCE = "faqUrlPreference";
    private static final String DATA_COLLECTION_INFO_URL_PREFERENCE = "dataCollectionInfoUrlPreference";

 public static void initialize(Context context)
    {
        // migrate existing values to Tray
        AppPreferences mpPreferences = new AppPreferences(context);
        String prefFileName = context.getPackageName() + "_preferences";

        mpPreferences.migrate(
                new SharedPreferencesImport(context, prefFileName, SPONSOR_ID_PREFERENCE, SPONSOR_ID_PREFERENCE),
                new SharedPreferencesImport(context, prefFileName, INFO_LINK_URL_PREFERENCE, INFO_LINK_URL_PREFERENCE),
                new SharedPreferencesImport(context, prefFileName, GET_NEW_VERSION_URL_PREFERENCE, GET_NEW_VERSION_URL_PREFERENCE),
                new SharedPreferencesImport(context, prefFileName, GET_NEW_VERSION_EMAIL_PREREFENCE, GET_NEW_VERSION_EMAIL_PREREFENCE),
                new SharedPreferencesImport(context, prefFileName, FAQ_URL_PREFERENCE, FAQ_URL_PREFERENCE),
                new SharedPreferencesImport(context, prefFileName, DATA_COLLECTION_INFO_URL_PREFERENCE, DATA_COLLECTION_INFO_URL_PREFERENCE)
        );

        if (!IS_PLAY_STORE_BUILD) {
            mpPreferences.put(SPONSOR_ID_PREFERENCE, SPONSOR_ID);
            mpPreferences.put(INFO_LINK_URL_PREFERENCE, INFO_LINK_URL);
            mpPreferences.put(GET_NEW_VERSION_URL_PREFERENCE, GET_NEW_VERSION_URL);
            mpPreferences.put(GET_NEW_VERSION_EMAIL_PREREFENCE, GET_NEW_VERSION_EMAIL);
            mpPreferences.put(FAQ_URL_PREFERENCE, FAQ_URL);
            mpPreferences.put(DATA_COLLECTION_INFO_URL_PREFERENCE, DATA_COLLECTION_INFO_URL);
        } else {
            SPONSOR_ID = mpPreferences.getString(SPONSOR_ID_PREFERENCE, SPONSOR_ID);
            INFO_LINK_URL = mpPreferences.getString(INFO_LINK_URL_PREFERENCE, INFO_LINK_URL);
            GET_NEW_VERSION_URL = mpPreferences.getString(GET_NEW_VERSION_URL_PREFERENCE, GET_NEW_VERSION_URL);
            GET_NEW_VERSION_EMAIL = mpPreferences.getString(GET_NEW_VERSION_EMAIL_PREREFENCE, GET_NEW_VERSION_EMAIL);
            FAQ_URL = mpPreferences.getString(FAQ_URL_PREFERENCE, FAQ_URL);
            DATA_COLLECTION_INFO_URL = mpPreferences.getString(DATA_COLLECTION_INFO_URL_PREFERENCE, DATA_COLLECTION_INFO_URL);
        }
    }

    public static boolean hasEverBeenSideLoaded(Context context)
    {
        if (IS_PLAY_STORE_BUILD)
        {
            AppPreferences mpPreferences = new AppPreferences(context);
            try {
                mpPreferences.getString(SPONSOR_ID_PREFERENCE);
            } catch (ItemNotFoundException e) {
                return false;
            }
            return true;
        }
        else
        {
            return true;
        }
    }
}
