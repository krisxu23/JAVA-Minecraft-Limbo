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
                String level = formatLevel(record.getLevel());
                return String.format("%s %s %s%n", timestamp, level, record.getMessage());
            }

            private String formatLevel(Level level) {
                if (level == Level.INFO) return "INFO -- ";
                if (level == Level.WARNING) return "WARN -- ";
                if (level == Level.SEVERE) return "ERROR -- ";
                if (level == Level.FINE) return "DEBUG -- ";
                return level.getName();
            }
        });

        LOGGER.addHandler(handler);
        LOGGER.setLevel(Level.ALL);
    }

    public static void setLevel(int level) {
        debugLevel = level;
    }

    public static void debug(Object msg, Object... args) {
        if (debugLevel >= 3) {
            printFormatted(Level.FINE, msg, args);
        }
    }

    public static void info(Object msg, Object... args) {
        if (debugLevel >= 2) {
            printFormatted(Level.INFO, msg, args);
        }
    }

    public static void warn(Object msg, Object... args) {
        if (debugLevel >= 1) {
            printFormatted(Level.WARNING, msg, args);
        }
    }

    public static void warning(Object msg, Object... args) {
        warn(msg, args);
    }

    public static void error(Object msg, Object... args) {
        if (debugLevel >= 0) {
            printFormatted(Level.SEVERE, msg, args);
        }
    }

    private static void printFormatted(Level level, Object msg, Object... args) {
        String text;
        if (args.length == 0) {
            text = msg.toString();
        } else {
            text = String.format(msg.toString(), args);
        }
        LOGGER.log(level, text);
    }

    public static boolean isDebug() {
        return debugLevel >= 3;
    }
}
