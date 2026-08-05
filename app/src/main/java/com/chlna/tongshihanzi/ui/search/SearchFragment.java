/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna.tongshihanzi.ui.search;

import android.os.Bundle;import android.text.Editable;import android.text.TextWatcher;import android.view.LayoutInflater;import android.view.View;import android.view.ViewGroup;import android.view.inputmethod.EditorInfo;import android.widget.TextView;
import androidx.annotation.NonNull;import androidx.annotation.Nullable;import androidx.fragment.app.Fragment;import androidx.lifecycle.ViewModelProvider;import androidx.navigation.fragment.NavHostFragment;import androidx.recyclerview.widget.RecyclerView;
import com.chlna.tongshihanzi.R;import com.chlna.tongshihanzi.domain.search.SearchMode;import com.chlna.tongshihanzi.domain.search.SearchResult;import com.google.android.material.chip.ChipGroup;import com.google.android.material.progressindicator.LinearProgressIndicator;import com.google.android.material.snackbar.Snackbar;import com.google.android.material.textfield.TextInputEditText;import java.util.List;

public final class SearchFragment extends Fragment {
    private SearchViewModel viewModel;private SearchResultAdapter adapter;private View emptyState;private TextView emptyTitle;private LinearProgressIndicator progress;
    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,@Nullable ViewGroup container,@Nullable Bundle state){return inflater.inflate(R.layout.fragment_search,container,false);}
    @Override public void onViewCreated(@NonNull View view,@Nullable Bundle state){
        viewModel=new ViewModelProvider(this).get(SearchViewModel.class);RecyclerView results=view.findViewById(R.id.results);emptyState=view.findViewById(R.id.empty_state);emptyTitle=view.findViewById(R.id.empty_title);progress=view.findViewById(R.id.progress);adapter=new SearchResultAdapter(this::openDetail);results.setAdapter(adapter);
        TextInputEditText input=view.findViewById(R.id.search_input);if(!viewModel.query().isEmpty())input.setText(viewModel.query());input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){viewModel.setQuery(s.toString());}public void afterTextChanged(Editable s){}});input.setOnEditorActionListener((v,actionId,event)->{if(actionId==EditorInfo.IME_ACTION_SEARCH){viewModel.searchNow();return true;}return false;});
        ChipGroup modes=view.findViewById(R.id.mode_group);modes.setOnCheckedStateChangeListener((group,ids)->{if(ids.isEmpty())return;int id=ids.get(0);SearchMode mode=id==R.id.mode_character?SearchMode.CHARACTER:id==R.id.mode_pinyin?SearchMode.PINYIN:id==R.id.mode_stroke?SearchMode.STROKE:id==R.id.mode_wubi?SearchMode.WUBI:SearchMode.AUTO;viewModel.setMode(mode);});
        viewModel.results().observe(getViewLifecycleOwner(),this::renderResults);viewModel.loading().observe(getViewLifecycleOwner(),loading->progress.setVisibility(Boolean.TRUE.equals(loading)?View.VISIBLE:View.GONE));viewModel.error().observe(getViewLifecycleOwner(),message->{if(message!=null&&!message.trim().isEmpty())Snackbar.make(view,message,Snackbar.LENGTH_LONG).show();});
    }
    private void renderResults(List<SearchResult> values){adapter.submitList(values);boolean empty=values==null||values.isEmpty();emptyState.setVisibility(empty?View.VISIBLE:View.GONE);emptyTitle.setText(viewModel.query().trim().isEmpty()?R.string.empty_search:R.string.no_result);}
    private void openDetail(SearchResult item){viewModel.recordOpen(item);Bundle args=new Bundle();args.putInt("characterId",item.getCharacterId());NavHostFragment.findNavController(this).navigate(R.id.action_search_to_detail,args);}
}
