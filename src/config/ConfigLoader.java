package config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class ConfigLoader {
    private final String content;
    private int cursor = 0;

    public static List<Config> loadConfig(String path) throws IOException {
        String raw = new String(Files.readAllBytes(Paths.get(path)));
        // Strip comments but keep the JSON structure intact
        // String content = raw.replaceAll("//.*|/\\*.*?\\*/", "");

        ConfigLoader loader = new ConfigLoader(raw);
        List<Config> configs = loader.parseRoot();

        validateConfiguration(configs);

        System.out.println("[CONFIG] Configuration validated successfully.");
        return configs;
    }

    private ConfigLoader(String content) {
        this.content = content.trim();
    }

    private List<Config> parseRoot() throws IOException {
        expectChar('{');
        List<Config> configs = null;

        while (peek() != '}') {
            String key = nextKey();
            expectChar(':');
            if (key.equals("servers")) {
                configs = parseServersArray();
            } else {
                skipValue();
            }

            // Strict Comma Handling
            if (peek() == ',') {
                cursor++;
                if (peek() == '}')
                    throw new IOException("STRICT ERROR: Trailing comma at root level.");
            } else if (peek() != '}') {
                throw new IOException("STRICT ERROR: Missing comma after key '" + key + "'.");
            }
        }
        expectChar('}');

        if (configs == null)
            throw new IOException("CRITICAL: Root 'servers' array is missing.");
        return configs;
    }

    private List<Config> parseServersArray() throws IOException {
        expectChar('[');
        List<Config> list = new ArrayList<>();
        while (peek() != ']') {
            list.add(parseServerObject());

            if (peek() == ',') {
                cursor++;
                if (peek() == ']')
                    throw new IOException("STRICT ERROR: Trailing comma in servers array.");
            } else if (peek() != ']') {
                throw new IOException("STRICT ERROR: Missing comma between server objects in array.");
            }
        }
        expectChar(']');
        return list;
    }

    private Config parseServerObject() throws IOException {
        expectChar('{');
        Config sc = new Config();

        while (peek() != '}') {
            String key = nextKey();
            expectChar(':');

            switch (key) {
                case "host":
                    sc.host = nextString();
                    break;
                case "server_name":
                    sc.serverName = nextString();
                    break;
                case "server_aliases":
                    sc.serverAliases.addAll(parseStringArray());
                    break;
                case "root":
                    sc.root = nextString();
                    break;
                case "is_default":
                    sc.isDefault = Boolean.parseBoolean(nextRawValue());
                    break;
                case "client_max_body_size":
                    sc.maxBodySize = Long.parseLong(nextRawValue());
                    break;
                case "ports":
                    try {
                        sc.ports.addAll(parseIntegerArray());

                    } catch (Exception e) {
                        skipValue();
                        sc.ports.clear();
                    }
                    // if (sc.ports.isEmpty())
                    // throw new IOException("CRITICAL: Server 'ports' array cannot be empty.");
                    break;
                case "error_pages":
                    sc.errorPages.putAll(parseErrorPages());
                    break;
                case "routes":
                    sc.routes = parseRoutes();
                    break;
                default:
                    skipValue();
                    break;
            }

            if (peek() == ',') {
                cursor++;
                if (peek() == '}')
                    throw new IOException("STRICT ERROR: Trailing comma in server object after '" + key + "'.");
            } else if (peek() != '}') {
                throw new IOException("STRICT ERROR: Missing comma after property '" + key + "' in server object.");
            }
        }
        expectChar('}');

        // Final sanity check for mandatory fields
        if (sc.host == null || sc.root == null || sc.ports.isEmpty()) {
            // throw new IOException(
            // "CRITICAL: Server configuration incomplete. Ensure host, root, and ports are
            // defined.");
            return sc; // Return empty list to indicate invalid server config
        }
        return sc;
    }

    private Map<Integer, String> parseErrorPages() throws IOException {
        expectChar('{');
        Map<Integer, String> errors = new HashMap<>();
        while (peek() != '}') {
            String key = nextKey();
            expectChar(':');
            errors.put(Integer.parseInt(key), nextString());

            if (peek() == ',') {
                cursor++;
                if (peek() == '}')
                    throw new IOException("STRICT ERROR: Trailing comma in error_pages.");
            } else if (peek() != '}') {
                throw new IOException("STRICT ERROR: Missing comma in error_pages after code " + key);
            }
        }
        expectChar('}');
        return errors;
    }

    private List<RouteConfig> parseRoutes() throws IOException {
        expectChar('[');
        List<RouteConfig> routes = new ArrayList<>();
        while (peek() != ']') {
            routes.add(parseRouteObject());
            if (peek() == ',') {
                cursor++;
            } else if (peek() != ']') {
                throw new IOException("STRICT ERROR: Missing comma between route objects.");
            }
        }
        expectChar(']');
        return routes;
    }

    private RouteConfig parseRouteObject() throws IOException {
        expectChar('{');
        RouteConfig rc = new RouteConfig();
        while (peek() != '}') {
            String key = nextKey();
            expectChar(':');
            switch (key) {
                case "path":
                    rc.path = nextString();
                    break;
                case "methods":
                    try {
                        rc.methods.addAll(parseStringArray());
                    } catch (Exception e) {
                        skipValue();
                        rc.methods.clear();

                    }
                    break;
                case "root":
                    rc.root = nextString();
                    break;
                case "cgi_path":
                    rc.cgiPath = nextString();
                    break;
                case "cgi_extension":
                    rc.cgiExtension = nextString();
                    break;
                case "directory_listing":
                    rc.directoryListing = Boolean.parseBoolean(nextRawValue());
                    break;
                case "default_file":
                    rc.indexFile = nextString();
                    break;
                case "redirect":
                    rc.redirect = nextString();
                    break;
                case "redirect_code":
                    rc.redirectCode = Integer.parseInt(nextRawValue());
                    break;
                default:
                    skipValue();
                    break;
            }
            if (peek() == ',') {
                cursor++;
            } else if (peek() != '}') {
                throw new IOException("STRICT ERROR: Missing comma in route object after '" + key + "'.");
            }
        }
        expectChar('}');
        return rc;
    }

    private String nextString() throws IOException {
        skipWhitespace();
        expectChar('"');
        StringBuilder sb = new StringBuilder();
        while (cursor < content.length()) {
            char c = content.charAt(cursor++);
            if (c == '"')
                return sb.toString();
            if (c == '\\') {
                if (cursor < content.length())
                    sb.append(content.charAt(cursor++));
            } else {
                sb.append(c);
            }
        }
        throw new IOException("STRICT ERROR: Unterminated string literal.");
    }

    private String nextKey() throws IOException {
        return nextString();
    }

    private String nextRawValue() throws IOException {
        skipWhitespace();
        int start = cursor;
        while (cursor < content.length() && !"{}[],:\" \n\r\t".contains(String.valueOf(content.charAt(cursor)))) {
            cursor++;
        }
        String val = content.substring(start, cursor);
        if (val.isEmpty())
            throw new IOException("STRICT ERROR: Expected a value (number/boolean) at " + start);
        return val;
    }

    private List<Integer> parseIntegerArray() throws IOException {
        expectChar('[');
        List<Integer> list = new ArrayList<>();

        while (peek() != ']') {
            String value;

            if (peek() == '"') {
                // Accept string numbers like "8080"
                value = nextString();
            } else {
                // Accept raw numbers like 8080
                value = nextRawValue();
            }

            try {
                list.add(Integer.parseInt(value));
            } catch (NumberFormatException e) {
                throw new IOException(
                        "STRICT ERROR: Port values must be integers. Found: " + value);
            }

            if (peek() == ',') {
                cursor++;
            } else if (peek() != ']') {
                throw new IOException("STRICT ERROR: Missing comma in ports array.");
            }
        }

        expectChar(']');
        return list;
    }

    private List<String> parseStringArray() throws IOException {
        expectChar('[');
        List<String> list = new ArrayList<>();
        while (peek() != ']') {
            list.add(nextString());
            if (peek() == ',') {
                cursor++;
            } else if (peek() != ']') {
                throw new IOException("STRICT ERROR: Missing comma in string array.");
            }
        }
        expectChar(']');
        return list;
    }

    private void skipValue() throws IOException {
        skipWhitespace();
        char c = peek();
        if (c == '"')
            nextString();
        else if (c == '{') {
            cursor++;
            int depth = 1;
            while (depth > 0 && cursor < content.length()) {
                char next = content.charAt(cursor++);
                if (next == '{')
                    depth++;
                else if (next == '}')
                    depth--;
            }
        } else if (c == '[') {
            cursor++;
            int depth = 1;
            while (depth > 0 && cursor < content.length()) {
                char next = content.charAt(cursor++);
                if (next == '[')
                    depth++;
                else if (next == ']')
                    depth--;
            }
        } else {
            nextRawValue();
        }
    }

    private void expectChar(char expected) throws IOException {
        skipWhitespace();
        if (cursor >= content.length() || content.charAt(cursor) != expected) {
            throw new IOException("STRICT JSON ERROR: Expected '" + expected +
                    "' but found '" + (cursor < content.length() ? content.charAt(cursor) : "EOF") +
                    "' at position " + cursor);
        }
        cursor++;
    }

    private char peek() {
        skipWhitespace();
        return cursor < content.length() ? content.charAt(cursor) : '\0';
    }

    private void skipWhitespace() {
        while (cursor < content.length() && Character.isWhitespace(content.charAt(cursor))) {
            cursor++;
        }
    }

    private static void validateConfiguration(List<Config> configs) throws IOException {
        Set<String> servename = new HashSet<>();
        for (int j = 0; j < configs.size(); j++) {
            Config s = configs.get(j);

            if (s.ports.isEmpty()) {
                System.err.println("CRITICAL: No valid ports for server '" + s.serverName +
                        "'. Removing server.");
                configs.remove(j);
                j--;
                continue;
            }

            if (s.serverName == null) {
                System.err.println(
                        "CRITICAL: server_name is missing for a server. Assigning default name 'default_server'.");
                s.serverName = "server_" + j;
            }

            if (s.host == null || s.host.trim().isEmpty()) {
                System.err.println(
                        "CRITICAL: host is missing for server '" + s.serverName
                                + "'. Removing this server from configuration.");
                configs.remove(j);
                j--;
                continue;
            } else {
                s.host = s.host.trim();
            }

            if (s.host.equals("localhost")) {
                s.host = "127.0.0.1";
            }

            if (!servename.add(s.serverName)) {
                System.err.println("CRITICAL CONFIG CONFLICT: Duplicate server_name '" + s.serverName + "'.");
                configs.remove(j);
                j--;
                continue;

            }
            Set<String> seenBindings = new HashSet<>();
            if (s.maxBodySize < 0) {
                System.err.println("CRITICAL: client_max_body_size cannot be negative. " +
                        "Server: " + s.serverName + ", Value: " + s.maxBodySize + ". Resetting to 0 (unlimited).");
                s.maxBodySize = 0;

            }

            String host = s.host.equals("localhost") ? "127.0.0.1" : s.host;
            for (int i = 0; i < s.ports.size(); i++) {
                int port = s.ports.get(i);
                if (port < 1 || port > 65535) {
                    System.err.println("CRITICAL: Invalid port number " + port +
                            " in server '" + s.serverName + "'. " +
                            "Ports must be between 1 and 65535.");
                    s.ports.remove(i);
                    i--;
                    continue;
                }

                String binding = host + ":" + port;
                if (!seenBindings.add(binding)) {
                    System.err.println("CRITICAL CONFIG CONFLICT: The port binding " + binding +
                            " is duplicated");
                    s.ports.remove(i);
                    i--;
                    continue;
                }
            }

            // // Check routes exist
            if (s.routes == null || s.routes.isEmpty()) {
                configs.remove(j);
                System.err.println(
                        "CRITICAL: No routes defined for server '" + s.serverName + "'. Server will be ignored.");
                j--;
                continue;
            }

            for (int i = 0; i < s.routes.size(); i++) {
                RouteConfig r = s.routes.get(i);
                if (r.path == null) {
                    System.err.println(
                            "CRITICAL: Route path is missing in server '" + s.serverName + "'. Route will be ignored.");
                    s.routes.remove(i);
                    i--;
                    continue;
                }
                if (r.methods == null || r.methods.isEmpty()) {
                    System.err.println("CRITICAL: No methods defined for route '" + r.path +
                            "' in server '" + s.serverName + "'. Route will be ignored.");
                    s.routes.remove(i);
                    i--;
                    continue;
                }
            }

        }

    }
}