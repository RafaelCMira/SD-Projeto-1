package sd2223.trab1.server.java;

import sd2223.trab1.api.User;
import sd2223.trab1.api.java.Result;
import sd2223.trab1.api.java.Result.ErrorCode;
import sd2223.trab1.api.java.Users;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class JavaUsers implements Users {

    private final Map<String, User> users = new HashMap<>();

    private Result<User> auxGetUser(String name, String pwd) {
        // Check if user is valid
        if (name == null || pwd == null) {
            // Log.info("UserId or password null.");
            return Result.error(ErrorCode.BAD_REQUEST); // 400
        }

        var user = users.get(name);

        // Check if user exists
        if (user == null) {
            //   Log.info("User does not exist.");
            return Result.error(ErrorCode.NOT_FOUND); // 404
        }

        //Check if the password is correct
        if (!user.getPwd().equals(pwd)) {
            //  Log.info("Password is incorrect.");
            return Result.error(ErrorCode.FORBIDDEN); // 403
        }

        return Result.ok(user);
    }

    @Override
    public Result<String> createUser(User user) {
        // Check if user data is valid
        if (user.getName() == null || user.getPwd() == null || user.getDisplayName() == null ||
                user.getDomain() == null) {
            //   Log.info("User object invalid.");
            return Result.error(ErrorCode.BAD_REQUEST); // 400
        }

        synchronized (this) {
            // Insert new user, checking if userId already exists
            if (users.putIfAbsent(user.getName(), user) != null) {
                //     Log.info("User already exists.");
                return Result.error(ErrorCode.CONFLICT); // 409
            }
        }

        String name = user.getName() + "@" + user.getDomain();
        // Log.info("createUser : " + user);
        return Result.ok(name);
    }

    @Override
    public Result<User> getUser(String name, String pwd) {
        Result<User> result;

        synchronized (this) {
            result = auxGetUser(name, pwd);
        }

        if (result.isOK())
            return Result.ok(result.value()); // 200
        else
            return Result.error(result.error()); // erro
    }

    @Override
    public Result<User> updateUser(String name, String pwd, User user) {
        Result<User> result;
        synchronized (this) {
            // Ja trata dos erros: 400, 403, 404
            result = auxGetUser(name, pwd);

            if (result.isOK()) {
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
            } else
                return Result.error(result.error()); // erro
        }
    }

    @Override
    public Result<User> deleteUser(String name, String pwd) {
        Result<User> result;

        synchronized (this) {
            result = auxGetUser(name, pwd);
        }

        if (result.isOK()) {
            users.remove(name);
            return Result.ok(result.value()); // 200
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
        var result = getUser(name, pwd);
        if (result.isOK())
            return Result.ok();
        else
            return Result.error(result.error());
    }
}
