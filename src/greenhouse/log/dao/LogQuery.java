package greenhouse.log.dao;

import java.util.LinkedHashSet;
import java.util.Set;

/** 日志查询条件：字段白名单拼接，值在DAO里走PreparedStatement占位符。 */
public class LogQuery {

    private String keyword = "";
    private String result = "";
    private String reason = "";
    private String source = "";
    private String module = "";
    private String action = "";
    private String startDate = "";
    private String endDate = "";
    private String sortBy = "";
    private String sortDir = "desc";
    private int page = 1;
    private int pageSize = 10;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword == null ? "" : keyword.trim();
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result == null ? "" : result.trim();
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason == null ? "" : reason.trim();
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source == null ? "" : source.trim();
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module == null ? "" : module.trim();
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action == null ? "" : action.trim();
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate == null ? "" : startDate.trim();
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate == null ? "" : endDate.trim();
    }

    public String getSortBy() {
        return sortBy;
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy == null ? "" : sortBy.trim();
    }

    public String getSortDir() {
        return sortDir;
    }

    public void setSortDir(String sortDir) {
        this.sortDir = "asc".equalsIgnoreCase(sortDir) ? "asc" : "desc";
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page < 1 ? 1 : page;
    }

    public int getPageSize() {
        return pageSize;
    }

    /** 每页只允许 10 / 20 / 50，其他值按 10 处理。 */
    public void setPageSize(int pageSize) {
        Set<Integer> allowed = new LinkedHashSet<Integer>();
        allowed.add(10);
        allowed.add(20);
        allowed.add(50);
        this.pageSize = allowed.contains(pageSize) ? pageSize : 10;
    }

    public int getOffset() {
        return (page - 1) * pageSize;
    }
}
