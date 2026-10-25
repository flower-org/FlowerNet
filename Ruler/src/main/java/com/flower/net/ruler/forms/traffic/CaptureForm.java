package com.flower.net.ruler.forms.traffic;

import com.flower.net.conntrack.ConnectionFilter;

/** A view in the Capture tab pane of TrafficControlForm that receives captured requests */
public interface CaptureForm {
    /** Target for the form's Allow/Deny actions */
    void setConnectionFilter(ConnectionFilter connectionFilter);

    /** Thread-safe. Callers may check this before building a CapturedRequest. */
    boolean isCaptureEnabled();

    /** Thread-safe: may be called from connection threads */
    void addCapture(CapturedRequest capturedRequest);
}
