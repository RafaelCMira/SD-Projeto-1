package sd2223.trab1;

import java.net.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * <p>A class interface to perform service discovery based on periodic
 * announcements over multicast communication.</p>
 */

public interface Discovery {

    /**
     * Used to announce the URI of the given service name.
     *
     * @param domain      - the domain of the service
     * @param serviceName - the name of the service
     * @param serviceURI  - the uri of the service
     */
    public void announce(String domain, String serviceName, String serviceURI);

    /**
     * Get discovered URIs for a given service name
     *
     * @param domainService - domain and name of the service (format domain:serviceName)
     * @param minReplies    - minimum number of requested URIs. Blocks until the number is satisfied.
     * @return array with the discovered URIs for the given service name.
     */
    public URI[] knownUrisOf(String domainService, int minReplies);

    /**
     * Get the instance of the Discovery service
     *
     * @return the singleton instance of the Discovery service
     */
    public static Discovery getInstance() {
        return DiscoveryImpl.getInstance();
    }
}

/**
 * Implementation of the multicast discovery service
 */
class DiscoveryImpl implements Discovery {
    private static Logger Log = Logger.getLogger(Discovery.class.getName());

    static final int DISCOVERY_RETRY_TIMEOUT = 5000;

    // Alterei o tempo para 10 segundos para se poder interagir na linha de comandos mas isto tem de estar mais baixo (tipo 1 ou 2)
    static final int DISCOVERY_ANNOUNCE_PERIOD = 1000;

    // The pre-aggreed multicast endpoint assigned to perform discovery.
    static final InetSocketAddress DISCOVERY_ADDR = new InetSocketAddress("224.0.0.1", 5000);

    // Used separate the two fields that make up a service announcement.
    private static final String DELIMITER = "\t";

    // Used separate the domain from the rest
    private static final String DELIMITER_2 = ":";

    private static final int MAX_DATAGRAM_SIZE = 65536;

    // Stores the received announcements by serviceName. (Adicionado)
    private Map<String, List<URI>> announcements = new HashMap<>();

    private static Discovery singleton;

    synchronized static Discovery getInstance() {
        if (singleton == null) {
            singleton = new DiscoveryImpl();
        }
        return singleton;
    }

    private DiscoveryImpl() {
        this.startListener();
    }

    @Override
    public void announce(String domain, String serviceName, String serviceURI) {
        // Log.info(String.format("Starting Discovery announcements on: %s for: %s -> %s\n", DISCOVERY_ADDR, serviceName, serviceURI));

        var pktBytes = String.format("%s%s%s%s%s", domain, DELIMITER_2, serviceName, DELIMITER, serviceURI).getBytes();
        var pkt = new DatagramPacket(pktBytes, pktBytes.length, DISCOVERY_ADDR);

        // start thread to send periodic announcements
        new Thread(() -> {
            try (var ds = new DatagramSocket()) {
                while (true) {
                    try {
                        ds.send(pkt);
                        Thread.sleep(DISCOVERY_ANNOUNCE_PERIOD);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }


    @Override
    public URI[] knownUrisOf(String serviceName, int minEntries) {
        // Wait for a minimum number of replies
        while (announcements.getOrDefault(serviceName, Collections.emptyList()).size() < minEntries) {
            try {
                Thread.sleep(DISCOVERY_RETRY_TIMEOUT);
            } catch (InterruptedException e) {
            }
        }

        // Return the known URIs for the service name
        //   List<URI> uris = announcements.getOrDefault(serviceName, Collections.emptyList());
        List<URI> uris = announcements.get(serviceName);
        return uris.toArray(new URI[0]);
    }

    private void startListener() {
        // Log.info(String.format("Starting discovery on multicast group: %s, port: %d\n", DISCOVERY_ADDR.getAddress(), DISCOVERY_ADDR.getPort()));

        new Thread(() -> {
            try (var ms = new MulticastSocket(DISCOVERY_ADDR.getPort())) {
                ms.joinGroup(DISCOVERY_ADDR, NetworkInterface.getByInetAddress(InetAddress.getLocalHost()));
                for (; ; ) {
                    try {
                        var pkt = new DatagramPacket(new byte[MAX_DATAGRAM_SIZE], MAX_DATAGRAM_SIZE);
                        ms.receive(pkt);

                        var msg = new String(pkt.getData(), 0, pkt.getLength());
                        // Log.info(String.format("Received: %s", msg));

                        var serviceAndDomain = msg.split(DELIMITER_2);
                        if (serviceAndDomain.length == 2) {
                            var domain = serviceAndDomain[0];
                            var parts = serviceAndDomain[1].split(DELIMITER);

                            if (parts.length == 2) {
                                var serviceName = parts[0];
                                var uri = URI.create(parts[1]);
                                String serviceDomain = serviceName + "." + domain;
                                // Store the announcement for the service name
                                announcements.computeIfAbsent(serviceDomain, k -> new ArrayList<>()).add(uri);
                            }
                        }
                    } catch (Exception x) {
                        x.printStackTrace();
                    }
                }
            } catch (Exception x) {
                x.printStackTrace();
            }
        }).start();
    }
}