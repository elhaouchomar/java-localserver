package utils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Session {

    private static final long SESSION_TIMEOUT_MS = 30L * 60 * 1000; // 30 minutes
    private static final Map<String, Session> store = new HashMap<>();

    private final String id;
    private final Map<String, Object> attributes = new HashMap<>();
    private long lastAccessed;

    private Session(String id) {
        this.id = id;
        this.lastAccessed = System.currentTimeMillis();
    }

    /**
     * Look up an existing, non-expired session by ID.
     * Returns null if not found or expired.
     */
    public static Session get(String sessionId) {
        if (sessionId == null) return null;
        Session s = store.get(sessionId);
        if (s != null && !s.isExpired()) {
            s.lastAccessed = System.currentTimeMillis();
            return s;
        }
        store.remove(sessionId);
        return null;
    }

    /** Create and store a brand-new session. */
    public static Session create() {
        String newId = UUID.randomUUID().toString();
        Session session = new Session(newId);
        store.put(newId, session);
        return session;
    }

    /**
     * Retrieve an existing session by ID, or create a new one.
     * Expired sessions are discarded.
     */
    public static Session getOrCreate(String sessionId) {
        Session s = get(sessionId);
        return s != null ? s : create();
    }

    /** Remove all expired sessions from the store. */
    public static void cleanup() {
        store.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - lastAccessed > SESSION_TIMEOUT_MS;
    }

    public String getId() { return id; }

    public void setAttribute(String key, Object value) { attributes.put(key, value); }

    public Object getAttribute(String key) { return attributes.get(key); }

    public void removeAttribute(String key) { attributes.remove(key); }

    /** Invalidate and remove this session from the store. */
    public void invalidate() { store.remove(id); }
}
