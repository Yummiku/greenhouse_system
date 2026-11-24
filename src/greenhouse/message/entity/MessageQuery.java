package greenhouse.message.entity;

import java.time.LocalDateTime;

/** 消息查询条件：字段值由Servlet按白名单解析，DAO只按固定顺序拼装占位符。 */
public class MessageQuery {

    public static final int DEFAULT_PAGE_SIZE = 10;

    private String keyword;
    private String messageType;
    /** 收件箱已读状态：空为全部，unread或read。 */
    private String readState;
    /** 发件箱状态：空为全部，draft或sent。 */
    private String sendStatus;
    private LocalDateTime from;
    private LocalDateTime to;
    private String sortField;
    private String sortDir;
    private int page = 1;
    private int pageSize = DEFAULT_PAGE_SIZE;

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    public String getReadState() { return readState; }
    public void setReadState(String readState) { this.readState = readState; }

    public String getSendStatus() { return sendStatus; }
    public void setSendStatus(String sendStatus) { this.sendStatus = sendStatus; }

    public LocalDateTime getFrom() { return from; }
    public void setFrom(LocalDateTime from) { this.from = from; }

    public LocalDateTime getTo() { return to; }
    public void setTo(LocalDateTime to) { this.to = to; }

    public String getSortField() { return sortField; }
    public void setSortField(String sortField) { this.sortField = sortField; }

    public String getSortDir() { return sortDir; }
    public void setSortDir(String sortDir) { this.sortDir = sortDir; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page < 1 ? 1 : page; }

    public int getPageSize() { return pageSize; }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, 100);
    }

    public int getOffset() { return (page - 1) * pageSize; }
}
