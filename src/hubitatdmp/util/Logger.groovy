package hubitatdmp.util

class Logger {
    static void debug(owner, String msg){ owner?.log?.debug msg }
    static void info(owner, String msg){ owner?.log?.info msg }
    static void warn(owner, String msg){ owner?.log?.warn msg }
    static void error(owner, String msg){ owner?.log?.error msg }
}
