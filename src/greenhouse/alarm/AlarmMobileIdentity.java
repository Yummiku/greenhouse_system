package greenhouse.alarm;

import java.sql.*;
import java.util.*;
import week08.bind.BindDao;
import greenhouse.config.LocalSettings;
import static greenhouse.alarm.AlarmDatabase.*;

/** Extends existing WeChat tokens for alarm read access; does not create a second login scheme. */
final class AlarmMobileIdentity {
    private static String appid(){return LocalSettings.get("week08.wxAppId","WEEK08_WX_APPID","").trim();}
    static void registerFresh(String token) throws Exception {
        if(token==null||!token.matches("[a-f0-9]{64}"))return;
        try(var c=open()) {
            update(c,"""
                INSERT IGNORE INTO alarm_mobile_grant(token_hash,appid,user_id,auth_version)
                SELECT s.token_hash,s.appid,u.id,u.auth_version FROM week08_login_session s
                JOIN week08_user_bind b ON b.appid=s.appid AND b.openid=s.openid
                JOIN todo_login_user u ON u.id=b.user_id
                WHERE s.token_hash=? AND s.appid=? AND s.expires_at>NOW() AND u.status='active' AND u.role_id IN (1,2,3)
                """,BindDao.hash(token),appid());
        }
    }
    static void require(String token) throws Exception {
        if(token==null||!token.matches("[a-f0-9]{64}"))throw new SecurityException("登录凭证无效，请重新登录");
        if(appid().isEmpty())throw new SQLException("小程序AppID未配置");
        try(var c=open()) {
            var user=one(c,"""
                SELECT u.id FROM week08_login_session s
                JOIN week08_user_bind b ON b.appid=s.appid AND b.openid=s.openid
                JOIN todo_login_user u ON u.id=b.user_id
                JOIN alarm_mobile_grant g ON g.token_hash=s.token_hash AND g.appid=s.appid AND g.user_id=u.id AND g.auth_version=u.auth_version
                WHERE s.token_hash=? AND s.appid=? AND s.expires_at>NOW() AND u.status='active' AND u.role_id IN (1,2,3)
                """,BindDao.hash(token),appid());
            if(user==null)throw new SecurityException("凭证过期、账号状态变化或尚未绑定，请重新登录并绑定");
        }
    }
}
