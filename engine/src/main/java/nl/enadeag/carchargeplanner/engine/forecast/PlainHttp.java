package nl.enadeag.carchargeplanner.engine.forecast;

/**
 * Where a typed server address may use plain http rather than https. Over plain http an API key travels readable,
 * which is safe only when it does not leave this computer; and a client that cannot connect over plain http at all
 * should not accept an address it could never reach. So the client that checks a typed address chooses, and always
 * says which: there is no default.
 */
public enum PlainHttp {

    /** Plain http only to this computer itself (localhost, 127.0.0.1 or [::1]); https anywhere. */
    TO_THIS_COMPUTER,

    /** Never, not even to this computer itself: https only. */
    NEVER
}
