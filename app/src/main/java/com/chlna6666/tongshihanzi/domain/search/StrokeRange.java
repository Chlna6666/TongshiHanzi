/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.chlna6666.tongshihanzi.domain.search;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
public final class StrokeRange {
    private static final Pattern PATTERN=Pattern.compile("^\\s*(\\d{1,2})(?:\\s*[-~到至]\\s*(\\d{1,2}))?\\s*$");
    private final int min,max;
    public StrokeRange(int min,int max){if(min<1||max<min||max>64)throw new IllegalArgumentException("笔画范围无效");this.min=min;this.max=max;}
    public int min(){return min;} public int max(){return max;}
    public static StrokeRange parse(String raw){Matcher m=PATTERN.matcher(raw==null?"":raw);if(!m.matches())throw new IllegalArgumentException("请输入 1-64 的笔画数或范围");int a=Integer.parseInt(m.group(1));int b=m.group(2)==null?a:Integer.parseInt(m.group(2));return new StrokeRange(Math.min(a,b),Math.max(a,b));}
    @Override public boolean equals(Object o){return o instanceof StrokeRange&&min==((StrokeRange)o).min&&max==((StrokeRange)o).max;}
    @Override public int hashCode(){return Objects.hash(min,max);} @Override public String toString(){return min==max?String.valueOf(min):min+"-"+max;}
}
