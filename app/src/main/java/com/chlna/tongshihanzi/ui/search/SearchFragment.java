/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna.tongshihanzi.ui.search;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.RecyclerView;

import com.chlna.tongshihanzi.R;
import com.chlna.tongshihanzi.domain.search.SearchMode;
import com.chlna.tongshihanzi.domain.search.SearchResult;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

public final class SearchFragment extends Fragment {
    private static final long PROGRESS_SHOW_DELAY_MS = 180L;

    private SearchViewModel viewModel;
    private SearchResultAdapter adapter;
    private View emptyState;
    private TextView emptyTitle;
    private LinearProgressIndicator progress;
    private boolean loading;

    private final Runnable showProgress = () -> {
        if (progress == null || !loading) {
            return;
        }
        List<SearchResult> current = viewModel == null ? null : viewModel.results().getValue();
        boolean hasVisibleResults = current != null && !current.isEmpty();
        progress.setVisibility(hasVisibleResults ? View.INVISIBLE : View.VISIBLE);
    };

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle state
    ) {
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        viewModel = new ViewModelProvider(this).get(SearchViewModel.class);
        RecyclerView results = view.findViewById(R.id.results);
        emptyState = view.findViewById(R.id.empty_state);
        emptyTitle = view.findViewById(R.id.empty_title);
        progress = view.findViewById(R.id.progress);
        adapter = new SearchResultAdapter(this::openDetail);
        results.setAdapter(adapter);

        TextInputEditText input = view.findViewById(R.id.search_input);
        if (!viewModel.query().isEmpty()) {
            input.setText(viewModel.query());
            input.setSelection(input.length());
        }
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                viewModel.setQuery(value.toString());
            }

            @Override
            public void afterTextChanged(Editable value) {
            }
        });
        input.setOnEditorActionListener((value, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                viewModel.searchNow();
                return true;
            }
            return false;
        });

        ChipGroup modes = view.findViewById(R.id.mode_group);
        modes.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) {
                return;
            }
            int id = ids.get(0);
            SearchMode mode = id == R.id.mode_character ? SearchMode.CHARACTER
                    : id == R.id.mode_pinyin ? SearchMode.PINYIN
                    : id == R.id.mode_stroke ? SearchMode.STROKE
                    : id == R.id.mode_wubi ? SearchMode.WUBI
                    : SearchMode.AUTO;
            viewModel.setMode(mode);
        });

        viewModel.results().observe(getViewLifecycleOwner(), this::renderResults);
        viewModel.loading().observe(getViewLifecycleOwner(), this::renderLoading);
        viewModel.error().observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.trim().isEmpty()) {
                Snackbar.make(view, message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        if (progress != null) {
            progress.removeCallbacks(showProgress);
        }
        progress = null;
        emptyState = null;
        emptyTitle = null;
        adapter = null;
        super.onDestroyView();
    }

    private void renderLoading(Boolean value) {
        loading = Boolean.TRUE.equals(value);
        if (progress == null) {
            return;
        }
        progress.removeCallbacks(showProgress);
        if (!loading) {
            progress.setVisibility(View.INVISIBLE);
            return;
        }
        List<SearchResult> current = viewModel.results().getValue();
        if (current != null && !current.isEmpty()) {
            progress.setVisibility(View.INVISIBLE);
            return;
        }
        progress.setVisibility(View.INVISIBLE);
        progress.postDelayed(showProgress, PROGRESS_SHOW_DELAY_MS);
    }

    private void renderResults(List<SearchResult> values) {
        if (adapter == null || emptyState == null || emptyTitle == null) {
            return;
        }
        adapter.submitList(values);
        boolean empty = values == null || values.isEmpty();
        emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        emptyTitle.setText(viewModel.query().trim().isEmpty()
                ? R.string.empty_search : R.string.no_result);
    }

    private void openDetail(SearchResult item) {
        viewModel.recordOpen(item);
        Bundle args = new Bundle();
        args.putInt("characterId", item.getCharacterId());
        args.putString("characterText", item.getCharacter());
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_search_to_detail, args);
    }
}
