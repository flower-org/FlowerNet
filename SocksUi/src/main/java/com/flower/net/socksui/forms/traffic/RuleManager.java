package com.flower.net.socksui.forms.traffic;

import com.flower.net.config.access.Access;
import com.flower.net.conntrack.whiteblacklist.ImmutableAddressRecord;
import com.flower.net.conntrack.whiteblacklist.ImmutableHostRecord;
import com.flower.net.conntrack.whiteblacklist.ImmutablePortRecord;
import com.flower.net.conntrack.whiteblacklist.WhitelistBlacklistConnectionFilter;
import com.flower.net.socksui.JavaFxUtils;
import com.flower.net.socksui.ModalWindow;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

public abstract class RuleManager {
    final static Logger LOGGER = LoggerFactory.getLogger(RuleManager.class);

    final WhitelistBlacklistConnectionFilter filter;

    protected RuleManager(WhitelistBlacklistConnectionFilter filter) {
        this.filter = filter;
    }

    /** Open TrafficRule dialog */
    public void newRule() {
        try {
            TrafficRuleAddDialog trafficRuleAddDialog = new TrafficRuleAddDialog();
            Stage workspaceStage = ModalWindow.showModal(checkNotNull(getStage()),
                    stage -> { trafficRuleAddDialog.setStage(stage); return trafficRuleAddDialog; },
                    "Add new traffic rule");

            workspaceStage.setOnHidden(
                    ev -> {
                        try {
                            TrafficRule trafficRule = trafficRuleAddDialog.getTrafficRule();
                            if (trafficRule != null) {
                                addTrafficRule(trafficRule);
                                refreshAndRestoreCursor();
                            }
                        } catch (Exception e) {
                            Alert alert = new Alert(Alert.AlertType.ERROR, "Error adding known server: " + e, ButtonType.OK);
                            LOGGER.error("Error adding known server: ", e);
                            alert.showAndWait();
                        }
                    }
            );
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Error adding known server: " + e, ButtonType.OK);
            LOGGER.error("Error adding known server: ", e);
            alert.showAndWait();
        }

        refreshAndRestoreCursor();
    }

    /** Open TrafficRule dialog pre-filled with derived rule */
    public void deriveRule() {
        try {
            TrafficRule trafficRuleToDeriveFrom = getSelectedTrafficRule();
            if (trafficRuleToDeriveFrom == null) {
                return;
            }

            TrafficRuleAddDialog trafficRuleAddDialog = new TrafficRuleAddDialog(trafficRuleToDeriveFrom);
            Stage workspaceStage = ModalWindow.showModal(checkNotNull(getStage()),
                    stage -> { trafficRuleAddDialog.setStage(stage); return trafficRuleAddDialog; },
                    "Add new traffic rule");

            workspaceStage.setOnHidden(
                    ev -> {
                        try {
                            TrafficRule trafficRule = trafficRuleAddDialog.getTrafficRule();
                            if (trafficRule != null) {
                                addTrafficRule(trafficRule);
                                refreshAndRestoreCursor();
                            }
                        } catch (Exception e) {
                            Alert alert = new Alert(Alert.AlertType.ERROR, "Error adding known server: " + e, ButtonType.OK);
                            LOGGER.error("Error adding known server: ", e);
                            alert.showAndWait();
                        }
                    }
            );
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Error adding known server: " + e, ButtonType.OK);
            LOGGER.error("Error adding known server: ", e);
            alert.showAndWait();
        }

        refreshAndRestoreCursor();
    }

    /** Add traffic rule to innerFilter */
    private void addTrafficRule(TrafficRule trafficRule) {
        if (trafficRule.addressRecord != null) {
            filter.addAddressRecord(trafficRule.addressRecord, true);
        } else if (trafficRule.hostRecord != null) {
            filter.addHostRecord(trafficRule.hostRecord, true);
        } else if (trafficRule.portRecord != null) {
            filter.addPortRecord(trafficRule.portRecord, true);
        }
        refreshAndRestoreCursor();
    }

    protected String clearRulesMsg() { return "Delete all rules?"; }

    /** Clear inner filter rules */
    public void clearRules() {
        if (JavaFxUtils.showYesNoDialog(clearRulesMsg()) == JavaFxUtils.YesNo.YES) {
            filter.clear();
            refreshAndRestoreCursor();
        }
    }

    protected String clearWhitelistRulesMsg() { return "Delete all Whitelist rules?"; }

    /** Clear inner filter ALLOW rules */
    public void clearAllowlistRules() {
        if (JavaFxUtils.showYesNoDialog(clearWhitelistRulesMsg()) == JavaFxUtils.YesNo.YES) {
            filter.clearFilterType(Access.ALLOW);
            refreshAndRestoreCursor();
        }
    }

    protected String clearBlacklistRulesMsg() {
        return "Delete all Blacklist rules?";
    }

    /** Clear inner filter DENY rules */
    public void clearDenylistRules() {
        if (JavaFxUtils.showYesNoDialog(clearBlacklistRulesMsg()) == JavaFxUtils.YesNo.YES) {
            filter.clearFilterType(Access.DENY);
            refreshAndRestoreCursor();
        }
    }

    /** Remove inner filter rule selected in UI */
    public void removeRule() {
        TrafficRule trafficRule = getSelectedTrafficRule();
        if (trafficRule != null) {
            if (trafficRule.addressRecord != null) {
                filter.removeAddressRecord(trafficRule.addressRecord);
            } else if (trafficRule.hostRecord != null) {
                filter.removeHostRecord(trafficRule.hostRecord);
            } else if (trafficRule.portRecord != null) {
                filter.removePortRecord(trafficRule.portRecord);
            }
            checkBlacklistedHosts();
        }

        refreshAndRestoreCursor();
    }

    Access flipFilterType(Access type) {
        switch (type) {
            case DENY: return Access.ALLOW;
            case ALLOW: return Access.DENY;
            default: throw new IllegalArgumentException("FilterType should be either Blacklist or Whitelist");
        }
    }

    /** Flip access type of a rule selected in UI */
    public void flipRule() {
        TrafficRule trafficRule = getSelectedTrafficRule();
        if (trafficRule != null) {
            if (trafficRule.addressRecord != null) {
                filter.removeAddressRecord(trafficRule.addressRecord);
                Access newAccess = flipFilterType(trafficRule.addressRecord.access());
                filter.addAddressRecord(ImmutableAddressRecord.builder()
                                .dstHost(trafficRule.addressRecord.dstHost())
                                .dstPort(trafficRule.addressRecord.dstPort())
                                .access(newAccess)
                                .creationTimestamp(System.currentTimeMillis())
                                .isWildcard(trafficRule.isWildcard())
                                .build(),
                        true);
            } else if (trafficRule.hostRecord != null) {
                filter.removeHostRecord(trafficRule.hostRecord);
                Access newAccess = flipFilterType(trafficRule.hostRecord.access());
                filter.addHostRecord(ImmutableHostRecord.builder()
                                .dstHost(trafficRule.hostRecord.dstHost())
                                .access(newAccess)
                                .creationTimestamp(System.currentTimeMillis())
                                .isWildcard(trafficRule.isWildcard())
                                .build(),
                        true);
            } else if (trafficRule.portRecord != null) {
                filter.removePortRecord(trafficRule.portRecord);
                Access newAccess = flipFilterType(trafficRule.portRecord.access());
                filter.addPortRecord(ImmutablePortRecord.builder()
                                .dstPort(trafficRule.portRecord.dstPort())
                                .access(newAccess)
                                .creationTimestamp(System.currentTimeMillis())
                                .build(),
                        true);
            }
            checkBlacklistedHosts();
        }
        refreshAndRestoreCursor();
    }

    abstract void refreshAndRestoreCursor();
    /** "close on blacklist" existing connections for newly blacklisted hosts */
    abstract void checkBlacklistedHosts();
    @Nullable abstract TrafficRule getSelectedTrafficRule();
    @Nullable abstract Stage getStage();
}
