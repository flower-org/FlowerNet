package com.flower.net.socksui.forms.traffic;

import com.flower.net.conntrack.AddressCheck;

public class CaptureFilter {
    final boolean matchedAllowed;
    final boolean matchedProhibited;
    final boolean unmatchedAllowed;
    final boolean unmatchedProhibited;

    CaptureFilter(boolean matchedAllowed, boolean matchedProhibited, boolean unmatchedAllowed, boolean unmatchedProhibited) {
        this.matchedAllowed = matchedAllowed;
        this.matchedProhibited = matchedProhibited;
        this.unmatchedAllowed = unmatchedAllowed;
        this.unmatchedProhibited = unmatchedProhibited;
    }

    boolean matchCapturedRecord(AddressCheck checkResult, boolean isRuleMatched) {
        if (isRuleMatched) {
            if (checkResult == AddressCheck.CONNECTION_ALLOWED) {
                return matchedAllowed;
            } else {
                return matchedProhibited;
            }
        } else {
            if (checkResult == AddressCheck.CONNECTION_ALLOWED) {
                return unmatchedAllowed;
            } else {
                return unmatchedProhibited;
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        if (matchedAllowed && matchedProhibited && unmatchedAllowed && unmatchedProhibited) {
            builder.append("ALL");
        } else if (matchedAllowed && matchedProhibited) {
            builder.append("MATCHED");
            if (unmatchedAllowed) { builder.append(" & UNMATCHED/ALLOWED"); }
            if (unmatchedProhibited) { builder.append(" & UNMATCHED/PROHIBITED"); }
        } else if (unmatchedAllowed && unmatchedProhibited) {
            if (matchedAllowed) { builder.append("MATCHED/ALLOWED & "); }
            if (matchedProhibited) { builder.append("MATCHED/PROHIBITED & "); }
            builder.append("UNMATCHED");
        } else {
            if (matchedAllowed) { builder.append("MATCHED/ALLOWED"); }
            if (matchedProhibited) {
                if (!builder.isEmpty()) { builder.append(" & "); }
                builder.append("MATCHED/PROHIBITED");
            }
            if (unmatchedAllowed) {
                if (!builder.isEmpty()) { builder.append(" & "); }
                builder.append("UNMATCHED/ALLOWED");
            }
            if (unmatchedProhibited) {
                if (!builder.isEmpty()) { builder.append(" & "); }
                builder.append("UNMATCHED/PROHIBITED");
            }
            if (builder.isEmpty()) { builder.append("NONE"); }
        }
        return builder.toString();
    }
}
