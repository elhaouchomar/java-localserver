package utils;

import java.util.LinkedHashMap;
import java.util.Map;

public class Cookie {

    private final String name;
    private final String value;
    private String path = "/";
    private String domain;
    private long maxAge = -1;
    private boolean httpOnly;
    private boolean secure;
    private String sameSite; // "Strict", "Lax", or "None"

    public Cookie(String name, String value) {
        this.name = name;
        this.value = value;
    }

    /** Parse the Cookie request header into a name→value map. */
    public static Map<String, String> parse(String cookieHeader) {
        Map<String, String> cookies = new LinkedHashMap<>();
        if (cookieHeader == null || cookieHeader.isEmpty()) return cookies;

        for (String pair : cookieHeader.split(";")) {
            pair = pair.trim();
            int eq = pair.indexOf('=');
            if (eq > 0) {
                cookies.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }
        }
        return cookies;
    }

    /** Produce the value for a Set-Cookie response header. */
    public String toSetCookieHeader() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append("=").append(value);
        if (path != null) sb.append("; Path=").append(path);
        if (domain != null) sb.append("; Domain=").append(domain);
        if (maxAge >= 0) sb.append("; Max-Age=").append(maxAge);
        if (httpOnly) sb.append("; HttpOnly");
        if (secure) sb.append("; Secure");
        if (sameSite != null) sb.append("; SameSite=").append(sameSite);
        return sb.toString();
    }

    public String getName()  { return name; }
    public String getValue() { return value; }

    public Cookie path(String path)         { this.path = path;         return this; }
    public Cookie domain(String domain)     { this.domain = domain;     return this; }
    public Cookie maxAge(long maxAge)       { this.maxAge = maxAge;     return this; }
    public Cookie httpOnly(boolean v)       { this.httpOnly = v;        return this; }
    public Cookie secure(boolean v)         { this.secure = v;          return this; }
    public Cookie sameSite(String sameSite) { this.sameSite = sameSite; return this; }
}
