package ua.nanit.limbo.proxy;

/**
 * Terminal utilities for console clearing and ANSI color codes.
 * Extracted from NanoLimbo.java during refactoring.
 */
public final class ConsoleUtils {

    public static final String ANSI_GREEN = "\033[1;32m";
    public static final String ANSI_RED = "\033[1;31m";
    public static final String ANSI_RESET = "\033[0m";

    private ConsoleUtils() {}

    /**
     * Clears the terminal and resizes it for better readability.
     */
    public static void clearConsole() {
        try {
            if (System.getProperty("os.name").contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls && mode con: lines=30 cols=120")
                    .inheritIO()
                    .start()
                    .waitFor();
            } else {
                System.out.print("\033[H\033[3J\033[2J");
                System.out.flush();
                new ProcessBuilder("tput", "reset")
                    .inheritIO()
                    .start()
                    .waitFor();
                System.out.print("\033[8;30;120t");
                System.out.flush();
            }
        } catch (Exception e) {
            try {
                new ProcessBuilder("clear").inheritIO().start().waitFor();
            } catch (Exception ignored) {
                // Last-resort fallback — nothing more to try
            }
        }
    }
}
