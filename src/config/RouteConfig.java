package config;

import java.util.*;

public class RouteConfig {
    public String path;
    public String root;
    public List<String> methods = new ArrayList<>();
    public boolean directoryListing;
    public String indexFile;
    public boolean autoRedirect = true;
    public String redirect;
    public int redirectCode;
    public String cgiExtension;
    public String cgiPath;
}
