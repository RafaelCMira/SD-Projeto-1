package sd2223.trab1.client;

import sd2223.trab1.Discovery;
import sd2223.trab1.api.java.Feeds;
import sd2223.trab1.client.REST.RestFeedsClient;
import sd2223.trab1.client.SOAP.SoapFeedsClient;

import java.net.URI;

public class FeedsClientFactory {

    private static final String SERVICE = "feeds";
    private static final String REST = "/rest";
    private static final String SOAP = "/soap";
    private static final int MIN_REPLIES = 1;

    public static Feeds get(String domain) {
        Discovery discovery = Discovery.getInstance();
        String serviceDomain = SERVICE + "." + domain;
        URI[] uris = discovery.knownUrisOf(serviceDomain, MIN_REPLIES);
        URI serverURI = uris[0];
        var uriString = serverURI.toString();

        if (uriString.endsWith(REST))
            return new RestFeedsClient(serverURI);
        else if (uriString.endsWith(SOAP))
            return new SoapFeedsClient(serverURI);
        else
            throw new RuntimeException("Unknown service type..." + uriString);
    }
}
