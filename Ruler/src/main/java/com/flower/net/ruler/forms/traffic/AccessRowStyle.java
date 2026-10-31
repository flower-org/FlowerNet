package com.flower.net.ruler.forms.traffic;

import com.flower.net.config.access.Access;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.css.PseudoClass;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;

import javax.annotation.Nullable;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkNotNull;

/** Tints table rows by Access: pale green for Allow, pale red for Deny */
final class AccessRowStyle {
    private static final PseudoClass ALLOW = PseudoClass.getPseudoClass("allow");
    private static final PseudoClass DENY = PseudoClass.getPseudoClass("deny");
    private static final String STYLESHEET =
            checkNotNull(AccessRowStyle.class.getResource("AccessRowStyle.css")).toExternalForm();

    private AccessRowStyle() {}

    /** For items whose access never changes */
    static <T> void install(TableView<T> table, Function<T, Access> access) {
        installObservable(table, item -> new ReadOnlyObjectWrapper<>(access.apply(item)));
    }

    /** For items whose access can change while displayed */
    static <T> void installObservable(TableView<T> table, Function<T, ObservableValue<Access>> access) {
        table.getStylesheets().add(STYLESHEET);
        table.setRowFactory(t -> new TableRow<>() {
            private @Nullable ObservableValue<Access> observed;
            private final ChangeListener<Access> listener = (obs, oldValue, newValue) -> setAccess(newValue);

            @Override
            protected void updateItem(@Nullable T item, boolean empty) {
                super.updateItem(item, empty);
                if (observed != null) {
                    observed.removeListener(listener);
                    observed = null;
                }
                if (empty || item == null) {
                    setAccess(null);
                } else {
                    observed = access.apply(item);
                    observed.addListener(listener);
                    setAccess(observed.getValue());
                }
            }

            private void setAccess(@Nullable Access value) {
                pseudoClassStateChanged(ALLOW, value == Access.ALLOW);
                pseudoClassStateChanged(DENY, value == Access.DENY);
            }
        });
    }
}
