package sd2223.trab1.client;

import sd2223.trab1.Discovery;
import sd2223.trab1.api.java.Users;
import sd2223.trab1.client.REST.RestUsersClient;
import sd2223.trab1.client.SOAP.SoapUsersClient;

import java.net.URI;

public class UsersClientFactory {
    private static final String SERVICE = "users";
    private static final String REST = "/rest";
    private static final String SOAP = "/soap";
    private static final int MIN_REPLIES = 1;

    public static Users get(String domain) {
        Discovery discovery = Discovery.getInstance();
        String serviceDomain = SERVICE + "." + domain;
        URI[] uris = discovery.knownUrisOf(serviceDomain, MIN_REPLIES);
        URI serverURI = uris[0];
        var uriString = serverURI.toString();

        if (uriString.endsWith(REST))
            return new RestUsersClient(serverURI);
        else if (uriString.endsWith(SOAP))
            return new SoapUsersClient(serverURI);
        else
            throw new RuntimeException("Unknown service type..." + uriString);
    }
}
