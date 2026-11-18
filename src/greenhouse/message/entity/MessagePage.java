package greenhouse.message.entity;

import java.util.List;

/** 一页查询结果：当前页数据与符合条件的总条数。 */
public class MessagePage {

    private final List<PersonalMessage> list;
    private final long total;
    private final int page;
    private final int pageSize;

    public MessagePage(List<PersonalMessage> list, long total, int page, int pageSize) {
        this.list = list;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
    }

    public List<PersonalMessage> getList() { return list; }
    public long getTotal() { return total; }
    public int getPage() { return page; }
    public int getPageSize() { return pageSize; }
}
