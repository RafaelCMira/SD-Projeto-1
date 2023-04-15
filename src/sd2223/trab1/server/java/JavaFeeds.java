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
    private final Map<String, Map<Long, Message>> allFeeds = new HashMap<>();

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

    private void putMessageOnSelf(String user, Message msg) {
        Map<Long, Message> userFeed = allFeeds.get(user);

        if (userFeed == null) {
            userFeed = new HashMap<>();
            allFeeds.put(user, userFeed);
        }
        userFeed.put(msg.getId(), msg);

        SortedMap<Long, Message> uMessagesByTime = userMessagesByTime.get(user);

        if (uMessagesByTime == null) {
            uMessagesByTime = new TreeMap<>();
            userMessagesByTime.put(user, uMessagesByTime);
        }
        uMessagesByTime.put(msg.getCreationTime(), msg);
    }

    // Metodo que coloca uma msg em todos os followers de alguem
    private void postMessageInFollowers(String user, Message msg) {
        // Vou buscar a lista dos meus seguidores
        List<String> followers = myFollowers.get(user);


        if (followers == null) {
            followers = new LinkedList<>();
            myFollowers.put(user, followers);
        }

        // Colocar a msg no follower
        for (String follower : followers) {

            // Assim ja coloca em ambas as estuturas
            putMessageOnSelf(follower, msg);

            /*
            // Vou ao feed de msg do follower e meto a msg
            Map<Long, Message> followerFeed = allFeeds.get(follower);

            // Vejo se o feed esta a null
            if (followerFeed == null) {
                followerFeed = new HashMap<>();
                allFeeds.put(follower, followerFeed);
            }
            followerFeed.put(msg.getId(), msg);*/


        }
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
            // Colocar a msg no user correto, no map ordenado por time
            putMessageOnSelf(user, msg);

            // Coloco a msg no feed de todos os users que me seguem
            postMessageInFollowers(user, msg);

            return Result.ok(id);

        } else {
            return Result.error(result.error());
        }
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
        Map<Long, Message> uMessages = allFeeds.get(user);
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

        // User nao existe (mentira, isto ta errado porque o user pode existir no USERs server), quando crio um user no userServer, tenho que lhe criar um feed (a melhor forma de fazer isto e fazer um pedido ao feedsServer quando crio um user no USERS, mas para funcionar agora vou so fazer um pedido para ver se ele existe do feeds para o USERS e se existe entao crio um feed se ele ainda nao o tiver

        var result = auxCheckUser(user);

        if (result.isOK()) {
            Map<Long, Message> userFeed = allFeeds.get(user);
            if (userFeed == null) {
                userFeed = new HashMap<>();
                allFeeds.put(user, userFeed);
            }

            SortedMap<Long, Message> uMessagesByTime = userMessagesByTime.get(user);

            if (uMessagesByTime == null) {
                uMessagesByTime = new TreeMap<>();
                userMessagesByTime.put(user, uMessagesByTime);
            }
        } else {
            return Result.error(result.error());
        }


        SortedMap<Long, Message> uMessagesByTime = userMessagesByTime.get(user);

        List<Message> list = new LinkedList<>();

        /*
        if (time == 0) {
            uMessagesByTime.forEach((id, msg) -> list.add(msg));
            return Result.ok(list);
        }*/

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

        // Adiciono o userSub as minhas subscricoes
        List<String> subs = mySubscriptions.get(user);
        if (subs == null) {
            subs = new LinkedList<>();
            mySubscriptions.put(user, subs);
        }
        subs.add(userSub);

        // Adiciono o user aos Followers do userSub
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
