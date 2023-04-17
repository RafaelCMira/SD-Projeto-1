package sd2223.trab1.api;

import java.util.List;

public class PropagateMsgHelper {
    private Message msg;

    private List<String> subs;

    public PropagateMsgHelper(Message msg, List<String> subs) {
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
