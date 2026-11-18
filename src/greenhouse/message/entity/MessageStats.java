package greenhouse.message.entity;

import java.util.LinkedHashMap;
import java.util.Map;

/** 本人可见消息的统计结果：总数、未读、已读及类型分布。 */
public class MessageStats {

    private final long total;
    private final long unread;
    private final long read;
    private final Map<String, Long> byType = new LinkedHashMap<>();

    public MessageStats(long total, long unread, long read) {
        this.total = total;
        this.unread = unread;
        this.read = read;
    }

    public long getTotal() { return total; }
    public long getUnread() { return unread; }
    public long getRead() { return read; }
    public Map<String, Long> getByType() { return byType; }

    public void putType(String type, long count) {
        byType.put(type == null || type.isBlank() ? "未分类" : type, count);
    }
}
