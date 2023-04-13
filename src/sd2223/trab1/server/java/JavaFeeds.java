package sd2223.trab1.server.java;

import sd2223.trab1.Discovery;
import sd2223.trab1.api.Message;
import sd2223.trab1.api.User;
import sd2223.trab1.api.java.Feeds;
import sd2223.trab1.api.java.Result;
import sd2223.trab1.client.RestUsersClient;
import sd2223.trab1.server.REST.Users.RestUsersServer;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JavaFeeds implements Feeds {

    private static final String DELIMITER = "@";

    private String domain;

    private int feedsID;

    private final int MIN_REPLIES = 10;

    private final Map<String, Message> allMessages = new HashMap<>();

    private final Map<User, List<Message>> userMessages = new HashMap<>();

    public JavaFeeds() {
        
    }

    public JavaFeeds(String domain, int feedsID) {
        this.domain = domain;
        this.feedsID = feedsID;
    }

    @Override
    public Result<Long> postMessage(String user, String pwd, Message msg) {

        var parts = user.split(DELIMITER);
        String name = parts[0];
        String domain = parts[1];

        // Descubro onde esta o servidor, obtenho o uri
        Discovery discovery = Discovery.getInstance();
        URI[] uris = discovery.knownUrisOf(domain + ":" + RestUsersServer.SERVICE, 1);
        URI serverUri = uris[0];

        // Obtenho o servidor
        var usersServer = new RestUsersClient(serverUri);

        // Faço um pedido para verificar a password. (Tb verifica se o user existe, entre outras coisas)
        var result = usersServer.verifyPassword(name, pwd);

        if (result.isOK()) {
            // Gerar um id, timeStamp, para a msg

            //
            long id = 213123;
            // Colocar a msg no user correto

            return Result.ok(id);


        } else {
            return Result.error(result.error());
        }

        //return null;
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
    public Result<List<Message>> listSubs(String user) {
        return null;
    }
}
