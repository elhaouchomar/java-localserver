package config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Config {
    public String host;
    public List<Integer> ports = new ArrayList<>();
    public String serverName;
    public List<String> serverAliases = new ArrayList<>(); // Alternative hostnames for this virtual host
    public boolean isDefault;
    public String root;
    public long maxBodySize;
    public Map<Integer, String> errorPages = new HashMap<>();
    public List<RouteConfig> routes = new ArrayList<>();

    /**
     * Checks if the given hostname matches this server's primary name or any of its
     * aliases
     * Also validates that the port (if specified) matches one of this server's
     * ports
     * 
     * @param hostHeader The Host header value (e.g., "example.com" or
     *                   "example.com:8080")
     * @return true if hostname and port match this server, false otherwise
     */
    public boolean matchesHostname(String hostHeader) {
        if (hostHeader == null) {
            return isDefault;
        }

        // Parse hostname and port from Host header
        String hostname = hostHeader;
        Integer requestPort = null;

        if (hostHeader.contains(":")) {
            int colonIndex = hostHeader.indexOf(":");
            hostname = hostHeader.substring(0, colonIndex);
            try {
                requestPort = Integer.parseInt(hostHeader.substring(colonIndex + 1));
            } catch (NumberFormatException e) {
                // Invalid port format
                return false;
            }
        }

        // Check if hostname matches
        boolean hostnameMatches = false;
        if (serverName != null && serverName.equalsIgnoreCase(hostname)) {
            hostnameMatches = true;
        } else {
            for (String alias : serverAliases) {
                if (alias.equalsIgnoreCase(hostname)) {
                    hostnameMatches = true;
                    break;
                }
            }
        }

        if (!hostnameMatches) {
            return false;
        }

        // If a port was specified, validate it against this server's ports
        if (requestPort != null) {
            return ports.contains(requestPort);
        }

        // If no port specified in Host header, accept the match
        return true;
    }
}