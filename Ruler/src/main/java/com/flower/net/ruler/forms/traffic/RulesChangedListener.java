package com.flower.net.ruler.forms.traffic;

/** Notified synchronously, on the UI thread, after any change to a rule list's rules. */
public interface RulesChangedListener {
    void rulesChanged(RuleListForm ruleList);
}
