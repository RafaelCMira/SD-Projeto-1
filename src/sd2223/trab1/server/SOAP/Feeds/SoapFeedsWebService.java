package sd2223.trab1.server.SOAP.Feeds;

import jakarta.jws.WebService;
import sd2223.trab1.api.Message;
import sd2223.trab1.api.PropMsgHelper;
import sd2223.trab1.api.java.Feeds;
import sd2223.trab1.api.soap.FeedsException;
import sd2223.trab1.api.soap.FeedsService;
import sd2223.trab1.server.SOAP.SoapWebService;
import sd2223.trab1.server.SOAP.Users.SoapUsersWebService;
import sd2223.trab1.server.java.JavaFeeds;

import java.util.List;
import java.util.logging.Logger;

@WebService(serviceName = FeedsService.NAME, targetNamespace = FeedsService.NAMESPACE, endpointInterface = FeedsService.INTERFACE)
public class SoapFeedsWebService extends SoapWebService<FeedsException> implements FeedsService {

    static Logger Log = Logger.getLogger(SoapUsersWebService.class.getName());
    final Feeds impl;

    public SoapFeedsWebService() {
        super((result) -> new FeedsException(result.error().toString()));
        this.impl = new JavaFeeds();
    }

    @Override
    public long postMessage(String user, String pwd, Message msg) throws FeedsException {
        return super.fromJavaResult(impl.postMessage(user, pwd, msg));
    }

    @Override
    public void removeFromPersonalFeed(String user, long mid, String pwd) throws FeedsException {
        super.fromJavaResult(impl.removeFromPersonalFeed(user, mid, pwd));
    }

    @Override
    public Message getMessage(String user, long mid) throws FeedsException {
        return super.fromJavaResult(impl.getMessage(user, mid));
    }

    @Override
    public List<Message> getMessages(String user, long time) throws FeedsException {
        return super.fromJavaResult(impl.getMessages(user, time));
    }

    @Override
    public void subUser(String user, String userSub, String pwd) throws FeedsException {
        super.fromJavaResult(impl.subUser(user, userSub, pwd));
    }

    @Override
    public void unsubscribeUser(String user, String userSub, String pwd) throws FeedsException {
        super.fromJavaResult(impl.unsubscribeUser(user, userSub, pwd));
    }

    @Override
    public List<String> listSubs(String user) throws FeedsException {
        return super.fromJavaResult(impl.listSubs(user));
    }

    @Override
    public void deleteUserFeed(String user) throws FeedsException {
        super.fromJavaResult(impl.deleteUserFeed(user));
    }

    @Override
    public void propagateSub(String user, String userSub) throws FeedsException {
        super.fromJavaResult(impl.propagateSub(user, userSub));
    }

    @Override
    public void propagateUnsub(String user, String userSub) throws FeedsException {
        super.fromJavaResult(impl.propagateUnsub(user, userSub));
    }

    @Override
    public void propagateMsg(PropMsgHelper msgAndList) throws FeedsException {
        super.fromJavaResult(impl.propagateMsg(msgAndList));
    }
}
