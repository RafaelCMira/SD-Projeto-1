package sd2223.trab1.server.java;

import jakarta.inject.Singleton;
import sd2223.trab1.api.Message;
import sd2223.trab1.api.PropMsgHelper;
import sd2223.trab1.api.java.Feeds;
import sd2223.trab1.api.java.Result;
import sd2223.trab1.api.java.Users;
import sd2223.trab1.client.FeedsClientFactory;
import sd2223.trab1.client.UsersClientFactory;
import sd2223.trab1.server.REST.Feeds.RestFeedsServer;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Singleton
public class JavaFeeds implements Feeds {
    private static final String DELIMITER = "@";
    private static String feedsDomain;
    private static int feedsID;
    private long idCounter;

    private final int THREADS = Runtime.getRuntime().availableProcessors();

    private ExecutorService executor = Executors.newFixedThreadPool(THREADS);


    /**
     * Feeds dos users.
     * String -> userName; Long -> id; Message
     */
    private final Map<String, Map<Long, Message>> feeds = new HashMap<>();

    /**
     * Subscricoes de users do de dominios diferentes, agrupados por dominio.
     * String -> userName; String -> Domain; Set String -> userName de subscricoes do user em Domain
     */
    private final Map<String, Map<String, Set<String>>> mySubscriptionsByDomain = new HashMap<>();

    /**
     * Subscricoes do user no mesmo dominio do user.
     * String -> userName; Set String -> userName de subscricoes do user no dominio do user
     */
    private final Map<String, Set<String>> mySubscriptionsInCurrentDomain = new HashMap<>();

    /**
     * Seguidores do user de dominios diferentes, agrupados por dominio.
     * String -> userName; String -> Domain; Set String -> userName de seguidores do user em Domain
     */
    private final Map<String, Map<String, Set<String>>> myFollowersByDomain = new HashMap<>();

    /**
     * Seguidores do user no mesmo dominio do user.
     * String -> userName; Set String -> userName de followers do user no dominio do user
     */
    private final Map<String, Set<String>> myFollowersInCurrentDomain = new HashMap<>();


    public JavaFeeds() {

    }

    public JavaFeeds(String feedsDomain, int feedsID) {
        this.feedsDomain = feedsDomain;
        this.feedsID = feedsID;
        idCounter = Long.MIN_VALUE;
    }

    /**
     * Verifica se a password do user esta correta.
     *
     * @param user user a ser verificado
     * @param pwd  password a ser verificado
     * @return ok se esta correta ou um erro.
     */
    private Result<Void> auxVerifyPassword(String user, String pwd) {
        var parts = user.split(DELIMITER);
        String userName = parts[0];
        String userDomain = parts[1];
        Users usersServer = UsersClientFactory.get(userDomain);
        return usersServer.verifyPassword(userName, pwd);
    }

    /**
     * Verifica se um user existe.
     *
     * @param user user a ser verificado
     * @return ok se existe ou um erro.
     */
    private Result<Void> auxCheckUser(String user) {
        var parts = user.split(DELIMITER);
        String userName = parts[0];
        String userDomain = parts[1];
        Users usersServer = UsersClientFactory.get(userDomain);
        return usersServer.checkUser(userName);
    }

    /**
     * Metodo auxiliar que chama o metodo que propaga uma mensagem.
     *
     * @param serverDomain dominio do servidor
     * @param users        conjunto de users que vao receber a msg
     * @param msg          msg a ser propagada
     */
    private void auxPropMsg(String serverDomain, Set<String> users, Message msg) {
        Feeds feedsServer = FeedsClientFactory.get(serverDomain);
        /*
        if (feedsServer instanceof RestFeedsServer) {
            String[] res = users.toArray(new String[users.size()]);
            PropMsgHelper msgAndList = new PropMsgHelper(msg, res);
            feedsServer.propagateMsgToRest(msgAndList);
        } else {
            String[] res = users.toArray(new String[users.size()]);
            feedsServer.propagateMsgToSoap(res, msg);
        }*/
        String[] res = users.toArray(new String[users.size()]);
        feedsServer.propagateMsgToSoap(res, msg);
    }

    /**
     * Metodo auxiliar para propagar uma subscricao de um dominio para outro.
     *
     * @param user    user que subscreve
     * @param userSub user que foi subscrito
     */
    private void auxPropSub(String user, String userSub) {
        String userSubDomain = getUserDomain(userSub);
        Feeds feedsServer = FeedsClientFactory.get(userSubDomain);
        feedsServer.propagateSub(user, userSub);
    }

    /**
     * Metodo auxiliar para propagar um unfollow de um dominio para outro.
     *
     * @param user    user que faz unfollow
     * @param userSub user que foi unfollowed
     */
    private void auxPropUnsub(String user, String userSub) {
        String userSubDomain = getUserDomain(userSub);
        Feeds feedsServer = FeedsClientFactory.get(userSubDomain);
        feedsServer.propagateUnsub(user, userSub);
    }

    /**
     * Coloca uma msg num user do dominio corrente.
     *
     * @param user user
     * @param msg  mensagem
     */
    private void putMessageInUser(String user, Message msg) {
        Map<Long, Message> userFeed = feeds.computeIfAbsent(user, feed -> new HashMap<>());
        userFeed.put(msg.getId(), msg);
    }

    /**
     * Metodo que coloca uma msg em todos os followers de um user.
     *
     * @param user user
     * @param msg  mensagem a colocar
     */
    private void postMessageInFollowers(String user, Message msg) {
        synchronized (this) {// mesmo dominio
            // Colocar a msg no feed de todos os followers do user no mesmo dominio
            Set<String> followersInCurrentDomain = myFollowersInCurrentDomain.computeIfAbsent(user, followers -> new HashSet<>());
            for (String f : followersInCurrentDomain)
                putMessageInUser(f, msg);
        }

        synchronized (this) {
            // Colocar a msg no feed de todos os followers do user com dominios diferentes.
            Map<String, Set<String>> followersByDomain = myFollowersByDomain.computeIfAbsent(user, domain -> new HashMap<>());
            for (String domain : followersByDomain.keySet()) {
                Set<String> set = followersByDomain.get(domain);
                Runnable task = () -> auxPropMsg(domain, set, msg);
                executor.execute(task);
            }
        }
        // executor.shutdown();
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
        }

        // Colocar a msg no user correto
        synchronized (this) {
            putMessageInUser(user, msg);
        }

        postMessageInFollowers(user, msg);

        return Result.ok(id);
    }

    @Override
    public Result<Void> removeFromPersonalFeed(String user, long mid, String pwd) {
        var result = auxVerifyPassword(user, pwd);
        if (!result.isOK()) return Result.error(result.error());

        synchronized (this) {
            Map<Long, Message> userFeed = feeds.computeIfAbsent(user, feed -> new HashMap<>());
            Message msg = userFeed.get(mid);
            if (msg == null) return Result.error(Result.ErrorCode.NOT_FOUND); // 404
            userFeed.remove(mid);
        }

        return Result.ok();
    }

    @Override
    public Result<Message> getMessage(String user, long mid) {
        String userDomain = getUserDomain(user);

        if (userDomain.equals(feedsDomain)) {
            Message msg;
            synchronized (this) {
                Map<Long, Message> userFeed = feeds.get(user);

                // Se o user nao existe
                if (userFeed == null) return Result.error(Result.ErrorCode.NOT_FOUND); // 404

                msg = userFeed.get(mid);
            }
            // Se a msg nao existe
            if (msg == null) return Result.error(Result.ErrorCode.NOT_FOUND); // 404

            return Result.ok(msg);
        } else {
            Feeds feedsServer = FeedsClientFactory.get(userDomain);
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
                Map<Long, Message> userFeed = feeds.computeIfAbsent(user, feed -> new HashMap<>());
                userFeed.forEach((id, msg) -> {
                    if (msg.getCreationTime() > time)
                        list.add(msg);
                });
            }

            return Result.ok(list);

        } else {
            Feeds feedsServer = FeedsClientFactory.get(userDomain);
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
                Set<String> userSubscriptions = mySubscriptionsInCurrentDomain.computeIfAbsent(user, subs -> new HashSet<>());
                userSubscriptions.add(userSub);

                // Adiciono user aos follows de userSub
                Set<String> userSubFollowers = myFollowersInCurrentDomain.computeIfAbsent(userSub, followers -> new HashSet<>());
                userSubFollowers.add(user);
            }
        }
        // Se estao em dominios diferentes
        else {
            synchronized (this) {
                // Adiciono userSub as subs de user
                Map<String, Set<String>> subscriptionsByDomain = mySubscriptionsByDomain.computeIfAbsent(user, domain -> new HashMap<>());
                String userSubDomain = getUserDomain(userSub);
                Set<String> subsInDomain = subscriptionsByDomain.computeIfAbsent(userSubDomain, subs -> new HashSet<>());
                subsInDomain.add(userSub);
            }
            Runnable task = () -> auxPropSub(user, userSub);
            executor.execute(task);
        }

        return Result.ok();
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
                Set<String> userSubscriptions = mySubscriptionsInCurrentDomain.get(user);
                if (userSubscriptions != null) userSubscriptions.remove(userSub);

                // Removo user dos followers de userSub
                Set<String> userSubFollowers = myFollowersInCurrentDomain.get(userSub);
                if (userSubFollowers != null) userSubFollowers.remove(user);
            }
        } else {
            synchronized (this) {
                // Removo userSub as subs de user de dominios diferentes
                Map<String, Set<String>> subscriptionsByDomain = mySubscriptionsByDomain.get(user);
                if (subscriptionsByDomain != null) {
                    String userSubDomain = getUserDomain(userSub);
                    Set<String> subsInDomain = subscriptionsByDomain.get(userSubDomain);
                    if (subsInDomain != null)
                        subsInDomain.remove(userSub);
                }
            }
            Runnable task = () -> auxPropUnsub(user, userSub);
            executor.execute(task);
        }
        return Result.ok();
    }

    @Override
    public Result<List<String>> listSubs(String user) {
        var result = auxCheckUser(user);
        if (!result.isOK()) return Result.error(result.error()); // 404

        List<String> list = new LinkedList<>();

        synchronized (this) {
            // Adicionamos todas as subscricores que o user tem no mesmo dominio
            Set<String> res = mySubscriptionsInCurrentDomain.get(user);
            if (res != null) list.addAll(res);
            // Adicionamos todas as subscricores que o user tem em dominios diferentes do seu
            Map<String, Set<String>> subsByDomain = mySubscriptionsByDomain.get(user);
            if (subsByDomain != null)
                subsByDomain.forEach((domain, domainSubs) -> {
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

            feeds.remove(user);

            Set<String> subscriptions = mySubscriptionsInCurrentDomain.get(user);
            if (subscriptions != null)
                for (String s : subscriptions) { // aviso meus followers que os deixei de seguir
                    Set<String> followers = myFollowersInCurrentDomain.get(s);
                    followers.remove(user);
                }
            mySubscriptionsInCurrentDomain.remove(user); // Remover todas as subs do mesmo dominio

            // Remover todas as subs de dominios diferentes
            // TODO se necessario

            // Removo todos os followers do user
            Set<String> uFollowers = myFollowersInCurrentDomain.get(user);
            if (uFollowers != null)
                for (String f : uFollowers) { // Tenho de avisar todos os followers que eles deixaram de me seguir
                    Set<String> subs = mySubscriptionsInCurrentDomain.get(f);
                    subs.remove(user);
                }
            myFollowersInCurrentDomain.remove(user); // Remover todos os followers do mesmo dominio

            // Remover todos os followers de dominios diferentes
            // TODO se necessario
        }

        return Result.ok();
    }

    @Override
    public Result<Void> propagateMsgToSoap(String[] users, Message msg) {
        if (users != null)
            for (String u : users) {
                Map<Long, Message> userFeed = feeds.computeIfAbsent(u, feed -> new HashMap<>());
                userFeed.put(msg.getId(), msg);
            }
        return Result.ok();
    }

    @Override
    public Result<Void> propagateSub(String user, String userSub) {
        // Adicionar user aos followers de userSub
        synchronized (this) {
            Map<String, Set<String>> followersByDomain = myFollowersByDomain.computeIfAbsent(userSub, domain -> new HashMap<>());
            String userDomain = getUserDomain(user);
            Set<String> usersInDomain = followersByDomain.computeIfAbsent(userDomain, followers -> new HashSet<>());
            usersInDomain.add(user);
        }
        return Result.ok();
    }

    @Override
    public Result<Void> propagateUnsub(String user, String userSub) {
        // Remover user dos followers de userSub
        synchronized (this) {
            Map<String, Set<String>> followersByDomain = myFollowersByDomain.computeIfAbsent(userSub, domain -> new HashMap<>());
            String userDomain = getUserDomain(user);
            Set<String> usersInDomain = followersByDomain.computeIfAbsent(userDomain, followers -> new HashSet<>());
            usersInDomain.remove(user);
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

    /**
     * Devolve o dominio do user.
     *
     * @param user user
     * @return dominio do user
     */
    private String getUserDomain(String user) {
        var parts = user.split(DELIMITER);
        String userDomain = parts[1];
        return userDomain;
    }

    /**
     * Metodo que gera um id unico para as msgs.
     *
     * @return id
     */
    private long generateId() {
        long result = idCounter * 256 + feedsID;
        idCounter++;
        return result;
    }

}
