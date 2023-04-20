package sd2223.trab1.server.java;

import jakarta.inject.Singleton;
import sd2223.trab1.Discovery;
import sd2223.trab1.api.Message;
import sd2223.trab1.api.PropMsgHelper;
import sd2223.trab1.api.java.Feeds;
import sd2223.trab1.api.java.Result;
import sd2223.trab1.api.java.Users;
import sd2223.trab1.client.FeedsClientFactory;
import sd2223.trab1.client.UsersClientFactory;
import sd2223.trab1.server.REST.Feeds.RestFeedsServer;
import sd2223.trab1.server.REST.Users.RestUsersServer;

import java.net.URI;
import java.util.*;

@Singleton
public class JavaFeeds implements Feeds {
    private static final String DELIMITER = "@";
    private static String feedsDomain;
    private static int feedsID;
    private final int MIN_REPLIES = 1;

    // Long -> id; Message
    // Todas as msgs do dominio
    // Apenas contem os ids que mapeiam um valor booleano. Se o id estiver a true entao esta em a ser usado, senao podemos ficar com ele para uma proxima msg
    private final Map<Long, Boolean> allMessages = new HashMap<>();

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

    public JavaFeeds(String feedsDomain, int feedsID) {
        this.feedsDomain = feedsDomain;
        this.feedsID = feedsID;
    }


    /**
     * Devolve um servidor de feeds do dominio do user.
     *
     * @param domain dominio do servidor
     * @return servidor de feeds
     */
    private Feeds getFeedsServer(String domain) {
        // Descubro onde esta o servidor
        Discovery discovery = Discovery.getInstance();
        String serviceDomain = RestFeedsServer.SERVICE + "." + domain;
        // Obtenho o URI
        URI[] uris = discovery.knownUrisOf(serviceDomain, MIN_REPLIES);
        URI serverUri = uris[0];
        // Devolvo o servidor
        return FeedsClientFactory.get(serverUri);
    }

    /**
     * Devolve um servidor de users daquele dominio.
     *
     * @param domain dominio do servidor.
     * @return servidor de users
     */
    private Users getUsersServer(String domain) {
        // Descubro onde esta o servidor
        Discovery discovery = Discovery.getInstance();
        String serviceDomain = RestUsersServer.SERVICE + "." + domain;
        // Obtenho o URI
        URI[] uris = discovery.knownUrisOf(serviceDomain, MIN_REPLIES);
        URI serverUri = uris[0];
        // Devolvo o servidor
        return UsersClientFactory.get(serverUri);
    }

    private String getUserDomain(String user) {
        var parts = user.split(DELIMITER);
        String userDomain = parts[1];
        return userDomain;
    }

    /**
     * Metodo que gera um id para a msg. O primeiro digito do id e o numero do servidor em que foi resgistada.
     *
     * @return id unico para uma nova msg no servidor
     */
    private long generateId() {
        long result = feedsID;
        Random rand = new Random();
        // Adiciona dígitos aleatórios após o número inicial
        for (int i = Long.toString(feedsID).length(); i < 19; i++)
            result = result * 10 + rand.nextInt(10);

        while (true) {
            synchronized (this) {
                if (allMessages.get(result) == null || allMessages.get(result) == false) {
                    allMessages.put(result, true);
                    return result;
                } else {
                    for (int i = Long.toString(feedsID).length(); i < 19; i++)
                        result = result * 10 + rand.nextInt(10);
                }
            }
        }
    }

    private Result<Void> auxVerifyPassword(String user, String pwd) {
        var parts = user.split(DELIMITER);
        String userName = parts[0];
        String userDomain = parts[1];
        Users usersServer = getUsersServer(userDomain);
        return usersServer.verifyPassword(userName, pwd);
    }

    private Result<Void> auxCheckUser(String user) {
        var parts = user.split(DELIMITER);
        String userName = parts[0];
        String userDomain = parts[1];
        Users usersServer = getUsersServer(userDomain);
        return usersServer.checkUser(userName);
    }

    private Result<Void> auxPropMsg(String serverDomain, PropMsgHelper obj) {
        Feeds feedsServer = getFeedsServer(serverDomain);
        return feedsServer.propagateMsg(obj);
    }

    private Result<Void> auxPropSub(String user, String userSub) {
        String userSubDomain = getUserDomain(userSub);
        Feeds feedsServer = getFeedsServer(userSubDomain);
        return feedsServer.propagateSub(user, userSub);
    }

    private Result<Void> auxPropUnsub(String user, String userSub) {
        String userSubDomain = getUserDomain(userSub);
        Feeds feedsServer = getFeedsServer(userSubDomain);
        return feedsServer.propagateUnsub(user, userSub);
    }

    // Coloca uma msg num user
    private Result<Void> putMessageInUser(String user, Message msg) {
        Map<Long, Message> userFeed = feeds.computeIfAbsent(user, k -> new HashMap<>());
        userFeed.put(msg.getId(), msg);
        return Result.ok();
    }

    // Metodo que coloca uma msg em todos os followers do user
    private Result<Void> postMessageInFollowers(String user, Message msg) {
        synchronized (this) {// mesmo dominio
            // Colocar a msg no feed de todos os followers do user no mesmo dominio
            List<String> followersInCurrentDomain = myFollowersInCurrentDomain.computeIfAbsent(user, k -> new LinkedList<>());

            for (String f : followersInCurrentDomain)
                putMessageInUser(f, msg);
        }

        synchronized (this) {
            // Colocar a msg no feed de todos os followers do user com dominios diferentes.
            Map<String, List<String>> followersByDomain = myFollowersByDomain.computeIfAbsent(user, k -> new HashMap<>()); // todos os followers do user agrupados por dominio

            for (String domain : followersByDomain.keySet()) {
                new Thread(() -> {
                    try {
                        PropMsgHelper msgAndList = new PropMsgHelper(msg, followersByDomain.get(domain));
                        auxPropMsg(domain, msgAndList);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();

            }
        }

        return Result.ok();
    }

    @Override
    public Result<Long> postMessage(String user, String pwd, Message msg) {
        if (msg == null || user == null || pwd == null) return Result.error(Result.ErrorCode.BAD_REQUEST); // 400

        String userDomain = getUserDomain(user);
        if (!userDomain.equals(feedsDomain)) return Result.error(Result.ErrorCode.BAD_REQUEST); // 400

        var result = auxVerifyPassword(user, pwd);
        if (!result.isOK()) return Result.error(result.error());

        long id = generateId();
        synchronized (this) {
            msg.setId(id);
            msg.setCreationTime(System.currentTimeMillis());
            // Coloco no allMessages
            allMessages.put(id, true);
        }

        // Colocar a msg no user correto
        synchronized (this) {
            putMessageInUser(user, msg);
        }

        // Coloco a msg no feed de todos os users que me seguem
        var res = postMessageInFollowers(user, msg);
        if (!res.isOK()) return Result.error(res.error());

        return Result.ok(id);
    }


    private Result<Void> auxRemoveFromFeed(String user, long mid) {
        synchronized (this) {
            Map<Long, Message> userFeed = feeds.computeIfAbsent(user, k -> new HashMap<>());
            Message msg = userFeed.get(mid);

            // Verifica se o user tem a msg no feed
            if (msg == null) return Result.error(Result.ErrorCode.NOT_FOUND); // 404

            userFeed.remove(mid);
        }

        synchronized (this) {
            allMessages.put(mid, false);
        }

        return Result.ok();
    }

    @Override
    public Result<Void> removeFromPersonalFeed(String user, long mid, String pwd) {
        var result = auxVerifyPassword(user, pwd);

        if (!result.isOK()) return Result.error(result.error());

        return auxRemoveFromFeed(user, mid);
    }

    @Override
    public Result<Message> getMessage(String user, long mid) {
        String userDomain = getUserDomain(user);

        if (userDomain.equals(feedsDomain)) {
            Message msg;
            synchronized (this) {
                Map<Long, Message> userFeed = feeds.get(user);

                if (userFeed == null) // Se o user nao existe
                    return Result.error(Result.ErrorCode.NOT_FOUND); // 404

                msg = userFeed.get(mid);
            }
            // Se a msg nao existe
            if (msg == null) return Result.error(Result.ErrorCode.NOT_FOUND); // 404

            return Result.ok(msg);
        } else {
            // e so fazer um getMessage ao servidor correto
            Feeds feedsServer = getFeedsServer(userDomain);
            return feedsServer.getMessage(user, mid);
        }
    }

    @Override
    public Result<List<Message>> getMessages(String user, long time) {
        String userDomain = getUserDomain(user);
        if (userDomain.equals(feedsDomain)) {
            var result = auxCheckUser(user);
            if (!result.isOK()) return Result.error(result.error());

            List<Message> list = new LinkedList<>();
            synchronized (this) {
                Map<Long, Message> userFeed = feeds.computeIfAbsent(user, k -> new HashMap<>());
                userFeed.forEach((id, msg) -> {
                    if (msg.getCreationTime() > time)
                        list.add(msg);
                });
            }
            return Result.ok(list);

        } else {
            // e so fazer um getMessage ao servidor correto
            Feeds feedsServer = getFeedsServer(userDomain);
            return feedsServer.getMessages(user, time);
        }
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
            synchronized (this) {
                // Adiciono userSub as subs de user
                List<String> userSubscriptions = mySubscriptionsInCurrentDomain.computeIfAbsent(user, k -> new LinkedList<>());
                userSubscriptions.add(userSub);

                // Adiciono user aos follows de userSub
                List<String> userSubFollowers = myFollowersInCurrentDomain.computeIfAbsent(userSub, k -> new LinkedList<>());
                userSubFollowers.add(user);
            }
        }
        // Se estao em dominios diferentes
        else {
            synchronized (this) {
                // Adiciono userSub as subs de user
                Map<String, List<String>> subscriptionsByDomain = mySubscriptionsByDomain.computeIfAbsent(user, k -> new HashMap<>());

                String userSubDomain = getUserDomain(userSub);

                List<String> subsInDomain = subscriptionsByDomain.computeIfAbsent(userSubDomain, k -> new LinkedList<>());
                subsInDomain.add(userSub);
            }

            var res = auxPropSub(user, userSub);
            if (!res.isOK()) return Result.error(Result.ErrorCode.CONFLICT);
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
            synchronized (this) {
                // Removo userSub das subcricoes de user
                List<String> userSubscriptions = mySubscriptionsInCurrentDomain.computeIfAbsent(user, k -> new LinkedList<>());
                while (userSubscriptions.remove(userSub)) ;

                // Removo user dos followers de userSub
                List<String> userSubFollowers = myFollowersInCurrentDomain.computeIfAbsent(userSub, k -> new LinkedList<>());
                while (userSubFollowers.remove(user)) ;
            }
        } else {
            synchronized (this) {
                // Removo userSub as subs de user de dominios diferentes
                // Podemos otimizar isto, se for null quer dizer que nao temos subs neste dominio e podemos passar isto
                Map<String, List<String>> subscriptionsByDomain = mySubscriptionsByDomain.computeIfAbsent(user, k -> new HashMap<>());

                String userSubDomain = getUserDomain(userSub);

                List<String> subsInDomain = subscriptionsByDomain.computeIfAbsent(userSubDomain, k -> new LinkedList<>());

                subsInDomain.remove(userSub);
            }

            var res = auxPropUnsub(user, userSub);
            if (!res.isOK()) return Result.error(res.error());
        }
        return Result.ok();
    }

    @Override
    public Result<List<String>> listSubs(String user) {
        var result = auxCheckUser(user);
        if (!result.isOK()) return Result.error(result.error()); // 404

        // Lista resultado
        List<String> list = new LinkedList<>();

        synchronized (this) {
            // Adicionamos todas as subscricores que o user tem no mesmo dominio
            List<String> res = mySubscriptionsInCurrentDomain.get(user);
            if (res != null) list.addAll(res);

            // Adicionamos todas as subscricores que o user tem em dominios diferentes
            if (mySubscriptionsByDomain.get(user) != null)
                mySubscriptionsByDomain.get(user).forEach((domain, domainSubs) -> {
                    if (domainSubs != null)
                        list.addAll(domainSubs);
                });
        }
        return Result.ok(list);
    }

    @Override
    public Result<Void> deleteUserFeed(String user) {
        // Eliminar todas as msg do user
        synchronized (this) {
            Map<Long, Message> userFeed = feeds.get(user);
            if (userFeed == null) return Result.ok(); // nao tem msg no feed

            userFeed.forEach((id, value) -> {
                allMessages.put(id, false);
            });

            feeds.remove(user);


            List<String> subscriptions = mySubscriptionsInCurrentDomain.get(user);
            if (subscriptions != null)
                for (String s : subscriptions) { // aviso meus followers que os deixei de seguir
                    List<String> followers = myFollowersInCurrentDomain.get(s);
                    while (followers.remove(user)) ;
                }
            mySubscriptionsInCurrentDomain.remove(user); // Remover todas as subs do mesmo dominio

            // Remover todas as subs de dominios diferentes
            // TODO se necessario

            // Removo todos os followers do user
            List<String> uFollowers = myFollowersInCurrentDomain.get(user);
            if (uFollowers != null)
                for (String f : uFollowers) { // Tenho de avisar todos os followers que eles deixaram de me seguir
                    List<String> subs = mySubscriptionsInCurrentDomain.get(f);
                    while (subs.remove(user)) ;
                }
            myFollowersInCurrentDomain.remove(user); // Remover todos os followers do mesmo dominio

            // Remover todos os followers de dominios diferentes
            // TODO se necessario
        }

        return Result.ok();
    }

    @Override
    public Result<Void> propagateMsg(PropMsgHelper msgAndList) {
        Message msg = msgAndList.getMsg();
        List<String> usersList = msgAndList.getSubs();

        // Para todos os users de usersList, colocar no feed de cada um
        for (String u : usersList) {
            Map<Long, Message> userFeed = feeds.computeIfAbsent(u, k -> new HashMap<>());
            userFeed.put(msg.getId(), msg);
        }
        return Result.ok();
    }

    @Override
    public Result<Void> propagateSub(String user, String userSub) {
        // Adicionar user aos followers de userSub

        synchronized (this) {
            Map<String, List<String>> followersByDomain = myFollowersByDomain.computeIfAbsent(userSub, k -> new HashMap<>());

            String userDomain = getUserDomain(user);

            List<String> usersInDomain = followersByDomain.computeIfAbsent(userDomain, k -> new LinkedList<>());
            usersInDomain.add(user);
        }
        return Result.ok();
    }

    @Override
    public Result<Void> propagateUnsub(String user, String userSub) {
        // Remover user dos followers de userSub
        synchronized (this) {
            Map<String, List<String>> followersByDomain = myFollowersByDomain.computeIfAbsent(userSub, k -> new HashMap<>());

            String userDomain = getUserDomain(user);

            List<String> usersInDomain = followersByDomain.computeIfAbsent(userDomain, k -> new LinkedList<>());
            usersInDomain.remove(user);
        }

        return Result.ok();
    }


}
