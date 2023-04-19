package sd2223.trab1.server.java;

import sd2223.trab1.Discovery;
import sd2223.trab1.api.User;
import sd2223.trab1.api.java.Feeds;
import sd2223.trab1.api.java.Result;
import sd2223.trab1.api.java.Result.ErrorCode;
import sd2223.trab1.api.java.Users;
import sd2223.trab1.client.RestFeedsClient;
import sd2223.trab1.server.REST.Feeds.RestFeedsServer;

import java.net.URI;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class JavaUsers implements Users {

    private final String DELIMITER = "@";
    private final int MIN_REPLIES = 1;
    private final Map<String, User> users = new HashMap<>();

    private Result<User> auxGetUser(String name, String pwd) {
        // Check if user is valid
        if (name == null || pwd == null)
            return Result.error(ErrorCode.BAD_REQUEST); // 400

        var user = users.get(name);

        // Check if user exists
        if (user == null)
            return Result.error(ErrorCode.NOT_FOUND); // 404

        //Check if the password is correct
        if (!user.getPwd().equals(pwd))
            return Result.error(ErrorCode.FORBIDDEN); // 403

        return Result.ok(user);
    }

    @Override
    public Result<String> createUser(User user) {
        // Check if user data is valid
        if (user.getName() == null || user.getPwd() == null || user.getDisplayName() == null || user.getDomain() == null)
            return Result.error(ErrorCode.BAD_REQUEST); // 400

        synchronized (this) {
            // Insert new user, checking if userId already exists
            if (users.putIfAbsent(user.getName(), user) != null) {
                return Result.error(ErrorCode.CONFLICT); // 409
            }
        }

        String name = user.getName() + DELIMITER + user.getDomain();
        return Result.ok(name);
    }

    @Override
    public Result<User> getUser(String name, String pwd) {
        Result<User> result;

        synchronized (this) {
            result = auxGetUser(name, pwd);
        }

        if (!result.isOK()) return Result.error(result.error()); // erro

        return Result.ok(result.value()); // 200
    }

    @Override
    public Result<User> updateUser(String name, String pwd, User user) {
        Result<User> result;
        synchronized (this) {
            // Ja trata dos erros: 400, 403, 404
            result = auxGetUser(name, pwd);

            if (!result.isOK()) return Result.error(result.error()); // erro
            else {
                if (!name.equals(user.getName())) {
                    return Result.error(ErrorCode.BAD_REQUEST); // 400
                }
                User oldUser = result.value();

                String newDisplayName = user.getDisplayName();
                if (newDisplayName != null)
                    oldUser.setDisplayName(newDisplayName);

                String newPassword = user.getPwd();
                if (newPassword != null)
                    oldUser.setPwd(newPassword);

                return Result.ok(oldUser); // 200
            }
        }
    }

    @Override
    public Result<User> deleteUser(String name, String pwd) {
        Result<User> result;

        synchronized (this) {
            result = auxGetUser(name, pwd);
        }

        if (result.isOK()) {
            User user = result.value();
            users.remove(name);
            String userName = user.getName() + DELIMITER + user.getDomain();

            // Descubro onde esta o servidor
            Discovery discovery = Discovery.getInstance();
            String serviceDomain = RestFeedsServer.SERVICE + "." + user.getDomain();
            // Obtenho o URI
            URI[] uris = discovery.knownUrisOf(serviceDomain, MIN_REPLIES);
            URI serverUri = uris[0];
            // Obtenho o servidor
            Feeds feedsServer = new RestFeedsClient(serverUri);

            var res = feedsServer.deleteUserFeed(userName);

            if (res.isOK())
                return Result.ok(user); // 200n
            else
                return Result.error(res.error());
        } else
            return Result.error(result.error()); // erro
    }

    @Override
    public Result<List<User>> searchUsers(String pattern) {
        // Log.info("searchUsers : pattern = " + pattern);

        List<User> result = new LinkedList<>();

        if (pattern == null) {
            // return all users, mesmo que nao haja nenhum
            //     Log.info("Returning all users");
            synchronized (this) {
                result.addAll(users.values());
            }
        } else {
            //    Log.info("Returning all users with " + pattern + " as substring");
            synchronized (this) {
                users.forEach((id, user) -> {
                    // return all users com o pattern
                    if (user.getName().toUpperCase().contains(pattern.toUpperCase())) {
                        result.add(user);
                    }
                });
            }
        }

        return Result.ok(result); // 200
    }

    @Override
    public Result<Void> verifyPassword(String name, String pwd) {
        Result<User> result;
        synchronized (this) {
            result = auxGetUser(name, pwd);
            //result = getUser(name, pwd);
        }

        if (result.isOK())
            return Result.ok();
        else
            return Result.error(result.error());
    }

    @Override
    public Result<Void> checkUser(String name) {
        // Check if user is valid
        if (name == null) {
            // Log.info("UserId or password null.");
            return Result.error(ErrorCode.BAD_REQUEST); // 400
        }

        var user = users.get(name);

        // Check if user exists
        if (user == null) {
            //   Log.info("User does not exist.");
            return Result.error(ErrorCode.NOT_FOUND); // 404
        }

        return Result.ok();
    }
}
