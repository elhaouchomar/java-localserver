package utils;

public class TimeoutTracker {

    private final long timeoutMs;
    private long lastActivityAt;

    public TimeoutTracker(long timeoutMs) {
        this.timeoutMs = timeoutMs;
        this.lastActivityAt = System.currentTimeMillis();
    }

    /** Reset the inactivity timer. */
    public void touch() {
        this.lastActivityAt = System.currentTimeMillis();
    }

    /** Returns true if no activity has occurred within the timeout window. */
    public boolean isTimedOut() {
        return System.currentTimeMillis() - lastActivityAt > timeoutMs;
    }

}
