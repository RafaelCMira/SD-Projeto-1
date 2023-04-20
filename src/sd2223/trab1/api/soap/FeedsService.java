package sd2223.trab1.api.soap;

import java.util.List;

import jakarta.jws.WebMethod;
import jakarta.jws.WebService;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import sd2223.trab1.api.Message;
import sd2223.trab1.api.PropMsgHelper;

@WebService(serviceName = FeedsService.NAME, targetNamespace = FeedsService.NAMESPACE, endpointInterface = FeedsService.INTERFACE)
public interface FeedsService {

    static final String NAME = "feeds";
    static final String NAMESPACE = "http://sd2223";
    static final String INTERFACE = "sd2223.trab1.api.soap.FeedsService";

    /**
     * Posts a new message in the feed, associating it to the feed of the specific user.
     * A message should be identified before publish it, by assigning an ID.
     * A user must contact the server of her domain directly (i.e., this operation should not be propagated to other domain)
     *
     * @param user user of the operation (format user@domain)
     * @param msg  the message object to be posted to the server
     * @param pwd  password of the user sending the message
     * @return the unique numerical identifier for the posted message;
     * @throws FORBIDDEN if the publisher does not exist in the current domain or if the pwd is not correct
     *                   BAD_REQUEST otherwise
     */
    @WebMethod
    long postMessage(String user, String pwd, Message msg) throws FeedsException;

    /**
     * Removes the message identified by mid from the feed of user.
     * A user must contact the server of her domain directly (i.e., this operation should not be
     * propagated to other domain)
     *
     * @param user user feed being accessed (format user@domain)
     * @param mid  the identifier of the message to be deleted
     * @param pwd  password of the user
     * @throws FORBIDDEN if the publisher does not exist in the current domain or if the pwd is not correct
     *                   BAD_REQUEST otherwise
     */
    @WebMethod
    void removeFromPersonalFeed(String user, long mid, String pwd) throws FeedsException;

    /**
     * Obtains the message with id from the feed of user (may be a remote user)
     *
     * @param user user feed being accessed (format user@domain)
     * @param mid  id of the message
     * @return the message if it exists;
     * @throws NOT_FOUND if the user or the message does not exist
     */
    @WebMethod
    Message getMessage(String user, long mid) throws FeedsException;

    /**
     * Returns a list of all messages stored in the server for a given user newer than time
     * (note: may be a remote user)
     *
     * @param user user feed being accessed (format user@domain)
     * @param time the oldest time of the messages to be returned
     * @return a list of messages, potentially empty;
     * @throws NOT_FOUND if the user does not exist.
     */
    @WebMethod
    List<Message> getMessages(String user, long time) throws FeedsException;

    /**
     * Subscribe a user.
     * A user must contact the server of her domain directly (i.e., this operation should not be
     * propagated to other domain)
     *
     * @param user    the user subscribing (following) other user (format user@domain)
     * @param userSub the user to be subscribed (followed) (format user@domain)
     * @param pwd     password of the user to subscribe
     * @throws NOT_FOUND if the user to be subscribed does not exist
     *                   FORBIDDEN if the user does not exist or if the pwd is not correct
     */
    @WebMethod
    void subUser(String user, String userSub, String pwd) throws FeedsException;

    /**
     * UnSubscribe a user
     * A user must contact the server of her domain directly (i.e., this operation should not be
     * propagated to other domain)
     *
     * @param user    the user unsubscribing (following) other user (format user@domain)
     * @param userSub the identifier of the user to be unsubscribed
     * @param pwd     password of the user to subscribe
     * @throws FORBIDDEN if the user does not exist or if the pwd is not correct
     *                   NOT_FOUND the userSub is not subscribed
     */
    @WebMethod
    void unsubscribeUser(String user, String userSub, String pwd) throws FeedsException;

    /**
     * Subscribed users.
     *
     * @param user user being accessed (format user@domain)
     * @throws NOT_FOUND if the user does not exist
     */
    @WebMethod
    List<String> listSubs(String user) throws FeedsException;


    /**
     * Elimina informacao do user no servidor de feeds
     *
     * @param user user being accessed (format user@domain)
     * @throws NOT_FOUND if the user does not exist
     */
    void deleteUserFeed(String user) throws FeedsException;

    /**
     * Propaga uma subscricao de um user para um userSub de outro server
     *
     * @param user    user que propaga    (format user@domain)
     * @param userSub userSub being accessed (format user@domain)
     * @throws NOT_FOUND if the user or userSub do not exist
     */
    void propagateSub(String user, String userSub) throws FeedsException;

    /**
     * Propaga um unfollow de um user para um userSub de outro server
     *
     * @param user    user que propaga    (format user@domain)
     * @param userSub user being accessed (format user@domain)
     * @throws NOT_FOUND if the user or userSub do not exist
     */
    void propagateUnsub(String user, String userSub) throws FeedsException;

    /**
     * Propaga uma mensagem de um user que esta num dominio para todos os seguidores de um outro dominio.
     *
     * @param msgAndList objeto que contém a mensagem a ser colocada no subscritores do user que postou a msg e, a lista de subscritores do user no dominio para onde e feito o pedido.
     *                   // @throws NOT_FOUND if the user or userSub do not exist
     */
    void propagateMsg(PropMsgHelper msgAndList) throws FeedsException;
}
