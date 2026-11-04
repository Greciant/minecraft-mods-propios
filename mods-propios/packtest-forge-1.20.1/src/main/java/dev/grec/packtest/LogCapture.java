package dev.grec.packtest;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Appender de Log4j enganchado al logger raiz. Guarda las lineas del juego (para expect_log)
 * y cuenta los ERROR/FATAL/excepciones posteriores a entrar al mundo.
 */
final class LogCapture extends AbstractAppender {
    private static final String NAME = "PackTestCapture";
    private static final int MAX_LINES = 200_000;
    private static final int MAX_ERRORS_KEPT = 20;

    private final ConcurrentLinkedDeque<String> lines = new ConcurrentLinkedDeque<>();
    private final AtomicInteger lineCount = new AtomicInteger();
    private final AtomicInteger errorCount = new AtomicInteger();
    private final List<String> firstErrors = new ArrayList<>();
    private volatile boolean recordLines = false;
    private volatile boolean countErrors = false;

    private LogCapture() {
        super(NAME, null, null, true, Property.EMPTY_ARRAY);
    }

    static LogCapture install() {
        LogCapture c = new LogCapture();
        c.start();
        c.attach();
        return c;
    }

    /** Engancha (o vuelve a enganchar si alguien reconfiguro Log4j). */
    void attach() {
        try {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            Configuration cfg = ctx.getConfiguration();
            LoggerConfig root = cfg.getRootLogger();
            if (root.getAppenders().containsKey(NAME)) return;
            root.addAppender(this, Level.ALL, null);
            // Loggers sin aditividad no llegan al raiz: engancharse tambien a ellos.
            for (LoggerConfig lc : cfg.getLoggers().values()) {
                if (lc != root && !lc.isAdditive() && !lc.getAppenders().containsKey(NAME)) lc.addAppender(this, Level.ALL, null);
            }
            ctx.updateLoggers();
        } catch (Throwable t) {
            PackTest.LOG.error("[packtest] no se pudo enganchar el appender de Log4j", t);
        }
    }

    void startRecordingLines() {
        lines.clear();
        lineCount.set(0);
        recordLines = true;
    }

    void startCountingErrors() {
        countErrors = true;
    }

    @Override
    public void append(LogEvent event) {
        String logger = event.getLoggerName();
        if (PackTest.LOGGER_NAME.equals(logger)) return;
        String msg;
        try {
            msg = event.getMessage() == null ? "" : event.getMessage().getFormattedMessage();
        } catch (Throwable t) {
            msg = "<mensaje no formateable>";
        }
        Throwable thrown = event.getThrown();
        if (recordLines) {
            String line = "[" + event.getLevel() + "] [" + logger + "] " + msg + (thrown != null ? " | " + thrown : "");
            lines.addLast(line);
            if (lineCount.incrementAndGet() > MAX_LINES) {
                lines.pollFirst();
                lineCount.decrementAndGet();
            }
        }
        if (countErrors && (event.getLevel().isMoreSpecificThan(Level.ERROR) || thrown != null)) {
            errorCount.incrementAndGet();
            synchronized (firstErrors) {
                if (firstErrors.size() < MAX_ERRORS_KEPT) {
                    String m = msg.length() > 300 ? msg.substring(0, 300) + "..." : msg;
                    firstErrors.add(event.getLevel() + " " + logger + ": " + m.replace('\n', ' ')
                            + (thrown != null ? " | " + thrown.getClass().getName() + ": " + thrown.getMessage() : ""));
                }
            }
        }
    }

    boolean anyLineMatches(Pattern p) {
        for (String l : lines) if (p.matcher(l).find()) return true;
        return false;
    }

    int errorCount() {
        return errorCount.get();
    }

    List<String> firstErrors() {
        synchronized (firstErrors) {
            return new ArrayList<>(firstErrors);
        }
    }
}
