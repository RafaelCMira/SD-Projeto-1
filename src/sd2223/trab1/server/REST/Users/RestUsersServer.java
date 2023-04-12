package sd2223.trab1.server.REST.Users;

import org.glassfish.jersey.jdkhttp.JdkHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import sd2223.trab1.Discovery;

import java.net.InetAddress;
import java.net.URI;
import java.util.logging.Logger;

public class RestUsersServer {

    private static Logger Log = Logger.getLogger(RestUsersServer.class.getName());

    static {
        System.setProperty("java.net.preferIPv4Stack", "true");
    }

    public static final int PORT = 8080;
    public static final String SERVICE = "users";
    private static final String SERVER_URI_FMT = "http://%s:%s/rest";

    public static void main(String[] args) {

        String domain = args[0];

        try {
            ResourceConfig config = new ResourceConfig();
            config.register(RestUsersResource.class);
            // config.register(CustomLoggingFilter.class);

            String ip = InetAddress.getLocalHost().getHostAddress();
            String serverURI = String.format(SERVER_URI_FMT, ip, PORT);
            JdkHttpServerFactory.createHttpServer(URI.create(serverURI), config);

            Discovery discovery = Discovery.getInstance();
            discovery.announce(domain, SERVICE, serverURI);

            //  Log.info(String.format("%s.%s Server ready @ %s\n", SERVICE, domain, serverURI));

        } catch (Exception e) {
            Log.severe(e.getMessage());
        }

    }
}
