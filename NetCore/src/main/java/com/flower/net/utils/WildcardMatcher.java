package com.flower.net.utils;

public class WildcardMatcher {
    public static boolean isMatch(String text, String pattern) {
        int textLen = text.length();
        int patternLen = pattern.length();
        int i = 0, j = 0, lastStarPos = -1, textBacktrackPos = 0;

        while (i < textLen) {
            if (j < patternLen && (pattern.charAt(j) == '?' || pattern.charAt(j) == text.charAt(i))) {
                i++;
                j++;
            } else if (j < patternLen && pattern.charAt(j) == '*') {
                // Collapse multiple '*' into one by moving to the last '*'
                lastStarPos = j++;
                textBacktrackPos = i;
            } else if (lastStarPos != -1) {
                // Backtrack: retry matching after last '*'
                j = lastStarPos + 1;
                i = ++textBacktrackPos;
            } else {
                return false;
            }
        }

        // Ensure remaining characters in pattern are all '*'
        while (j < patternLen && pattern.charAt(j) == '*') {
            j++;
        }

        return j == patternLen;
    }
}