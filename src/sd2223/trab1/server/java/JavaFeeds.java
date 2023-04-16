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
import java.util.logging.Logger;

@Singleton
public class JavaFeeds implements Feeds {

    private static Logger Log = Logger.getLogger(JavaUsers.class.getName());
    private static final String DELIMITER = "@";
    private String domain;
    private int feedsID;
    private final int MIN_REPLIES = 1;

    // Long -> id; Message
    // Todas as msgs do dominio
    private final Map<Long, Message> allMessages = new HashMap<>();

    // String -> userName; Mapa Long -> id; Message
    // Feeds de todos os users do dominio
    private final Map<String, Map<Long, Message>> feeds = new HashMap<>();


    // String -> userName; List String -> userName de quem sigo
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

    // Coloca uma msg num user especifico
    private void putMessageInUser(String user, Message msg) {
        Map<Long, Message> userFeed = feeds.get(user);

        if (userFeed == null) {
            userFeed = new HashMap<>();
            feeds.put(user, userFeed);
        }
        userFeed.put(msg.getId(), msg);
    }

    // Metodo que coloca uma msg em todos os followers de alguem
    private void postMessageInFollowers(String user, Message msg) {
        // Vou buscar a lista dos meus seguidores
        List<String> followers = myFollowers.get(user);

        if (followers == null) {
            followers = new LinkedList<>();
            myFollowers.put(user, followers);
        }

        for (String follower : followers) {
            putMessageInUser(follower, msg);
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
            putMessageInUser(user, msg);

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

    private Result<Void> auxRemoveFromFeed(String user, long mid) {
        Map<Long, Message> userFeed = feeds.get(user);
        if (userFeed == null) {
            userFeed = new HashMap<>();
            feeds.put(user, userFeed);
        }

        // Verifica se o user tem a msg no feed
        Message msg = userFeed.get(mid);
        if (msg == null) return Result.error(Result.ErrorCode.NOT_FOUND); // 404

        // Se tem a msg entao remove
        userFeed.remove(mid);

        allMessages.remove(mid);

        return Result.ok();
    }

    @Override
    public Result<Void> removeFromPersonalFeed(String user, long mid, String pwd) {
        // Verifica se o user existe e se password esta correta
        var result = auxVerifyPassword(user, pwd);
        if (result.isOK()) {
            var res = auxRemoveFromFeed(user, mid);
            if (res.isOK()) return Result.ok();
            else return Result.error(result.error());
        } else
            return Result.error(result.error());
    }

    @Override
    public Result<Message> getMessage(String user, long mid) {
        Map<Long, Message> userFeed = feeds.get(user);
        // Se o user nao existe
        if (userFeed == null) {
            return Result.error(Result.ErrorCode.NOT_FOUND); // 404
        }

        Message msg = userFeed.get(mid);
        // Se a msg nao exite
        if (msg == null) {
            return Result.error(Result.ErrorCode.NOT_FOUND); // 404
        }

        return Result.ok(msg);
    }

    @Override
    public Result<List<Message>> getMessages(String user, long time) {

        var result = auxCheckUser(user);
        List<Message> list = new LinkedList<>();

        if (result.isOK()) {
            Map<Long, Message> userFeed = feeds.get(user);
            if (userFeed == null) {
                userFeed = new HashMap<>();
                feeds.put(user, userFeed);
            }

            userFeed.forEach((id, msg) -> {
                if (msg.getCreationTime() > time) { // esta correto, e maior que tenho de verificar
                    list.add(msg);
                }
            });
        } else {
            return Result.error(result.error());
        }

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

    // A partir do momento que faco unsub deixo de receber msg de quem deixei se subscrever
    // Isto nao ta a acontecer. Tenho um bug qq que nao esta a assumir que deixei de seguir a pessoa
    @Override
    public Result<Void> unsubscribeUser(String user, String userSub, String pwd) {
        // user vai tirar a sub de userSub

        // Verificar se userSub a ser "des-subscrito" existe
        var result = auxCheckUser(userSub);
        if (!result.isOK()) return Result.error(result.error());

        // Verificar se user que tira a sub existe e se password esta correta
        result = auxVerifyPassword(user, pwd);
        if (!result.isOK()) return Result.error(result.error());

        // Removo a sub de userSub
        List<String> subs = mySubscriptions.get(user);
        if (subs == null) {
            subs = new LinkedList<>();
            mySubscriptions.put(user, subs);
        }

        while (subs.remove(userSub)) {

        }

        // Removo o follow de user
        List<String> followers = myFollowers.get(userSub);
        if (followers == null) {
            followers = new LinkedList<>();
            myFollowers.put(userSub, followers);
        }

        while (followers.remove(user)) {

        }

        return Result.ok();
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

    @Override
    public Result<Void> deleteUserFeed(String user) {
        // Eliminar todas as msg do user

        Map<Long, Message> userFeed = feeds.get(user);
        if (userFeed == null) {
            // Nao tem msg no feed
            return Result.ok();
        }

        // Removo as msg da tabela com todas as msgs
        userFeed.forEach((id, msg) -> {
            allMessages.remove(id);
        });

        // Retiro a subcricao de todas as pessoas e aviso que as deixo de seguir
        List<String> userSubs = mySubscriptions.get(user);
        if (userSubs != null)
            for (String s : userSubs) {
                List<String> followers = myFollowers.get(s);
                while (followers.remove(user)) ;
            }
        mySubscriptions.remove(user);

        // Retiro a subscricao de quem me segue
        List<String> uFollowers = myFollowers.get(user);
        if (uFollowers != null)
            for (String f : uFollowers) {
                List<String> subs = mySubscriptions.get(f);
                while (subs.remove(user)) ;
            }
        myFollowers.remove(user);

        // Feito no fim so
        feeds.remove(user);

        return Result.ok();
    }
}
