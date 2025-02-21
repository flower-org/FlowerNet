package com.flower.conntrack.whiteblacklist;

public class WildcardMatcher {
    public static boolean isMatch(String text, String pattern) {
        int texLen = text.length();
        int pattLen = pattern.length();
        int i = 0, j = 0, wildcardIndex = -1, match = 0;

        while (i < texLen) {
            // Characters match or '?' in pattern matches
            // any character.
            if (j < pattLen && (pattern.charAt(j) == '?' || pattern.charAt(j) == text.charAt(i))) {
                i++;
                j++;
            } else if (j < pattLen && pattern.charAt(j) == '*') {
                // Wildcard character '*', mark the current
                // position in the pattern and the text as a
                // proper match.
                wildcardIndex = j;
                match = i;
                j++;
            } else if (wildcardIndex != -1) {
                // No match, but a previous wildcard was found.
                // Backtrack to the last '*' character position
                // and try for a different match.
                j = wildcardIndex + 1;
                match++;
                i = match;
            } else {
                // If none of the above cases comply, the
                // pattern does not match.
                return false;
            }
        }

        // Consume any remaining '*' characters in
        // the given pattern.
        while (j < pattLen && pattern.charAt(j) == '*') {
            j++;
        }

        // If we have reached the end of both the
        // pattern and the text, the pattern matches
        // the text.
        return j == pattLen;
    }

    public static void main(String[] args) {
        String str = "rjdokrjdokrjdokrjdok";
        String pattern = "*jdok";

        System.out.println(isMatch(str, pattern) ? "Yes" : "No");
    }

    public static void main2(String[] args) {
        String str = "baaabab";
        String pattern = "*****ba*****ab";

        System.out.println(isMatch(str, pattern) ? "Yes" : "No");
    }
}