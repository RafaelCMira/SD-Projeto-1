package sd2223.trab1.server.java;

import jakarta.inject.Singleton;
import sd2223.trab1.Discovery;
import sd2223.trab1.api.Message;
import sd2223.trab1.api.PropMsgHelper;
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
    private static String domain;
    private static int feedsID;
    private final int MIN_REPLIES = 1;

    // Long -> id; Message
    // Todas as msgs do dominio
    private final Map<Long, Message> allMessages = new HashMap<>();

    // String -> userName; Mapa Long -> id; Message
    // Feeds de todos os users do dominio
    private final Map<String, Map<Long, Message>> feeds = new HashMap<>();

    // String -> userName; ---- String -> Domain; List String -> userName de subscricoes do user em Domain
    // Contem todas as pessoas de dominios diferentes que o user segue
    private final Map<String, Map<String, List<String>>> mySubscriptionsByDomain = new HashMap<>();

    // String -> userName; List String -> userName de subscricoes do user no dominio do user
    private final Map<String, List<String>> mySubscriptionsInCurrentDomain = new HashMap<>();

    // String -> userName; ---- String -> Domain; List String -> userName de followers do user em Domain
    // Contem todos os seguidores do user em dominios diferentes do dominio do user
    private final Map<String, Map<String, List<String>>> myFollowersByDomain = new HashMap<>();

    // String -> userName; List String -> userName de followers do user no dominio do user
    private final Map<String, List<String>> myFollowersInCurrentDomain = new HashMap<>();


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
    private Result<Void> putMessageInUser(String user, Message msg) {

        synchronized (this) {
            Map<Long, Message> userFeed = feeds.get(user);

            if (userFeed == null) {
                userFeed = new HashMap<>();
                feeds.put(user, userFeed);
            }
            userFeed.put(msg.getId(), msg);
        }

        return Result.ok();
    }

    // Metodo que coloca uma msg em todos os followers de alguem
    private Result<Void> postMessageInFollowers(String user, Message msg) {
        // Vou buscar a lista dos meus seguidores
        synchronized (this) {

            // Colocar a msg no feed de todos os followers do user no mesmo dominio (mesmo dominio)
            List<String> followersInCurrentDomain = myFollowersInCurrentDomain.get(user);
            if (followersInCurrentDomain == null) {
                followersInCurrentDomain = new LinkedList<>();
                myFollowersInCurrentDomain.put(user, followersInCurrentDomain);
            }
            for (String f : followersInCurrentDomain) {
                putMessageInUser(f, msg);
            }


            // Colocar a msg no feed de todos os followers do user com dominios diferentes.
            Map<String, List<String>> followersByDomain = myFollowersByDomain.get(user); // todos os followers do user agrupados por dominio

            if (followersByDomain == null) {
                followersByDomain = new HashMap<>();
                myFollowersByDomain.put(user, followersByDomain);
            }

            followersByDomain.forEach((domain, list) -> {
                // para cada domain, vamos enviar um pedido ao servidor com aquele dominio e colocamos a msg em todos os seguidores do user naquele dominio
                PropMsgHelper msgAndList = new PropMsgHelper(msg, list);
                // var result = auxPropagateMsg(msgAndList);
            });

        }

        return Result.ok();
    }

    @Override
    public Result<Long> postMessage(String user, String pwd, Message msg) {
        if (msg == null || user == null || pwd == null) {
            return Result.error(Result.ErrorCode.BAD_REQUEST); // 400
        }

        var parts = user.split(DELIMITER);
        String userDomain = parts[1];
        if (!userDomain.equals(msg.getDomain())) {
            return Result.error(Result.ErrorCode.BAD_REQUEST); // 400
        }

        var result = auxVerifyPassword(user, pwd);

        if (result.isOK()) {
            // Gerar um id, timeStamp, para a msg
            long id;

            synchronized (this) {
                id = generateId();
                msg.setId(id);
                msg.setCreationTime(System.currentTimeMillis());
                // Coloco no allMessages
                allMessages.put(id, msg);
            }

            // Colocar a msg no user correto
            putMessageInUser(user, msg);

            // Coloco a msg no feed de todos os users que me seguem
            var res = postMessageInFollowers(user, msg);

            if (res.isOK()) return Result.ok(id);
            else return Result.error(res.error());

            //return Result.ok(id);

        } else {
            return Result.error(result.error());
        }
    }


    // Metodo auxiliar para gerar id's
    private long generateId() {
        long result = feedsID;
        Random rand = new Random();
        // Adiciona dígitos aleatórios após o número inicial
        for (int i = Long.toString(feedsID).length(); i < 19; i++) {
            result = result * 10 + rand.nextInt(10);
        }
        while (allMessages.containsKey(result)) {
            for (int i = Long.toString(feedsID).length(); i < 19; i++) {
                result = result * 10 + rand.nextInt(10);
            }
        }
        return result;
    }

    private Result<Void> auxRemoveFromFeed(String user, long mid) {
        synchronized (this) {
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
        }

        synchronized (this) {
            allMessages.remove(mid);
        }

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
        synchronized (this) {
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
    }

    @Override
    public Result<List<Message>> getMessages(String user, long time) {
        var result = auxCheckUser(user);
        List<Message> list = new LinkedList<>();

        if (result.isOK()) {
            synchronized (this) {
                Map<Long, Message> userFeed = feeds.get(user);
                if (userFeed == null) {
                    userFeed = new HashMap<>();
                    feeds.put(user, userFeed);
                }

                userFeed.forEach((id, msg) -> {
                    if (msg.getCreationTime() > time)
                        list.add(msg);
                });
            }
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

        // Se userSub esta no mesmo dominio de user
        if (areSameDomain(user, userSub)) {
            // Adiciono userSub as subs de user
            List<String> userSubscriptions = mySubscriptionsInCurrentDomain.get(user);
            if (userSubscriptions == null) {
                userSubscriptions = new LinkedList<>();
                mySubscriptionsInCurrentDomain.put(user, userSubscriptions);
            }

            userSubscriptions.add(userSub);

            // Adiciono user aos follows de userSub
            List<String> userSubFollowers = myFollowersInCurrentDomain.get(userSub);
            if (userSubFollowers == null) {
                userSubFollowers = new LinkedList<>();
                myFollowersInCurrentDomain.put(userSub, userSubFollowers);
            }

            userSubFollowers.add(user);
        } else {
            // Se estao em dominios diferentes

            // Adiciono userSub as subs de user (feito da mesma forma quer esteja no mesmo dominio ou nao)
            List<String> userSubscriptions = mySubscriptionsInCurrentDomain.get(user);
            if (userSubscriptions == null) {
                userSubscriptions = new LinkedList<>();
                mySubscriptionsInCurrentDomain.put(user, userSubscriptions);
            }
            if (!userSubscriptions.contains(userSub))
                userSubscriptions.add(userSub);

            // Adiciono user aos folloes de userSub
            // Faco um pedido
            // TODO
            // Propago o follow para o dominio do userSub
            // var result = auxPropagateSub(user, userSub)
        }

        return Result.ok();
    }

    /**
     * Verifica se 2 users estao no mesmo dominio.
     *
     * @param user
     * @param userSub
     * @return true se estao, false caso contrario
     */
    private boolean areSameDomain(String user, String userSub) {
        var parts = user.split(DELIMITER);
        String userDomain = parts[1];
        parts = userSub.split(DELIMITER);
        String userSubDomain = parts[1];
        return userDomain.equals(userSubDomain);
    }

    @Override
    public Result<Void> unsubscribeUser(String user, String userSub, String pwd) {
        // Verificar se userSub a ser "des-subscrito" existe
        var result = auxCheckUser(userSub);
        if (!result.isOK()) return Result.error(result.error());

        // Verificar se user que tira a sub existe e se password esta correta
        result = auxVerifyPassword(user, pwd);
        if (!result.isOK()) return Result.error(result.error());

        // Se estao no mesmo dominio
        if (areSameDomain(user, userSub)) {
            // Removo userSub das subcricoes de user
            List<String> userSubscriptions = mySubscriptionsInCurrentDomain.get(user);
            if (userSubscriptions == null) {
                userSubscriptions = new LinkedList<>();
                mySubscriptionsInCurrentDomain.put(user, userSubscriptions);
            }
            while (userSubscriptions.remove(userSub)) ;

            // Removo user dos followers de userSub
            List<String> userSubFollowers = myFollowersInCurrentDomain.get(userSub);
            if (userSubFollowers == null) {
                userSubFollowers = new LinkedList<>();
                myFollowersInCurrentDomain.put(userSub, userSubFollowers);
            }
            while (userSubFollowers.remove(user)) ;
        } else {
            // Estao em dominios diferentes
            // Faco o pedido
            // TODO
            // Propago o unfollow para o dominio do userSub
            // var result = auxPropagateUnSub(user, userSub)
        }


        // Removo o user dos followers do userSub
        if (areSameDomain(user, userSub)) {
            // Se user esta no mesmo dominio de userSub
            List<String> userSubFollowers = myFollowersInCurrentDomain.get(userSub);
            if (userSubFollowers == null) {
                userSubFollowers = new LinkedList<>();
                myFollowersInCurrentDomain.put(userSub, userSubFollowers);
            }
            userSubFollowers.remove(user);
        } else {
            // Se estao em dominios diferentes
            // Propago o unfollow para o dominio do userSub
            // var result = auxPropagateUnSub(user, userSub)
        }


        return Result.ok();
    }

    @Override
    public Result<List<String>> listSubs(String user) {
        var result = auxCheckUser(user);
        if (!result.isOK()) {
            return Result.error(result.error()); // 404
        }

        // Lista resultado
        List<String> list = new LinkedList<>();

        // Adicionamos todas as subscricores que o user tem no mesmo dominio
        List<String> res = mySubscriptionsInCurrentDomain.get(user);
        if (res != null) list.addAll(res);

        // Adicionamos todas as subscricores que o user tem em dominios diferentes
        // TODO
        /*
        if (mySubscriptionsInCurrentDomain != null)
            mySubscriptionsInCurrentDomain.forEach((domain, domainSubs) -> {
                if (domainSubs != null) {
                    list2.addAll(domainSubs);
                }
            });*/


        if (list == null) return Result.ok(new LinkedList<>());

        return Result.ok(list);
    }

    @Override
    public Result<Void> deleteUserFeed(String user) {
        // Eliminar todas as msg do user
        synchronized (this) {
            Map<Long, Message> userFeed = feeds.get(user);
            if (userFeed == null) {
                // Nao tem msg no feed
                return Result.ok();
            }

            // Removo as msg da tabela com todas as msgs
            userFeed.forEach((id, msg) -> {
                allMessages.remove(id);
            });

            feeds.remove(user);
        }

        // ###################################################################### //

        // Removo todas as subscricoes de user | tenho de avisar todos os followers que os deixei de seguir

        // Remover todas as subs do mesmo dominio | aviso meus followers que os deixei de seguir
        List<String> subscriptions = mySubscriptionsInCurrentDomain.get(user);
        if (subscriptions != null)
            for (String s : subscriptions) {
                List<String> followers = myFollowersInCurrentDomain.get(s);
                while (followers.remove(user)) ;
            }
        mySubscriptionsInCurrentDomain.remove(user);

        // Remover todas as subs de dominios diferentes
        // TODO


        // Removo todos os followers do user | Tenho de avisar todos os followers que eles deixaramd e me seguir

        // Remover todos os followers do mesmo dominio
        List<String> uFollowers = myFollowersInCurrentDomain.get(user);
        if (uFollowers != null)
            for (String f : uFollowers) {
                List<String> subs = mySubscriptionsInCurrentDomain.get(f);
                while (subs.remove(user)) ;
            }
        myFollowersInCurrentDomain.remove(user);

        // Remover todos os followers de dominios diferentes
        // TODO


        // ###################################################################### //


        return Result.ok();
    }


}
