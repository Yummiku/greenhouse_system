package greenhouse.identity;

/** 可对外显示的业务错误；数据库异常另行记录，不返回SQL细节。 */
public class UserProblem extends RuntimeException {
    public final int status;
    public UserProblem(int status, String message) {
        super(message);
        this.status = status;
    }
}
