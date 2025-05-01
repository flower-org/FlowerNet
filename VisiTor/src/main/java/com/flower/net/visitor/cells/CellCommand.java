package com.flower.net.visitor.cells;

public enum CellCommand {
    /** Command constant for a PADDING type cell. */
    PADDING(0),

    /** Command constant for a CREATE type cell. */
    CREATE(1),

    /** Command constant for a CREATED type cell. */
    CREATED(2),

    /** Command constant for a RELAY type cell. */
    RELAY(3),

    /** Command constant for a DESTROY type cell. */
    DESTROY(4),

    /** Command constant for a CREATE_FAST type cell. */
    CREATE_FAST(5),

    /** Command constant for a CREATED_FAST type cell. */
    CREATED_FAST(6),

    /** Command constant for a VERSIONS type cell. */
    VERSIONS(7),

    /** Command constant for a NETINFO type cell. */
    NETINFO(8),

    /** Command constant for a RELAY_EARLY type cell. */
    RELAY_EARLY(9),

    VPADDING(128),
    CERTS(129),
    AUTH_CHALLENGE(130),
    AUTHENTICATE(131),
    AUTHORIZE(132);

    public final int code;
    CellCommand(int code) {
        this.code = code;
    }

    public static CellCommand fromCode(int code) {
        switch(code) {
            case 0: return PADDING;
            case 1: return CREATE;
            case 2: return CREATED;
            case 3: return RELAY;
            case 4: return DESTROY;
            case 5: return CREATE_FAST;
            case 6: return CREATED_FAST;
            case 7: return VERSIONS;
            case 8: return NETINFO;
            case 9: return RELAY_EARLY;

            case 128: return VPADDING;
            case 129: return CERTS;
            case 130: return AUTH_CHALLENGE;
            case 131: return AUTHENTICATE;
            case 132: return AUTHORIZE;

            default: throw new UnsupportedOperationException("CellCommand code:" + code + " not supported");
        }
    }
}
