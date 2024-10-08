package sd2223.trab1.server.REST.Feeds;

import jakarta.inject.Singleton;
import sd2223.trab1.api.Message;
import sd2223.trab1.api.java.Feeds;
import sd2223.trab1.api.rest.FeedsService;
import sd2223.trab1.server.REST.RestResource;
import sd2223.trab1.server.java.JavaFeeds;

import java.util.List;

@Singleton
public class RestFeedsResource extends RestResource implements FeedsService {

    final Feeds impl;

    public RestFeedsResource() {
        this.impl = new JavaFeeds();
    }

    public RestFeedsResource(String domain, int serverId) {
        this.impl = new JavaFeeds(domain, serverId);
    }

    @Override
    public long postMessage(String user, String pwd, Message msg) {
        return super.fromJavaResult(impl.postMessage(user, pwd, msg));
    }

    @Override
    public void removeFromPersonalFeed(String user, long mid, String pwd) {
        super.fromJavaResult(impl.removeFromPersonalFeed(user, mid, pwd));
    }

    @Override
    public Message getMessage(String user, long mid) {
        return super.fromJavaResult(impl.getMessage(user, mid));
    }

    @Override
    public List<Message> getMessages(String user, long time) {
        return super.fromJavaResult(impl.getMessages(user, time));
    }

    @Override
    public void subUser(String user, String userSub, String pwd) {
        super.fromJavaResult(impl.subUser(user, userSub, pwd));
    }

    @Override
    public void unsubscribeUser(String user, String userSub, String pwd) {
        super.fromJavaResult(impl.unsubscribeUser(user, userSub, pwd));
    }

    @Override
    public List<String> listSubs(String user) {
        return super.fromJavaResult(impl.listSubs(user));
    }

    @Override
    public void deleteUserFeed(String user) {
        super.fromJavaResult(impl.deleteUserFeed(user));
    }

    @Override
    public void propagateUnsub(String user, String userSub) {
        super.fromJavaResult(impl.propagateUnsub(user, userSub));
    }

    @Override
    public void propagateSub(String user, String userSub) {
        super.fromJavaResult(impl.propagateSub(user, userSub));
    }

    @Override
    public void propagateMsg(String[] users, Message msg) {
        super.fromJavaResult(impl.propagateMsg(users, msg));
    }


}
