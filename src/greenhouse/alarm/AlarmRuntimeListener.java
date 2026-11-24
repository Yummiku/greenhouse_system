package greenhouse.alarm;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebListener;
import java.sql.SQLException;
import java.util.concurrent.*;

/** Does not take ownership of the sampling servlet or modify the shared LoginFilter. */
@WebListener
public final class AlarmRuntimeListener implements ServletContextListener {
    private ScheduledExecutorService worker;
    @Override public void contextInitialized(ServletContextEvent event) {
        ServletContext context=event.getServletContext();
        JdbcSampleAlarmProcessor processor=new JdbcSampleAlarmProcessor();
        try {
            processor.initialize();
            context.setAttribute("sampleAlarmProcessor",processor);
            context.setAttribute("sampleAlarmStatus","READY");
            worker=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"xm06-alarm-worker");t.setDaemon(true);return t;});
            worker.scheduleWithFixedDelay(()->{
                try { processor.processDueTasks(50);context.setAttribute("sampleAlarmStatus","READY"); }
                catch(SQLException|RuntimeException failure) {
                    context.setAttribute("sampleAlarmStatus","RETRY_SERVICE_FAILED");
                    // No SQL, connection URL, collector token or raw client body in logs.
                    context.log("Alarm retry service unavailable; verify database and migrations 21/31/33");
                }
            },1,1,TimeUnit.SECONDS);
        }catch(SQLException|RuntimeException failure) {
            context.setAttribute("sampleAlarmStatus","NOT_READY");
            context.log("Alarm processor not started; install migrations 21/31/33 in this application's database and restart");
        }
    }
    @Override public void contextDestroyed(ServletContextEvent event) {
        if(worker!=null)worker.shutdownNow();
        event.getServletContext().removeAttribute("sampleAlarmProcessor");
    }
}
