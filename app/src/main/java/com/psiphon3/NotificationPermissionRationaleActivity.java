/*
 * Copyright (c) 2023, Psiphon Inc.
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

package com.psiphon3;

import android.Manifest;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BulletSpan;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.PermissionChecker;

import com.psiphon3.psiphonlibrary.LocalizedActivities;

public class NotificationPermissionRationaleActivity extends LocalizedActivities.AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.permission_rationale);
        setFinishOnTouchOutside(false);

        String alertTitle = String.format(getString(R.string.notifications_permission_rationale_title), getString(R.string.app_name));
        ((TextView) findViewById(R.id.alertTitle)).setText(alertTitle);

        String str = String.format(getString(R.string.notifications_permission_rationale_intro), getString(R.string.app_name));

        SpannableStringBuilder message = new SpannableStringBuilder();
        message.append(str);
        message.append("\n\n");

        SpannableString bp = new SpannableString(getString(R.string.notifications_permission_rationale_vpn_state_bp));
        bp.setSpan(new BulletSpan(15), 0, bp.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        message.append(bp);
        message.append("\n\n");

        bp = new SpannableString(getString(R.string.notifications_permission_rationale_connection_problems_bp));
        bp.setSpan(new BulletSpan(15), 0, bp.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        message.append(bp);
        message.append("\n\n");

        bp = new SpannableString(getString(R.string.notifications_permission_rationale_crash_reports_bp));
        bp.setSpan(new BulletSpan(15), 0, bp.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        message.append(bp);
        message.append("\n\n");

        bp = new SpannableString(getString(R.string.notifications_permission_rationale_malaware_alerts_bp));
        bp.setSpan(new BulletSpan(15), 0, bp.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        message.append(bp);
        message.append("\n\n");

        try {
            Class.forName("com.psiphon3.psiphonlibrary.UpgradeChecker");
            bp = new SpannableString(getString(R.string.notifications_permission_rationale_upgrade_available_bp));
            bp.setSpan(new BulletSpan(15), 0, bp.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            message.append(bp);
            message.append("\n\n");
        } catch (ClassNotFoundException ignored) {
        }

        message.append(getString(R.string.notifications_permission_rationale_disable_any_time));
        ((TextView) findViewById(R.id.messageTextView)).setText(message);

        findViewById(R.id.continue_btn).setOnClickListener(v -> {
            // Request notification permission again.
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PermissionChecker.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        MainActivity.REQUEST_CODE_PERMISSIONS);
            } else {
                // If we are not requesting notification permission, finish the activity.
                finish();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        // Regardless of whether the permission was granted or not, finish the activity.
        if (requestCode == MainActivity.REQUEST_CODE_PERMISSIONS) {
            finish();
        }
    }
}
