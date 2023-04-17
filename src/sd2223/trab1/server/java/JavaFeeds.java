package sd2223.trab1.server.java;

import jakarta.inject.Singleton;
import sd2223.trab1.Discovery;
import sd2223.trab1.api.Message;
import sd2223.trab1.api.PropagateMsgHelper;
import sd2223.trab1.api.java.Feeds;
import sd2223.trab1.api.java.Result;
import sd2223.trab1.api.java.Users;
import sd2223.trab1.client.RestFeedsClient;
import sd2223.trab1.client.RestUsersClient;
import sd2223.trab1.server.REST.Feeds.RestFeedsServer;
import sd2223.trab1.server.REST.Users.RestUsersServer;

import java.net.URI;
import java.util.*;
import java.util.logging.Logger;

@Singleton
public class JavaFeeds implements Feeds {

    private static Logger Log = Logger.getLogger(JavaUsers.class.getName());
    private static final String DELIMITER = "@";
    private static String serverDomain;
    private static int  feedsID;
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

    // String -> userName; -- String -> Domain; String -> Users
    private final Map<String, Map<String, List<String>>> followersByDomain = new HashMap<>();


    // String -> userName; List String -> userName de quem me segue
    // Quem me segue
    private final Map<String, List<String>> myFollowers = new HashMap<>();

    public JavaFeeds() {

    }

    public JavaFeeds(String serverDomain, int feedsID) {
        this.serverDomain = serverDomain;
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

    private Result<Void> auxPropagateSub(String user, String userSub) {
        var parts = userSub.split(DELIMITER);
        String userSubDomain = parts[1];

        // Descubro onde esta o servidor
        Discovery discovery = Discovery.getInstance();
        String serviceDomain = RestFeedsServer.SERVICE + "." + userSubDomain;

        // Obtenho o URI
        URI[] uris = discovery.knownUrisOf(serviceDomain, MIN_REPLIES);
        URI serverUri = uris[0];

        // Obtenho o servidor
        Feeds feedsServer = new RestFeedsClient(serverUri);

        // Faço um pedido para propagar o sub para o user de outro dominio (colocar la o follow)

        var result = feedsServer.propagateSub(user, userSub);

        if (result.isOK())
            return Result.ok();
        else
            return Result.error(result.error());
    }

    private Result<Void> auxPropagateMsg(String domainToPropagate, Message msg, List<String> subs) {
        // Descubro onde esta o servidor
        Discovery discovery = Discovery.getInstance();
        String serviceDomain = RestFeedsServer.SERVICE + "." + domainToPropagate;

        // Obtenho o URI
        URI[] uris = discovery.knownUrisOf(serviceDomain, MIN_REPLIES);
        URI serverUri = uris[0];

        // Obtenho o servidor
        Feeds feedsServer = new RestFeedsClient(serverUri);


        PropagateMsgHelper propMsg = new PropagateMsgHelper(msg, subs);

        var result = feedsServer.propagateMsg(propMsg);


        /*
        // Faço um pedido para propagar a msg para o user de outro dominio
        var result = feedsServer.propagateMsg(user, msg); // ENTRA AQUI
        if (result.isOK())
            return Result.ok();
        else
            return Result.error(result.error());*/
        return Result.ok();
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
            List<String> followers = myFollowers.get(user);

            if (followers == null) {
                followers = new LinkedList<>();
                myFollowers.put(user, followers);
            }

            for (String follower : followers) {
                if (sameDomain(user, follower) == null)
                    putMessageInUser(follower, msg);
            }

            var parts = user.split(DELIMITER);
            String userDomain = parts[1];

            Map<String, List<String>> userSubsDomains = followersByDomain.get(user);

            if(userSubsDomains == null) {
                userSubsDomains = new HashMap<>();
                followersByDomain.put(user, userSubsDomains);
            }

            userSubsDomains.forEach((domain, list) -> {
                if (userDomain.equals(domain)) {
                    for(String u: list) putMessageInUser(u, msg);
                } else {
                  var result =  auxPropagateMsg(domain, msg, list);
                }
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
        /*
        if (!userDomain.equals(msg.getDomain())) {
            return Result.error(Result.ErrorCode.BAD_REQUEST); // 400
        }*/

        if (!userDomain.equals(serverDomain)) {
            return Result.error(Result.ErrorCode.BAD_REQUEST); // 400
        }



        var result = auxVerifyPassword(user, pwd);

        if (result.isOK()) {
            // Gerar um id, timeStamp, para a msg
            long id;

            synchronized (this) {
                id = generateId2(feedsID, 19); //generateId();
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
    private long generateId2(long number, int n) {
        long result = number;
        Random rand = new Random();

        // Adiciona dígitos aleatórios após o número inicial
        for (int i = Long.toString(number).length(); i < n; i++) {
            result = result * 10 + rand.nextInt(10);
        }

        while (allMessages.containsKey(result)) {
            for (int i = Long.toString(number).length(); i < n; i++) {
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

        synchronized (this) {
            // Adiciono o userSub as minhas subscricoes
            List<String> subs = mySubscriptions.get(user);
            if (subs == null) {
                subs = new LinkedList<>();
                mySubscriptions.put(user, subs);
            }
            subs.add(userSub);
        }

        String userSubDomain = sameDomain(user, userSub);

        if (userSubDomain == null) {
            synchronized (this) {
                // Adiciono o user aos Followers do userSub
                List<String> followers = myFollowers.get(userSub);
                if (followers == null) {
                    followers = new LinkedList<>();
                    myFollowers.put(userSub, followers);
                }
                followers.add(user);

                Map<String, List<String>> followersDomain = followersByDomain.get(user);
                if (followersDomain == null) {
                    followersDomain = new HashMap<>();
                    followersByDomain.put(user, followersDomain);
                }

                var parts = user.split(DELIMITER);
                String userSubDomain2 = parts[1];

                List<String> followsTemp = followersDomain.get(userSubDomain2);

                if(followsTemp == null) {
                    followsTemp = new LinkedList<>();
                    followersDomain.put(userSubDomain2, followsTemp);
                }

                followsTemp.add(userSub);

            }
        } else {
            // Propago o sub para outros dominios
            Result<Void> res = auxPropagateSub(user, userSub);
            if (!res.isOK()) return Result.error(res.error());
        }

        return Result.ok();
    }

    /**
     * Verifica se 2 users estao no mesmo dominio.
     *
     * @param user
     * @param userSub
     * @return null se estao, caso contrario retorna o dominio do userSub
     */
    private String sameDomain(String user, String userSub) {
        var parts = user.split(DELIMITER);
        String userDomain = parts[1];
        parts = userSub.split(DELIMITER);
        String userSubDomain = parts[1];
        if (!userDomain.equals(userSubDomain)) return userSubDomain;
        else return null;
    }

    @Override
    public Result<Void> unsubscribeUser(String user, String userSub, String pwd) {
        // Verificar se userSub a ser "des-subscrito" existe
        var result = auxCheckUser(userSub);
        if (!result.isOK()) return Result.error(result.error());

        // Verificar se user que tira a sub existe e se password esta correta
        result = auxVerifyPassword(user, pwd);
        if (!result.isOK()) return Result.error(result.error());

        synchronized (this) {
            // Removo a sub de userSub
            List<String> subs = mySubscriptions.get(user);
            if (subs == null) {
                subs = new LinkedList<>();
                mySubscriptions.put(user, subs);
            }

            while (subs.remove(userSub)) ;
        }

        synchronized (this) {
            // Removo o follow de user
            List<String> followers = myFollowers.get(userSub);
            if (followers == null) {
                followers = new LinkedList<>();
                myFollowers.put(userSub, followers);
            }

            while (followers.remove(user)) ;
        }

        return Result.ok();
    }

    @Override
    public Result<List<String>> listSubs(String user) {
        var result = auxCheckUser(user);
        if (!result.isOK()) {
            return Result.error(result.error()); // 404
        }

        List<String> list;

        synchronized (this) {
            list = mySubscriptions.get(user);
        }

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

            // Feito no fim so
            feeds.remove(user);
        }

        synchronized (this) {
            // Retiro a subcricao de todas as pessoas e aviso que as deixo de seguir
            List<String> userSubs = mySubscriptions.get(user);
            if (userSubs != null)
                for (String s : userSubs) {
                    List<String> followers = myFollowers.get(s);
                    while (followers.remove(user)) ;
                }
            mySubscriptions.remove(user);
        }

        synchronized (this) {
            // Retiro a subscricao de quem me segue
            List<String> uFollowers = myFollowers.get(user);
            if (uFollowers != null)
                for (String f : uFollowers) {
                    List<String> subs = mySubscriptions.get(f);
                    while (subs.remove(user)) ;
                }
            myFollowers.remove(user);
        }

        return Result.ok();
    }

    @Override
    public Result<Void> propagateSub(String user, String userSub) {

        synchronized (this) {
            List<String> follows = myFollowers.get(userSub);
            if (follows == null) {
                follows = new LinkedList<>();
            }
            follows.add(user);

            Map<String, List<String>> followersDomain = followersByDomain.get(user);
            if (followersDomain == null) {
                followersDomain = new HashMap<>();
                followersByDomain.put(user, followersDomain);
            }

            var parts = userSub.split(DELIMITER);
            String userFollowDomain = parts[1];

            List<String> followTemp = followersDomain.get(userFollowDomain);

            if(followTemp == null) {
                followTemp = new LinkedList<>();
                followersDomain.put(userFollowDomain, followTemp);
            }

            followTemp.add(userSub);

        }

        return Result.ok();
       // return Result.error(Result.ErrorCode.CONFLICT); // 409
    }

    @Override
    public Result<Void> propagateMsg(PropagateMsgHelper msgAndSubsList) {

        Message msg = msgAndSubsList.getMsg();
        List<String> users = msgAndSubsList.getSubs();

        for(String u: users) {
            Map<Long, Message> userFeed = feeds.get(u);

            userFeed.put(msg.getId(), msg);
        }

        return Result.ok();
    }


}
