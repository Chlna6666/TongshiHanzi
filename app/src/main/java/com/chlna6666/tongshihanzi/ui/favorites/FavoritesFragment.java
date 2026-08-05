/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna6666.tongshihanzi.ui.favorites;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.RecyclerView;

import com.chlna6666.tongshihanzi.R;
import com.chlna6666.tongshihanzi.domain.search.SearchResult;
import com.chlna6666.tongshihanzi.ui.search.SearchResultAdapter;
import com.chlna6666.tongshihanzi.util.MotionEffects;
import com.google.android.material.tabs.TabLayout;

public final class FavoritesFragment extends Fragment {
    private FavoritesViewModel viewModel;
    private SearchResultAdapter adapter;
    private TextView empty;
    private int selectedTab;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle state
    ) {
        return inflater.inflate(R.layout.fragment_favorites, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        MotionEffects.enterPage(view);
        viewModel = new ViewModelProvider(this).get(FavoritesViewModel.class);

        RecyclerView list = view.findViewById(R.id.list);
        empty = view.findViewById(R.id.empty);
        adapter = new SearchResultAdapter(this::openDetail);
        list.setItemAnimator(null);
        list.setAdapter(adapter);

        TabLayout tabs = view.findViewById(R.id.tabs);
        tabs.addTab(tabs.newTab().setText(R.string.saved_characters));
        tabs.addTab(tabs.newTab().setText(R.string.recent_searches));
        tabs.post(() -> {
            View firstChild = tabs.getChildAt(0);
            if (firstChild instanceof ViewGroup) {
                MotionEffects.installPressFeedbackForChildren((ViewGroup) firstChild);
            }
        });
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                selectedTab = tab.getPosition();
                load();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                load();
            }
        });

        viewModel.items().observe(getViewLifecycleOwner(), values -> {
            adapter.submitList(values);
            boolean isEmpty = values == null || values.isEmpty();
            empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            empty.setText(selectedTab == 0 ? R.string.no_favorites : R.string.empty_search);
        });
        load();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null) {
            load();
        }
    }

    private void load() {
        if (selectedTab == 0) {
            viewModel.loadFavorites();
        } else {
            viewModel.loadHistory();
        }
    }

    private void openDetail(SearchResult item) {
        Bundle args = new Bundle();
        args.putInt("characterId", item.getCharacterId());
        args.putString("characterText", item.getCharacter());
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_favorites_to_detail, args);
    }
}
