package sd2223.trab1.server.REST.Feeds;

import jakarta.inject.Singleton;
import sd2223.trab1.api.Message;
import sd2223.trab1.api.rest.FeedsService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class RestFeedsResource implements FeedsService {

    // Dominio
    private String domain;

    // Id
    private int serverId;

    private final Map<String, Message> allMessages;

    public RestFeedsResource() {
        allMessages = new HashMap<>();
    }

    public RestFeedsResource(String domain, int serverId) {
        this.domain = domain;
        this.serverId = serverId;
        allMessages = new HashMap<>();
    }

    @Override
    public long postMessage(String user, String pwd, Message msg) {
        return 0;
    }

    @Override
    public void removeFromPersonalFeed(String user, long mid, String pwd) {

    }

    @Override
    public Message getMessage(String user, long mid) {
        return null;
    }

    @Override
    public List<Message> getMessages(String user, long time) {
        return null;
    }

    @Override
    public void subUser(String user, String userSub, String pwd) {

    }

    @Override
    public void unsubscribeUser(String user, String userSub, String pwd) {

    }

    @Override
    public List<String> listSubs(String user) {
        return null;
    }
}
