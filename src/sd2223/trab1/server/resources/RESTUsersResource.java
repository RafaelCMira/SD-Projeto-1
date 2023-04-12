package sd2223.trab1.server.resources;

import jakarta.inject.Singleton;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import sd2223.trab1.api.User;
import sd2223.trab1.api.rest.UsersService;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@Singleton
public class RESTUsersResource implements UsersService {

    private final Map<String, User> users = new HashMap<>();

    private static Logger Log = Logger.getLogger(RESTUsersResource.class.getName());

    public RESTUsersResource() {
    }

    private User auxGetUser(String name, String pwd) {
        // Check if user is valid
        if (name == null || pwd == null) {
            // Log.info("UserId or password null.");
            throw new WebApplicationException(Response.Status.BAD_REQUEST); // 400
        }

        var user = users.get(name);

        // Check if user exists
        if (user == null || !user.getName().equals(name)) {
            //   Log.info("User does not exist.");
            throw new WebApplicationException(Response.Status.NOT_FOUND); // 404
        }

        //Check if the password is correct
        if (!user.getPwd().equals(pwd)) {
            //  Log.info("Password is incorrect.");
            throw new WebApplicationException(Response.Status.FORBIDDEN); // 403
        }

        return user;
    }

    @Override
    public String createUser(User user) {
        // Check if user data is valid
        if (user.getName() == null || user.getPwd() == null || user.getDisplayName() == null ||
                user.getDomain() == null) {
            //   Log.info("User object invalid.");
            throw new WebApplicationException(Response.Status.BAD_REQUEST); // 400
        }

        synchronized (this) {
            // Insert new user, checking if userId already exists
            if (users.putIfAbsent(user.getName(), user) != null) {
                //     Log.info("User already exists.");
                throw new WebApplicationException(Response.Status.CONFLICT); // 409
            }
        }

        // Log.info("createUser : " + user);
        return user.getName() + "@" + user.getDomain();
    }

    @Override
    public User getUser(String name, String pwd) {
        User user;

        synchronized (this) {
            user = auxGetUser(name, pwd);
        }

        //  Log.info("getUser : user = " + name + "; pwd = " + pwd);
        return user; // 200
    }

    @Override
    public User updateUser(String name, String pwd, User user) {
        // TODO
        User oldUser;
        synchronized (this) {
            // Ja trata dos erros: 400, 403, 404
            oldUser = auxGetUser(name, pwd);

            String newDisplayName = user.getDisplayName();
            if (newDisplayName != null)
                oldUser.setDisplayName(newDisplayName);

            String newPassword = user.getPwd();
            if (newPassword != null)
                oldUser.setPwd(newPassword);
        }

        // Log.info("updateUser debuger : user = " + name + "; pwd = " + pwd + " ; user = " + user);
        return oldUser; // 200
    }

    @Override
    public User deleteUser(String name, String pwd) {
        User oldUser;

        synchronized (this) {
            oldUser = auxGetUser(name, pwd); // 400 ou 403 ou 404
            users.remove(name);
        }

        //    Log.info("deleteUser : user = " + name + "; pwd = " + pwd);
        return oldUser; // 200
    }

    @Override
    public List<User> searchUsers(String pattern) {
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

        return result;
    }
}
