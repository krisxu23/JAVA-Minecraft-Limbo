package ua.nanit.limbo.server;

import java.time.format.DateTimeFormatter;
import java.time.Instant;
import java.time.ZoneId;
import java.util.logging.*;

public class Log {
    private static final Logger LOGGER = Logger.getLogger("Server");
    private static int debugLevel = 2;

    static {
        LOGGER.setUseParentHandlers(false);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.ALL);
        handler.setFormatter(new Formatter() {
            private final DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
                    .withZone(ZoneId.systemDefault());
            @Override
            public String format(LogRecord record) {
                String timestamp = timeFormat.format(Instant.ofEpochMilli(record.getMillis()));
                String level = switch (record.getLevel()) {
                    case INFO -> "INFO  -- ";
                    case WARNING -> "WARN  -- ";
                    case SEVERE -> "ERROR -- ";
                    case FINE -> "DEBUG -- ";
                    default -> record.getLevel().getName();
                };
                return String.format("[%s] %s%s", timestamp, level, record.getMessage());
            }
        });
        LOGGER.addHandler(handler);
        LOGGER.setLevel(Level.ALL);
    }

    public static void setLevel(int level) { debugLevel = level; }

    public static void debug(Object msg, Object... args) {
        if (debugLevel >= 3) printFormatted(Level.FINE, msg, args);
    }

    public static void info(Object msg, Object... args) {
        if (debugLevel >= 2) printFormatted(Level.INFO, msg, args);
    }

    public static void warn(Object msg, Object... args) {
        if (debugLevel >= 1) printFormatted(Level.WARNING, msg, args);
    }

    public static void warning(Object msg, Object... args) { warn(msg, args); }

    public static void error(Object msg, Object... args) {
        if (debugLevel >= 0) {
            String text = formatArgs(msg, args);
            if (args.length > 0 && args[args.length - 1] instanceof Throwable t) {
                LOGGER.log(Level.SEVERE, text, t);
            } else {
                LOGGER.log(Level.SEVERE, text);
            }
        }
    }

    private static String formatArgs(Object msg, Object... args) {
        String text = msg.toString();
        if (args.length == 0) return text;
        if (args.length == 1 && args[0] instanceof Throwable && !msg.toString().contains("%"))
            return text + args[0].toString();
        return String.format(text, args);
    }

    public static boolean isDebug() { return debugLevel >= 3; }
}
