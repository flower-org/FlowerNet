package com.flower.net.ruler.forms.traffic;

import com.flower.net.config.access.Access;

/** Outcome of checking a destination against the rule lists and filtering settings */
class CheckResult {
    final Access checkResult;
    final boolean isDirectIpBlock;
    final boolean isRuleMatched;

    CheckResult(Access checkResult, boolean isDirectIpBlock, boolean isRuleMatched) {
        this.checkResult = checkResult;
        this.isDirectIpBlock = isDirectIpBlock;
        this.isRuleMatched = isRuleMatched;
    }
}
