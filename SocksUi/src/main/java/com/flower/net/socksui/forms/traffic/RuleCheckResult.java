package com.flower.net.socksui.forms.traffic;

import com.flower.net.config.access.Access;

public class RuleCheckResult {
    final Access checkResult;
    final boolean isDirectIpBlock;
    final boolean isRuleMatched;

    RuleCheckResult(Access checkResult, boolean isDirectIpBlock, boolean isRuleMatched) {
        this.checkResult = checkResult;
        this.isDirectIpBlock = isDirectIpBlock;
        this.isRuleMatched = isRuleMatched;
    }
}

