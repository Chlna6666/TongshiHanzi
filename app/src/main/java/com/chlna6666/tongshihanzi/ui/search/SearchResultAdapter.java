/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna6666.tongshihanzi.ui.search;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.chlna6666.tongshihanzi.R;
import com.chlna6666.tongshihanzi.domain.search.SearchResult;
import com.chlna6666.tongshihanzi.util.MotionEffects;

public final class SearchResultAdapter
        extends ListAdapter<SearchResult, SearchResultAdapter.Holder> {

    public interface Listener {
        void onOpen(SearchResult item);
    }

    private final Listener listener;

    public SearchResultAdapter(Listener listener) {
        super(new DiffUtil.ItemCallback<>() {
            @Override
            public boolean areItemsTheSame(
                    @NonNull SearchResult first,
                    @NonNull SearchResult second
            ) {
                if (first.getCharacterId() >= 0 || second.getCharacterId() >= 0) {
                    return first.getCharacterId() == second.getCharacterId();
                }
                return first.getCharacter().equals(second.getCharacter());
            }

            @Override
            public boolean areContentsTheSame(
                    @NonNull SearchResult first,
                    @NonNull SearchResult second
            ) {
                return first.getScore() == second.getScore()
                        && first.getPinyin().equals(second.getPinyin())
                        && first.getDefinition().equals(second.getDefinition())
                        && first.getMatchType().equals(second.getMatchType());
            }
        });
        this.listener = listener;
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        SearchResult item = getItem(position);
        return item.getCharacterId() >= 0
                ? item.getCharacterId()
                : 0x4000_0000L + item.getCharacter().hashCode();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_search_result, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(getItem(position), listener, getItemId(position), position);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        private final TextView character;
        private final TextView pinyin;
        private final TextView metadata;
        private final TextView definition;
        private final TextView matchType;

        Holder(View view) {
            super(view);
            character = view.findViewById(R.id.character);
            pinyin = view.findViewById(R.id.pinyin);
            metadata = view.findViewById(R.id.metadata);
            definition = view.findViewById(R.id.definition);
            matchType = view.findViewById(R.id.match_type);
            MotionEffects.installPressFeedback(view);
        }

        void bind(SearchResult item, Listener listener, long stableKey, int position) {
            character.setText(item.getCharacter());
            pinyin.setText(item.getPinyin().trim().isEmpty()
                    ? "读音待补充" : item.getPinyin());
            metadata.setText("部首："
                    + (item.getRadical().trim().isEmpty() ? "—" : item.getRadical())
                    + "　" + item.getTotalStrokes() + " 画");
            definition.setText(item.getDefinition().trim().isEmpty()
                    ? "释义数据待补充" : item.getDefinition());
            matchType.setText(item.getMatchType());
            itemView.setContentDescription(item.getCharacter() + "，" + item.getPinyin()
                    + "，" + item.getTotalStrokes() + "画");
            itemView.setOnClickListener(view -> listener.onOpen(item));
            MotionEffects.enterListItem(itemView, stableKey, position);
        }
    }
}
