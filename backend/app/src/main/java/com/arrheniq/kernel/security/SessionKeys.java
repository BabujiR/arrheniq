package com.arrheniq.kernel.security;

/** Names of the session attributes owned by the security kernel (values are small and serializable). */
final class SessionKeys {

    static final String LOGIN_AT = "arq.loginAt";
    static final String LAST_ACTIVITY = "arq.lastActivity";
    static final String IDLE_LOCK = "arq.idleLock";
    static final String LOCKED = "arq.locked";
    static final String RETURN_TO = "arq.returnTo";
    static final String PENDING_REAUTH = "arq.pendingReauth";
    static final String REAUTH_EVIDENCE = "arq.reauthEvidence";
    static final String LOGIN_HINT = "arq.loginHint";

    private SessionKeys() { }
}
