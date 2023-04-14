package sd2223.trab1.server.java;

import jakarta.inject.Singleton;
import sd2223.trab1.Discovery;
import sd2223.trab1.api.Message;
import sd2223.trab1.api.java.Feeds;
import sd2223.trab1.api.java.Result;
import sd2223.trab1.api.java.Users;
import sd2223.trab1.client.RestUsersClient;
import sd2223.trab1.server.REST.Users.RestUsersServer;

import java.net.URI;
import java.util.*;

@Singleton
public class JavaFeeds implements Feeds {
    private static final String DELIMITER = "@";
    private String domain;
    private int feedsID;
    private final int MIN_REPLIES = 1;

    // Long -> id; Message
    private final Map<Long, Message> allMessages = new HashMap<>();

    // String -> userName; Mapa Long -> id; Message
    private final Map<String, Map<Long, Message>> myFeed = new HashMap<>();

    // String -> userName; Mapa Long -> id; Message
    private final Map<String, SortedMap<Long, Message>> userMessagesByTime = new HashMap<>();

    // String -> userName; List String -> userName de quem eu sigo
    // Quem estou a seguir
    private final Map<String, List<String>> mySubscriptions = new HashMap<>();

    // String -> userName; List String -> userName de quem me segue
    // Quem me segue
    private final Map<String, List<String>> myFollowers = new HashMap<>();

    public JavaFeeds() {

    }

    public JavaFeeds(String domain, int feedsID) {
        this.domain = domain;
        this.feedsID = feedsID;
    }

    private Result<Void> auxVerifyPassword(String user, String pwd) {
        var parts = user.split(DELIMITER);
        String userName = parts[0];
        String userDomain = parts[1];

        // Descubro onde esta o servidor
        Discovery discovery = Discovery.getInstance();
        String serviceDomain = RestUsersServer.SERVICE + "." + userDomain;

        // Obtenho o URI
        URI[] uris = discovery.knownUrisOf(serviceDomain, MIN_REPLIES);
        URI serverUri = uris[0];

        // Obtenho o servidor
        Users usersServer = new RestUsersClient(serverUri);

        // Faço um pedido para verificar a password. (Tb verifica se o user existe, entre outras coisas)
        var result = usersServer.verifyPassword(userName, pwd);

        return result;
    }

    private Result<Void> auxCheckUser(String user) {
        var parts = user.split(DELIMITER);
        String userName = parts[0];
        String userDomain = parts[1];

        // Descubro onde esta o servidor
        Discovery discovery = Discovery.getInstance();
        String serviceDomain = RestUsersServer.SERVICE + "." + userDomain;

        // Obtenho o URI
        URI[] uris = discovery.knownUrisOf(serviceDomain, MIN_REPLIES);
        URI serverUri = uris[0];

        // Obtenho o servidor
        Users usersServer = new RestUsersClient(serverUri);

        // Faço um pedido para verificar a password. (Tb verifica se o user existe, entre outras coisas)
        var result = usersServer.checkUser(userName);

        return result;
    }

    @Override
    public Result<Long> postMessage(String user, String pwd, Message msg) {
        if (msg == null || user == null || pwd == null) {
            return Result.error(Result.ErrorCode.BAD_REQUEST); // 400
        }

        var result = auxVerifyPassword(user, pwd);

        if (result.isOK()) {
            // Gerar um id, timeStamp, para a msg
            long id = generateId();

            msg.setId(id);
            msg.setCreationTime(System.currentTimeMillis());

            // Coloco no allMessages
            allMessages.put(id, msg);

            // Colocar a msg no user correto
            Map<Long, Message> uMessages = myFeed.get(user);

            if (uMessages == null) {
                uMessages = new HashMap<>();
                myFeed.put(user, uMessages);
            }
            uMessages.put(id, msg);

            SortedMap<Long, Message> uMessagesByTime = userMessagesByTime.get(user);

            // Colocar a msg no user correto, no map ordenado por time
            if (uMessagesByTime == null) {
                uMessagesByTime = new TreeMap<>();
                userMessagesByTime.put(user, uMessagesByTime);
            }
            uMessagesByTime.put(msg.getCreationTime(), msg);

            // Coloco a msg no feed de todos os users que me seguem
            List<String> followers = myFollowers.get(user);
            if (followers == null) {
                followers = new LinkedList<>();
                myFollowers.put(user, followers);
            }

            for (String i : followers) {
                postMessageInFollowers(i, msg);
            }


            return Result.ok(id);
        } else {
            return Result.error(result.error());
        }
    }

    private void postMessageInFollowers(String follower, Message msg) {
        // Colocar a msg no follower
        Map<Long, Message> uMessages = myFeed.get(follower);

        if (uMessages == null) {
            uMessages = new HashMap<>();
            myFeed.put(follower, uMessages);
        }

        uMessages.put(msg.getId(), msg);
    }

    // Metodo auxiliar para gerar id's
    private long generateId() {
        Random rand = new Random();
        long id = Math.abs(rand.nextLong());
        while (allMessages.containsKey(id)) {
            id = Math.abs(rand.nextLong());
        }
        return id;
    }

    @Override
    public Result<Void> removeFromPersonalFeed(String user, long mid, String pwd) {
        return null;
    }

    @Override
    public Result<Message> getMessage(String user, long mid) {
        Map<Long, Message> uMessages = myFeed.get(user);
        // Se o user nao existe
        if (uMessages == null) {
            return Result.error(Result.ErrorCode.NOT_FOUND); // 404
        }

        Message msg = uMessages.get(mid);
        // Se a msg nao exite
        if (msg == null) {
            return Result.error(Result.ErrorCode.NOT_FOUND); // 404
        }

        return Result.ok(msg);
    }

    @Override
    public Result<List<Message>> getMessages(String user, long time) {
        SortedMap<Long, Message> uMessagesByTime = userMessagesByTime.get(user);

        // User nao existe
        if (uMessagesByTime == null) {
            return Result.error(Result.ErrorCode.NOT_FOUND); // 404
        }

        List<Message> list = new LinkedList<>();

        uMessagesByTime.forEach((id, msg) -> {
            if (msg.getCreationTime() > time) { // esta correto, e maior que tenho de verificar
                list.add(msg);
            }
        });

        return Result.ok(list);
    }

    @Override
    public Result<Void> subUser(String user, String userSub, String pwd) {
        // Verificar se user a ser subscrito existe
        var result = auxCheckUser(userSub);
        if (!result.isOK()) return Result.error(result.error());

        // Verificar se user que subscreve existe e se password esta correta
        result = auxVerifyPassword(user, pwd);
        if (!result.isOK()) return Result.error(result.error());

        // Adiciono o userSub as subscricoes do user
        List<String> subs = mySubscriptions.get(user);
        if (subs == null) {
            subs = new LinkedList<>();
            mySubscriptions.put(user, subs);
        }

        subs.add(userSub);

        // Adiciono o user aos folowers do userSub
        List<String> followers = myFollowers.get(userSub);
        if (followers == null) {
            followers = new LinkedList<>();
            myFollowers.put(userSub, followers);
        }

        followers.add(user);

        return Result.ok();
    }

    @Override
    public Result<Void> unsubscribeUser(String user, String userSub, String pwd) {
        return null;
    }

    @Override
    public Result<List<String>> listSubs(String user) {
        var result = auxCheckUser(user);
        if (!result.isOK()) {
            return Result.error(result.error()); // 404
        }

        List<String> list = mySubscriptions.get(user);

        if (list == null) return Result.ok(new LinkedList<>());

        return Result.ok(list);
    }
}
