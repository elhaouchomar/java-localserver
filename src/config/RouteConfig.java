package config;

import java.util.Set;

public class RouteConfig {
    public String root;
    public Set<String> allowedMethods;
    public boolean directoryListing;
    public String indexFile;
    public String redirectTo;
    public boolean autoRedirect = true;
    public boolean isCgi;
}
