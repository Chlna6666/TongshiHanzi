/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna.tongshihanzi.domain.search;

import java.util.Objects;
public final class SearchResult {
    private final int characterId; private final String character; private final String pinyin; private final String radical;
    private final int totalStrokes; private final String definition; private final String matchType; private final int score;
    public SearchResult(int characterId, String character, String pinyin, String radical, int totalStrokes, String definition, String matchType, int score) {
        this.characterId=characterId; this.character=character; this.pinyin=pinyin; this.radical=radical; this.totalStrokes=totalStrokes; this.definition=definition; this.matchType=matchType; this.score=score;
    }
    public int getCharacterId(){return characterId;} public String getCharacter(){return character;} public String getPinyin(){return pinyin;}
    public String getRadical(){return radical;} public int getTotalStrokes(){return totalStrokes;} public String getDefinition(){return definition;}
    public String getMatchType(){return matchType;} public int getScore(){return score;}
    @Override public boolean equals(Object o){return o instanceof SearchResult && characterId==((SearchResult)o).characterId;}
    @Override public int hashCode(){return Objects.hash(characterId);}
}
