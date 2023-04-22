package sd2223.trab1.api;

import java.io.Serializable;
import java.util.List;

public class PropMsgHelper implements Serializable {
    private Message msg;
    private String[] subs;

    public PropMsgHelper() {

    }

    public PropMsgHelper(Message msg, String[] subs) {
        this.msg = msg;
        this.subs = subs;
    }

    public Message getMsg() {
        return msg;
    }

    public String[] getSubs() {
        return subs;
    }
}
