package com.muantech.rollbar.java;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.status.StatusLogger;
import org.json.JSONException;

import java.io.Serializable;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;

@Plugin(name = "Rollbar", category = "Core", elementType = "appender", printObject = true)
public class RollbarAppender extends AbstractAppender {

    private static final long serialVersionUID = 1L;

    private static final int DEFAULT_LOGS_LIMITS = 100;

    private static boolean init;
    private static LimitedQueue<String> LOG_BUFFER = new LimitedQueue<String>(DEFAULT_LOGS_LIMITS);
    
    private StatusLogger statusLogger = StatusLogger.getLogger();

    private boolean enabled = true;
    private boolean onlyThrowable = true;
    private boolean logs = true;

    private Level notifyLevel = Level.ERROR;

    private String apiKey;
    private String env;
    private List<String> enabledEnvs = new ArrayList<String>();
    
    private String url = "https://api.rollbar.com/api/1/item/";
    
    private static ThreadLocal<ServletRequest> CURRENT_REQUEST = new ThreadLocal<>();
    
    public RollbarAppender(final String name, final Filter filter, final Layout<? extends Serializable> layout,
            final boolean ignoreExceptions, final String apiKey, final String env, final List<String> enabledEnvs) {
        super(name, filter, layout, ignoreExceptions);
        this.apiKey = apiKey;
        this.env = env;
        this.enabledEnvs = enabledEnvs;
    }
    
    @PluginFactory
    public static RollbarAppender createAppender(@PluginAttribute("name") String name,
                                                 @PluginAttribute("ignoreExceptions") boolean ignoreExceptions,
                                                 @PluginElement("Layout") Layout layout,
                                                 @PluginElement("Filters") Filter filter,
                                                 @PluginAttribute("apiKey") String apiKey,
                                                 @PluginAttribute("env") String env,
                                                 @PluginAttribute("enabledEnvs") String enabledEnvString)
    {
        List<String> envs = new ArrayList<String>();
        
        if(enabledEnvString != null && !enabledEnvString.isEmpty())
        {
            Collections.addAll(envs, enabledEnvString.split(","));
        }
        
        return new RollbarAppender(name, filter, layout, ignoreExceptions, apiKey, env, envs);
    }

    @Override
    public void append(final LogEvent event) {
        if (!enabled) return;
        
        if(!enabledEnvs.isEmpty() && !enabledEnvs.contains(env))
        {
            return;
        }

        try {

            // add to the LOG_BUFFER buffer
            LOG_BUFFER.add(new String(getLayout().toByteArray(event), "UTF-8").trim());

            if (!hasToNotify(event.getLevel())) return;

            boolean hasThrowable = thereIsThrowableIn(event);
            if (onlyThrowable && !hasThrowable) return;

            initNotifierIfNeeded();

            final Map<String, Object> context = getContext(event);

            if (hasThrowable) {
                RollbarNotifier.notify(event.getMessage().getFormattedMessage(), getThrowable(event), context);
            } else {
                RollbarNotifier.notify(event.getMessage().getFormattedMessage(), context);
            }

        } catch (Exception e) {
            statusLogger.error("Error sending error notification! error=" + e.getClass().getName() + " with message=" + e.getMessage());
        }

    }

    private Map<String, Object> getContext(final LogEvent event) {

        @SuppressWarnings("unchecked")
        final Map<String, Object> context = new HashMap<String, Object>();
        context.put("request", RollbarAppender.getCurrentRequest());
        context.put("LOG_BUFFER", LOG_BUFFER);
        
        for(Map.Entry<String, String> ctxEntry : ThreadContext.getContext().entrySet())
        {
            context.put(ctxEntry.getKey(), ctxEntry.getValue());
        }

        return context;
    }

    public boolean hasToNotify(Level level) {
        return level.isMoreSpecificThan(notifyLevel);
    }

    private synchronized void initNotifierIfNeeded() throws JSONException, UnknownHostException {
        if (init) return;
        RollbarNotifier.init(url, apiKey, env);
        init = true;
    }

    public void setApiKey(final String apiKey) {
        this.apiKey = apiKey;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public void setEnv(final String env) {
        this.env = env;
    }

    public boolean isOnlyThrowable() {
        return onlyThrowable;
    }

    public void setOnlyThrowable(boolean onlyThrowable) {
        this.onlyThrowable = onlyThrowable;
    }

    public void setUrl(final String url) {
        this.url = url;
    }

    public Level getNotifyLevel() {
        return notifyLevel;
    }

    public void setLevel(String notifyLevel) {
        this.notifyLevel = Level.toLevel(notifyLevel);
    }

    public boolean isLogs() {
        return logs;
    }

    public void setLogs(boolean logs) {
        this.logs = logs;
    }

    public void setLimit(int limit) {
        RollbarAppender.LOG_BUFFER = new LimitedQueue<String>(limit);
    }

    private boolean thereIsThrowableIn(LogEvent loggingEvent) {
        return loggingEvent.getThrown() != null || loggingEvent.getMessage() instanceof Throwable;
    }

    private Throwable getThrowable(final LogEvent loggingEvent) {
        Throwable throwable = loggingEvent.getThrown();
        if (throwable != null) return throwable;

        Object message = loggingEvent.getMessage();
        if (message instanceof Throwable) {
            return (Throwable) message;
        } else if (message instanceof String) {
            return new Exception((String) message);
        }

        return null;
    }

    private static class LimitedQueue<E> extends LinkedList<E> {

        private static final long serialVersionUID = 6557339882154255572L;

        private final int limit;

        public LimitedQueue(int limit) {
            this.limit = limit;
        }

        @Override
        public boolean add(E o) {
            super.add(o);
            while (size() > limit) {
                super.remove();
            }
            return true;
        }
    }
    
    public static ServletRequest getCurrentRequest()
    {
        return CURRENT_REQUEST.get();
    }
    
    public static void setCurrentRequest(ServletRequest request)
    {
        CURRENT_REQUEST.set(request);
    }

}
