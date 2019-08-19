/*
 * Copyright (c) 2016, Psiphon Inc.
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

import android.app.Service;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.IBinder;

public class TunnelService extends Service
{
    private TunnelManager m_Manager = new TunnelManager(this);

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Note that this will be called no matter what the current LocaleManager locale is, be it system or not.
        // So we want to always have the TunnelManager update their context to have the new configuration.
        // We don't need to update the notifications though if the language is not set to default.
        // Also note that if this service is stopped when the system language is changed, notifications like the
        // upgrade one will not be updated until something else triggers them to be updated. This could be fixed by
        // adding a broadcast receiver for locale changes but ATM it feels not worth the effort.
        m_Manager.updateContext(this);

        LocaleManager localeManager = LocaleManager.getInstance(this);
        if (localeManager.isSetToSystemLocale()) {
            m_Manager.updateNotifications();
        }
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return m_Manager.onBind(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        return m_Manager.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCreate()
    {
        m_Manager.onCreate();
    }

    @Override
    public void onDestroy()
    {
        m_Manager.onDestroy();
    }
}
