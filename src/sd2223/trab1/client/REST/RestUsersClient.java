package sd2223.trab1.client.REST;

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import sd2223.trab1.api.User;
import sd2223.trab1.api.java.Result;
import sd2223.trab1.api.java.Users;
import sd2223.trab1.api.rest.UsersService;

import java.net.URI;
import java.util.List;

public class RestUsersClient extends RestClient implements Users {

    final WebTarget target;

    public RestUsersClient(URI serverURI) {
        super(serverURI);
        target = client.target(serverURI).path(UsersService.PATH);
    }

    @Override
    public Result<String> createUser(User user) {
        return super.reTry(() -> clt_createUser(user));
    }

    @Override
    public Result<User> getUser(String name, String pwd) {
        return super.reTry(() -> clt_getUser(name, pwd));
    }

    @Override
    public Result<User> updateUser(String name, String pwd, User user) {
        return super.reTry(() -> clt_updateUser(name, pwd, user));
    }

    @Override
    public Result<User> deleteUser(String name, String pwd) {
        return super.reTry(() -> clt_delUser(name, pwd));
    }

    @Override
    public Result<List<User>> searchUsers(String pattern) {
        return super.reTry(() -> clt_searchUser(pattern));
    }

    @Override
    public Result<Void> verifyPassword(String name, String pwd) {
        return super.reTry(() -> clt_verifyPassword(name, pwd));
    }

    @Override
    public Result<Void> checkUser(String name) {
        return super.reTry(() -> clt_checkUser(name));
    }


    private Result<String> clt_createUser(User user) {
        Response r = target
                .request()
                .post(Entity.entity(user, MediaType.APPLICATION_JSON));

        return super.toJavaResult(r, String.class);
    }

    private Result<User> clt_getUser(String name, String pwd) {
        Response r = target
                .path(name)
                .queryParam(UsersService.PWD, pwd)
                .request()
                .accept(MediaType.APPLICATION_JSON)
                .get();

        return super.toJavaResult(r, User.class);
    }

    private Result<User> clt_updateUser(String name, String pwd, User user) {
        Response r = target
                .path(name)
                .path(UsersService.PWD)
                .queryParam(UsersService.PWD, pwd)
                .request()
                .put(Entity.entity(user, MediaType.APPLICATION_JSON));

        return super.toJavaResult(r, User.class);
    }

    private Result<User> clt_delUser(String name, String pwd) {
        Response r = target
                .path(name)
                .queryParam(UsersService.PWD, pwd)
                .request()
                .accept(MediaType.APPLICATION_JSON)
                .delete();

        return super.toJavaResult(r, User.class);
    }

    private Result<List<User>> clt_searchUser(String pattern) {
        Response r = target
                .queryParam(UsersService.QUERY, pattern)
                .request()
                .accept(MediaType.APPLICATION_JSON)
                .get();
        return super.toJavaResult(r, new GenericType<List<User>>() {
        });
    }

    private Result<Void> clt_verifyPassword(String name, String pwd) {
        Response r = target
                .path(name)
                .path(UsersService.PWD)
                .queryParam(UsersService.PWD, pwd)
                .request()
                .get();

        return super.toJavaResult(r, Void.class);
    }

    private Result<Void> clt_checkUser(String name) {
        Response r = target
                .path(name)
                .request()
                .get();

        return super.toJavaResult(r, Void.class);
    }


}
