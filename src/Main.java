import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import config.Config;
import config.ConfigLoader;
import config.RouteConfig;
import router.Router;

public class Main {
    public static void main(String[] args) throws Exception {
        try {
            List<Config> configs = ConfigLoader.loadConfig("config.json");
            Server server = new Server(configs);
            server.start();
        } catch (IOException error) {
            System.err.println("Failed to start server: " + error);
            error.printStackTrace();
        }
    }
}
