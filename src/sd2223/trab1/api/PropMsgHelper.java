package sd2223.trab1.api;

import java.util.List;

public class PropMsgHelper {

    private Message msg;

    private List<String> subs;

    public PropMsgHelper(Message msg, List<String> subs) {
        this.msg = msg;
        this.subs = subs;
    }

    public Message getMsg() {
        return msg;
    }

    public List<String> getSubs() {
        return subs;
    }
}
