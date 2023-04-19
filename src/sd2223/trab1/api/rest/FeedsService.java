package sd2223.trab1.api.rest;

import java.util.List;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import sd2223.trab1.api.Message;
import sd2223.trab1.api.PropMsgHelper;

@Path(FeedsService.PATH)
public interface FeedsService {

    String MID = "mid";
    String PWD = "pwd";
    String USER = "user";
    String TIME = "time";
    String DOMAIN = "domain";
    String USERSUB = "userSub";

    String PATH = "/feeds";

    /**
     * Posts a new message in the feed, associating it to the feed of the specific user.
     * A message should be identified before publish it, by assigning an ID.
     * A user must contact the server of her domain directly (i.e., this operation should not be
     * propagated to other domain)
     *
     * @param user user of the operation (format user@domain)
     * @param msg  the message object to be posted to the server
     * @param pwd  password of the user sending the message
     * @return 200 the unique numerical identifier for the posted message;
     * 404 if the publisher does not exist in the current domain
     * 403 if the pwd is not correct
     * 400 otherwise
     */
    @POST
    @Path("/{" + USER + "}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    long postMessage(@PathParam(USER) String user, @QueryParam(PWD) String pwd, Message msg);

    /**
     * Removes the message identified by mid from the feed of user.
     * A user must contact the server of her domain directly (i.e., this operation should not be
     * propagated to other domain)
     *
     * @param user user feed being accessed (format user@domain)
     * @param mid  the identifier of the message to be deleted
     * @param pwd  password of the user
     * @return 204 if ok
     * 403 if the pwd is not correct
     * 404 is generated if the message does not exist in the server or if the user does not exist
     */
    @DELETE
    @Path("/{" + USER + "}/{" + MID + "}")
    void removeFromPersonalFeed(@PathParam(USER) String user, @PathParam(MID) long mid, @QueryParam(PWD) String pwd);

    /**
     * Obtains the message with id from the feed of user (may be a remote user)
     *
     * @param user user feed being accessed (format user@domain)
     * @param mid  id of the message
     * @return 200 the message if it exists;
     * 404 if the user or the message does not exists
     */
    @GET
    @Path("/{" + USER + "}/{" + MID + "}")
    @Produces(MediaType.APPLICATION_JSON)
    Message getMessage(@PathParam(USER) String user, @PathParam(MID) long mid);

    /**
     * Returns a list of all messages stored in the server for a given user newer than time
     * (note: may be a remote user)
     *
     * @param user user feed being accessed (format user@domain)
     * @param time the oldest time of the messages to be returned
     * @return 200 a list of messages, potentially empty;
     * 404 if the user does not exist.
     */
    @GET
    @Path("/{" + USER + "}")
    @Produces(MediaType.APPLICATION_JSON)
    List<Message> getMessages(@PathParam(USER) String user, @QueryParam(TIME) long time);


    /**
     * Subscribe a user.
     * A user must contact the server of her domain directly (i.e., this operation should not be
     * propagated to other domain)
     *
     * @param user    the user subscribing (following) other user (format user@domain)
     * @param userSub the user to be subscribed (followed) (format user@domain)
     * @param pwd     password of the user
     * @return 204 if ok
     * 404 is generated if the user or the user to be subscribed does not exist
     * 403 is generated if the pwd is not correct
     */
    @POST
    @Path("/sub/{" + USER + "}/{" + USERSUB + "}")
    void subUser(@PathParam(USER) String user, @PathParam(USERSUB) String userSub, @QueryParam(PWD) String pwd);


    /**
     * UnSubscribe a user
     * A user must contact the server of her domain directly (i.e., this operation should not be
     * propagated to other domain)
     *
     * @param user    the user unsubscribing (following) other user (format user@domain)
     * @param userSub the identifier of the user to be unsubscribed
     * @param pwd     password of the user
     * @return 204 if ok
     * 404 is generated if the user or the user to be unsubscribed does not exist
     * 403 is generated if the pwd is not correct
     */
    @DELETE
    @Path("/sub/{" + USER + "}/{" + USERSUB + "}")
    void unsubscribeUser(@PathParam(USER) String user, @PathParam(USERSUB) String userSub, @QueryParam(PWD) String pwd);

    /**
     * Subscribed users.
     *
     * @param user user being accessed (format user@domain)
     * @return 200 if ok
     * 404 is generated if the user does not exist
     */
    @GET
    @Path("/sub/list/{" + USER + "}")
    @Produces(MediaType.APPLICATION_JSON)
    List<String> listSubs(@PathParam(USER) String user);

    /**
     * Elimina informacao do user no servidor de feeds
     *
     * @param user user being accessed (format user@domain)
     * @return 204 if ok
     */
    @DELETE
    @Path("/{" + USER + "}")
    void deleteUserFeed(@PathParam(USER) String user);


    /**
     * Propaga uma subscricao de um user para um userSub de outro server
     * // Acrecentar user aos followers de userSub
     *
     * @param user    user que propaga    (format user@domain)
     * @param userSub user being accessed (format user@domain)
     * @return 204 if ok
     */
    @POST
    @Path("/{" + USER + "}/suber/" + "{" + USERSUB + "}")
    void propagateSub(@PathParam(USER) String user, @PathParam(USERSUB) String userSub);

    /**
     * Propaga uma mensagem de um user que esta noutro dominio para todos os seguidores de um outro dominio.
     *
     * @param msgAndList objeto que contém a mensagem a ser colocada no subscritores do user que postou a msg e, a lista de subscritores do user no dominio para onde e feito o pedido.
     * @return 204 if ok
     */
    @POST
    @Path("/propagate")
    @Consumes(MediaType.APPLICATION_JSON)
    void propagateMsg(PropMsgHelper msgAndList);


    /**
     * Propaga um unfollow de um user para um userSub de outro server
     * // Retirar user aos followers de userSub
     *
     * @param user    user que propaga    (format user@domain)
     * @param userSub user being accessed (format user@domain)
     * @return 204 if ok
     */
    @DELETE
    @Path("/{" + USER + "}/suber/" + "{" + USERSUB + "}")
    void propagateUnsub(@PathParam(USER) String user, @PathParam(USERSUB) String userSub);

}
