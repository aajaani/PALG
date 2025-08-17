# PALG: Programming Activity Log Generator

---

## Description

The PALG plugin generates a log file every time IntelliJ IDEA is started.

The plugin logs the following actions: text insertions, deletions, pasting, file opening, closing, switching, running, and debugging.

The log files are added to the `${user.home}/PALG_logs/` folder. You can also navigate to the log file folder by using the "PALG: Open Log Folder" button after right-clicking in an open editor.

The log files generated can be used by the [PALA: Programming Activity Log Analyzer](https://progtugi.cs.ut.ee/pala/) web application to analyze the contents of the log files and provide an overview of development activities. PALA also provides a replayer to visualize how the code was written in the IDE.

Link to the [PALA repository](https://github.com/Programming-Activity-Log-Analyser/PALA)