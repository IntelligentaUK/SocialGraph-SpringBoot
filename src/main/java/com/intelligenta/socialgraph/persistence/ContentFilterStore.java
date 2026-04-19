package com.intelligenta.socialgraph.persistence;

import java.util.List;

/** Per-user content filters (negative keywords + blocked image hashes). Long-lived. */
public interface ContentFilterStore {
    void addNegativeKeyword(String uid, String keyword);
    boolean hasAnyNegativeKeyword(String uid, List<String> keywords);

    void blockImage(String uid, String md5);
    boolean isImageBlocked(String uid, String md5);
}
