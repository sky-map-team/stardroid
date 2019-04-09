package com.google.android.stardroid.activities.util.loadPlaces.model;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class MatchedSubstring {
    @SerializedName("length")
    @Expose
    private int length;
    @SerializedName("offset")
    @Expose
    private int offset;

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }
}
