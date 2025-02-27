package com.flower.net.socksui.forms.traffic;

import com.flower.net.config.access.Access;
import com.flower.net.conntrack.whiteblacklist.ImmutableAddressRecord;
import com.flower.net.conntrack.whiteblacklist.ImmutableHostRecord;
import com.flower.net.conntrack.whiteblacklist.ImmutablePortRecord;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;

import static com.google.common.base.Preconditions.checkNotNull;

public class TrafficRuleAddDialog extends VBox {
    final static Logger LOGGER = LoggerFactory.getLogger(TrafficRuleAddDialog.class);

    public final static String WHITELIST = "Whitelist";
    public final static String BLACKLIST = "Blacklist";

    @FXML @Nullable ComboBox<String> filterTypeComboBox;
    @FXML @Nullable TextField hostTextField;
    @FXML @Nullable TextField portTextField;
    @FXML @Nullable CheckBox isWildcardRuleCheckBox;

    @Nullable Stage stage;
    @Nullable volatile TrafficRule trafficRule = null;

    public TrafficRuleAddDialog() {
        this(null);
    }

    Boolean getIsWhitelist(TrafficRule trafficRule) {
        return trafficRule.getFilterType() == Access.ALLOW;
    }
    @Nullable String getHost(TrafficRule trafficRule) {
        return StringUtils.defaultIfBlank(trafficRule.getHost(), null);
    }
    @Nullable Integer getPort(TrafficRule trafficRule) {
        return trafficRule.getIntPort();
    }
    Boolean getIsWildcard(TrafficRule trafficRule) {
        return trafficRule.isWildcard();
    }

    public TrafficRuleAddDialog(@Nullable TrafficRule trafficRule) {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("TrafficRuleAddDialog.fxml"));
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(this);

        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }

        if (trafficRule != null) {
            Boolean isWhitelist = getIsWhitelist(trafficRule);
            String host = getHost(trafficRule);
            Integer port = getPort(trafficRule);
            Boolean isWildcard = getIsWildcard(trafficRule);

            checkNotNull(filterTypeComboBox).getSelectionModel().select(isWhitelist ? WHITELIST : BLACKLIST);
            if (host != null) { checkNotNull(hostTextField).textProperty().set(host); }
            if (port != null) { checkNotNull(portTextField).textProperty().set(port.toString()); }
            checkNotNull(isWildcardRuleCheckBox).setSelected(isWildcard);
        }
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void okClose() {
        try {
            String filterTypeStr = checkNotNull(filterTypeComboBox).valueProperty().get();
            String host = checkNotNull(hostTextField).textProperty().get().trim();
            String portStr = checkNotNull(portTextField).textProperty().get().trim();
            Boolean isWildcard = checkNotNull(isWildcardRuleCheckBox).selectedProperty().get();

            if (!isWildcard && (host.contains("*") || host.contains("?"))) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Non-wildcard rule can't have wildcards (*, ?)", ButtonType.OK);
                alert.showAndWait();
                return;
            }

            if (isWildcard) {
                if (!host.contains("*") && !host.contains("?")) {
                    Alert alert = new Alert(Alert.AlertType.ERROR, "Wildcard rule must have wildcards (*, ?)", ButtonType.OK);
                    alert.showAndWait();
                    return;
                }

                boolean hasNonWildcards = false;
                for (char c : host.toCharArray()) {
                    if (c != '*' && c != '?') {
                        hasNonWildcards = true;
                    }
                }

                if (!hasNonWildcards) {
                    Alert alert = new Alert(Alert.AlertType.ERROR, "Wildcard rule must have non-wildcard characters (*, ?)", ButtonType.OK);
                    alert.showAndWait();
                    return;
                }
            }

            int port = 1000;
            if (!StringUtils.isBlank(portStr)) {
                try {
                    port = Integer.parseInt(portStr);
                } catch (Exception e) {
                    port = -1;
                }
            }

            Access access = null;
            try {
                if (filterTypeStr.equals(WHITELIST)) {
                    access = Access.ALLOW;
                } else if (filterTypeStr.equals(BLACKLIST)) {
                    access = Access.DENY;
                }
            } catch (Exception e) {
            }

            if ((access != Access.ALLOW && access != Access.DENY)) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION,
                        "Please specify valid filter type (WHITELIST ot BLACKLIST)", ButtonType.OK);
                alert.showAndWait();
            } else if (StringUtils.isBlank(host) && StringUtils.isBlank(portStr)) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION,
                        "Please specify host or port or both ", ButtonType.OK);
                alert.showAndWait();
            } else if (port <= 0) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION,
                        "Please specify no port (blank) or integer port > 0 (specified:" + portStr + ")", ButtonType.OK);
                alert.showAndWait();
            } else {
                if (!StringUtils.isBlank(host) && !StringUtils.isBlank(portStr)) {
                    trafficRule = new TrafficRule(ImmutableAddressRecord.builder()
                            .access(access)
                            .dstHost(host)
                            .dstPort(port)
                            .isWildcard(isWildcard)
                            .creationTimestamp(System.currentTimeMillis())
                            .build());
                    checkNotNull(stage).close();
                } else if (!StringUtils.isBlank(host) && StringUtils.isBlank(portStr)) {
                    trafficRule = new TrafficRule(ImmutableHostRecord.builder()
                            .access(access)
                            .dstHost(host)
                            .isWildcard(isWildcard)
                            .creationTimestamp(System.currentTimeMillis())
                            .build());
                    checkNotNull(stage).close();
                } else if (StringUtils.isBlank(host) && !StringUtils.isBlank(portStr)) {
                    trafficRule = new TrafficRule(ImmutablePortRecord.builder()
                            .access(access)
                            .dstPort(port)
                            .creationTimestamp(System.currentTimeMillis())
                            .build());
                    checkNotNull(stage).close();
                }
            }
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "WorkspaceDialog close Error: " + e, ButtonType.OK);
            LOGGER.error("WorkspaceDialog close Error:", e);
            alert.showAndWait();
        }
    }

    @Nullable public TrafficRule getTrafficRule() {
        return trafficRule;
    }

    public void textFieldKeyRelease(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER){
            okClose();
        }
    }
}
