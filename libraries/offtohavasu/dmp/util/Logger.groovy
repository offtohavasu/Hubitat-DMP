library(
    name: "DMP_Logger",
    base: "app",
    author: "Curtis Cienfuegos",
    category: "Security",
    namespace: "offtohavasu",
    documentationLink: "https://github.com/offtohavasu/Hubitat-DMP",
    version: "0.1.0-alpha1",
    description: "Hubitat library utility for DMP logging helpers."
)

package offtohavasu.dmp.util

class Logger {
    static void debug(owner, String msg){ owner?.log?.debug msg }
    static void info(owner, String msg){ owner?.log?.info msg }
    static void warn(owner, String msg){ owner?.log?.warn msg }
    static void error(owner, String msg){ owner?.log?.error msg }
}
