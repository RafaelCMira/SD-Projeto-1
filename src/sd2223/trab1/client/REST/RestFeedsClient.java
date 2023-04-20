package sd2223.trab1.client.REST;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import sd2223.trab1.api.Message;
import sd2223.trab1.api.PropMsgHelper;
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
        return super.reTry(() -> clt_getMessage(user, mid));
    }

    @Override
    public Result<List<Message>> getMessages(String user, long time) {
        return super.reTry(() -> clt_getMessages(user, time));
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
    public Result<Void> propagateUnsub(String user, String userSub) {
        return super.reTry(() -> clt_propagateUnsub(user, userSub));
    }


    @Override
    public Result<Void> propagateMsg(PropMsgHelper msgAndList) {
        return super.reTry(() -> clt_propagateMsg(msgAndList));
    }

    private Result<Message> clt_getMessage(String user, long mid) {
        Response r = target
                .path(user)
                .path(String.valueOf(mid))
                .request()
                .accept(MediaType.APPLICATION_JSON)
                .get();
        return super.toJavaResult(r, Message.class);
    }

    private Result<List<Message>> clt_getMessages(String user, long time) {
        Response r = target
                .path(user)
                .queryParam(FeedsService.TIME, time)
                .request()
                .accept(MediaType.APPLICATION_JSON)
                .get();
        return super.toJavaResult(r, new GenericType<List<Message>>() {
        });
    }


    private Result<Void> clt_propagateSub(String user, String userSub) {
        Response r = target
                .path(user)
                .path("suber")
                .path(userSub)
                .request()
                .post(Entity.entity(user, MediaType.APPLICATION_JSON));
        return super.toJavaResult(r, Void.class);
    }

    private Result<Void> clt_propagateUnsub(String user, String userSub) {
        Response r = target
                .path(user)
                .path("suber")
                .path(userSub)
                .request()
                .delete();
        return super.toJavaResult(r, Void.class);
    }

    private Result<Void> clt_propagateMsg(PropMsgHelper msgAndList) {
        Response r = target
                .path("propagate")
                .request()
                .post(Entity.entity(msgAndList, MediaType.APPLICATION_JSON));
        return super.toJavaResult(r, Void.class);
    }

    private Result<Void> clt_delUserFeed(String user) {
        Response r = target
                .path(user)
                .request()
                .delete();
        return super.toJavaResult(r, Void.class);
    }

}
