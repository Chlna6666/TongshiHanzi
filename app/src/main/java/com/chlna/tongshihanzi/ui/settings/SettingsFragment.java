/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna.tongshihanzi.ui.settings;
import android.os.Bundle;import android.view.LayoutInflater;import android.view.View;import android.view.ViewGroup;import androidx.annotation.NonNull;import androidx.annotation.Nullable;import androidx.fragment.app.Fragment;import com.chlna.tongshihanzi.R;
public final class SettingsFragment extends Fragment{@Nullable @Override public View onCreateView(@NonNull LayoutInflater i,@Nullable ViewGroup c,@Nullable Bundle s){return i.inflate(R.layout.fragment_settings,c,false);}@Override public void onViewCreated(@NonNull View v,@Nullable Bundle s){if(s==null)getChildFragmentManager().beginTransaction().replace(R.id.settings_container,new SettingsPreferenceFragment()).commit();}}
