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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.psiphon3.psiphonlibrary.LocalizedActivities;

public class PsiphonBumpHelpActivity extends LocalizedActivities.AppCompatActivity {
    private ViewPager2 viewPager;
    private TextView textViewSkip;
    private TextView textViewNext;
    private static final int NUM_PAGES = 4;

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_psiphon_bump_help_layout);

        textViewSkip = findViewById(R.id.textViewSkip);
        textViewSkip.setOnClickListener(v -> finish());

        textViewNext = findViewById(R.id.textViewNext);
        textViewNext.setOnClickListener(v -> {
            if (viewPager.getCurrentItem() == NUM_PAGES - 1) {
                finish();
            } else {
                viewPager.setCurrentItem(viewPager.getCurrentItem() + 1);
            }
        });

        viewPager = findViewById(R.id.viewPager);
        viewPager.setAdapter(new ViewPagerAdapter(this));

        TabLayout pageIndicator = findViewById(R.id.pageIndicator);
        new TabLayoutMediator(pageIndicator, viewPager, (tab, position) -> {
        }).attach();

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (position == NUM_PAGES - 1) {
                    textViewSkip.setVisibility(View.INVISIBLE);
                    textViewNext.setText(R.string.onboarding_done);
                } else {
                    textViewSkip.setVisibility(View.VISIBLE);
                    textViewNext.setText(R.string.onboarding_next);
                }
            }
        });
    }

    private static class ViewPagerAdapter extends FragmentStateAdapter {
        public ViewPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
            super(fragmentActivity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            if (position >= 0 && position < NUM_PAGES) {
                return PsiphonBumpHelpFragment.create(position);
            }
            throw new RuntimeException("Invalid position in view pager adapter: " + position);
        }

        @Override
        public int getItemCount() {
            return NUM_PAGES;
        }
    }

    public static class PsiphonBumpHelpFragment extends Fragment {
        private static final String ARG_POSITION = "position";

        public static PsiphonBumpHelpFragment create(int position) {
            PsiphonBumpHelpFragment fragment = new PsiphonBumpHelpFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_POSITION, position);
            fragment.setArguments(args);
            return fragment;
        }

        @Nullable
        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            return inflater.inflate(R.layout.psiphon_bump_help_fragment, container, false);
        }

        @Override
        public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Bundle args = getArguments();
            if (args != null) {
                int position = args.getInt(ARG_POSITION);
                TextView textView = view.findViewById(R.id.textView);
                switch (position) {
                    case 0:
                        textView.setText(R.string.psiphon_bump_help_1);
                        break;
                    case 1:
                        textView.setText(R.string.psiphon_bump_help_2);
                        break;
                    case 2:
                        textView.setText(R.string.psiphon_bump_help_3);
                        break;
                    case 3:
                        textView.setText(R.string.psiphon_bump_help_4);
                        break;
                }
            }
        }
    }
}
