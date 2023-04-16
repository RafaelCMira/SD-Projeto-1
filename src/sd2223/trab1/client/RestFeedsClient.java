package sd2223.trab1.client;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import sd2223.trab1.api.Message;
import sd2223.trab1.api.java.Feeds;
import sd2223.trab1.api.java.Result;
import sd2223.trab1.api.rest.FeedsService;

import java.net.URI;
import java.util.List;

public class RestFeedsClient extends RestClient implements Feeds {

    final WebTarget target;

    public RestFeedsClient(URI serverURI) {
        super(serverURI);
        target = client.target(serverURI).path(FeedsService.PATH);
    }

    @Override
    public Result<Long> postMessage(String user, String pwd, Message msg) {
        return null;
    }

    @Override
    public Result<Void> removeFromPersonalFeed(String user, long mid, String pwd) {
        return null;
    }

    @Override
    public Result<Message> getMessage(String user, long mid) {
        return null;
    }

    @Override
    public Result<List<Message>> getMessages(String user, long time) {
        return null;
    }

    @Override
    public Result<Void> subUser(String user, String userSub, String pwd) {
        return null;
    }

    @Override
    public Result<Void> unsubscribeUser(String user, String userSub, String pwd) {
        return null;
    }

    @Override
    public Result<List<String>> listSubs(String user) {
        return null;
    }

    @Override
    public Result<Void> deleteUserFeed(String user) {
        return super.reTry(() -> clt_delUserFeed(user));
    }

    @Override
    public Result<Void> propagateSub(String user, String userSub) {
        return super.reTry(() -> clt_propagateSub(user, userSub));
    }

    @Override
    public Result<Void> propagateMsg(String user, Message msg) {
        return super.reTry(() -> clt_propagateMsg(user, msg));
    }

    private Result<Void> clt_delUserFeed(String user) {
        Response r = target
                .path(user)
                .request()
                .delete();

        return super.toJavaResult(r, Void.class);
    }

    private Result<Void> clt_propagateSub(String user, String userSub) {
        Response r = target
                .path("suber")
                .path(user)
                .path(userSub)
                .request()
                .post(Entity.entity(user, MediaType.APPLICATION_JSON));

        return super.toJavaResult(r, Void.class);
    }

    private Result<Void> clt_propagateMsg(String user, Message msg) {
        Response r = target
                .path("propagate")
                .request()
                .post(Entity.entity(msg, MediaType.APPLICATION_JSON));
        return super.toJavaResult(r, Void.class);
    }

}
