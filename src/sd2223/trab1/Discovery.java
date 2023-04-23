package sd2223.trab1;

import java.net.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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
     * @param serviceDomain - serviceName and domain (format service.domain)
     * @param minReplies    - minimum number of requested URIs. Blocks until the number is satisfied.
     * @return array with the discovered URIs for the given service name.
     */
    public URI[] knownUrisOf(String serviceDomain, int minReplies);

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
    private static final String DELIMITER_TAB = "\t";

    // Used separate the domain from the rest
    private static final String DELIMITER_2_DOTS = ":";

    private static final int MAX_DATAGRAM_SIZE = 65536;

    // Stores the received announcements by serviceName (format service.domain)
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
        Log.info(String.format("Starting Discovery announcements on: %s for: %s -> %s\n", DISCOVERY_ADDR, serviceName + "." + domain,
                serviceURI));
        var pktBytes = String.format("%s%s%s%s%s", domain, DELIMITER_2_DOTS, serviceName, DELIMITER_TAB, serviceURI).getBytes();
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
    public URI[] knownUrisOf(String serviceDomain, int minEntries) {
        // Wait for a minimum number of replies
        while (announcements.get(serviceDomain) == null) {
            try {
                Thread.sleep(DISCOVERY_RETRY_TIMEOUT);
            } catch (InterruptedException e) {
                // ignore
            }
        }

        while (announcements.get(serviceDomain).size() < minEntries) {
            try {
                Thread.sleep(DISCOVERY_RETRY_TIMEOUT);
            } catch (InterruptedException e) {
                // ignore
            }
        }

        /*
        while (announcements.getOrDefault(serviceDomain, Collections.emptyList()).size() < minEntries) {
            try {
                Thread.sleep(DISCOVERY_RETRY_TIMEOUT);
            } catch (InterruptedException e) {
            }
        }*/

        // Return the known URIs for the service name
        //   List<URI> uris = announcements.getOrDefault(serviceName, Collections.emptyList());
        List<URI> uris = announcements.get(serviceDomain);

        URI[] array = new URI[uris.size()];
        uris.toArray(array);
        return array;
    }

    private void startListener() {
        new Thread(() -> {
            try (var ms = new MulticastSocket(DISCOVERY_ADDR.getPort())) {
                ms.joinGroup(DISCOVERY_ADDR, NetworkInterface.getByInetAddress(InetAddress.getLocalHost()));
                for (; ; ) {
                    try {
                        var pkt = new DatagramPacket(new byte[MAX_DATAGRAM_SIZE], MAX_DATAGRAM_SIZE);
                        ms.receive(pkt);

                        var msg = new String(pkt.getData(), 0, pkt.getLength());
                        //   Log.info(String.format("Received: %s", msg));

                        var parts = msg.split(DELIMITER_TAB);

                        var parts1 = parts[0].split(DELIMITER_2_DOTS);

                        var domain = parts1[0];
                        var service = parts1[1];

                        var serviceDomain = service + "." + domain;
                        URI uri = URI.create(parts[1]);

                        List<URI> list = announcements.get(serviceDomain);
                        if (list == null) {
                            list = new LinkedList<>();
                            announcements.put(serviceDomain, list);
                        }
                        list.add(uri);
                        //announcements.computeIfAbsent(serviceDomain, k -> new ArrayList<>()).add(uri);
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