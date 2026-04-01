import 'dart:async';
import 'dart:io';
import 'package:path_provider/path_provider.dart';

/// File-based logger for persistent log storage.
///
/// Writes log entries to a file on disk, with configurable max file size
/// and automatic rotation. Useful for debugging in production when console
/// output is not available.
///
/// Mirrors Android FileLogger for feature parity.
class FileLogger {
  static const int _defaultMaxFileSize = 1024 * 1024; // 1 MB
  static const int _defaultMaxFiles = 4;

  final String _logFileName;
  final int _maxFileSize;
  final int _maxFiles;
  final String? _logDirectory;

  File? _currentFile;
  IOSink? _sink;

  /// Whether file logging is enabled.
  bool enabled;

  FileLogger({
    String logFileName = 'salesforce_sdk.log',
    int maxFileSize = _defaultMaxFileSize,
    int maxFiles = _defaultMaxFiles,
    String? logDirectory,
    this.enabled = true,
  })  : _logFileName = logFileName,
        _maxFileSize = maxFileSize,
        _maxFiles = maxFiles,
        _logDirectory = logDirectory;

  /// Gets or creates the log directory.
  Future<Directory> _getLogDirectory() async {
    if (_logDirectory != null) {
      final dir = Directory(_logDirectory!);
      if (!await dir.exists()) await dir.create(recursive: true);
      return dir;
    }
    final appDir = await getApplicationDocumentsDirectory();
    final logDir = Directory('${appDir.path}/logs');
    if (!await logDir.exists()) await logDir.create(recursive: true);
    return logDir;
  }

  /// Gets or creates the current log file.
  Future<File> _getLogFile() async {
    if (_currentFile != null) return _currentFile!;
    final dir = await _getLogDirectory();
    _currentFile = File('${dir.path}/$_logFileName');
    return _currentFile!;
  }

  /// Writes a log line to the file.
  Future<void> log(String level, String tag, String message,
      [Object? error]) async {
    if (!enabled) return;

    final file = await _getLogFile();
    await _rotateIfNeeded(file);

    final timestamp = DateTime.now().toIso8601String();
    final logLine = '$timestamp [$level] $tag: $message';
    final buffer = StringBuffer(logLine);
    if (error != null) {
      buffer.write(' | Error: $error');
    }
    buffer.writeln();

    _sink ??= file.openWrite(mode: FileMode.append);
    _sink!.write(buffer.toString());
  }

  /// Flushes pending writes.
  Future<void> flush() async {
    await _sink?.flush();
  }

  /// Reads the entire log file contents.
  Future<String> readLogs() async {
    final file = await _getLogFile();
    if (await file.exists()) {
      return file.readAsString();
    }
    return '';
  }

  /// Reads the last N lines from the log file.
  Future<List<String>> readLastLines(int count) async {
    final content = await readLogs();
    final lines = content.split('\n');
    if (lines.length <= count) return lines;
    return lines.sublist(lines.length - count);
  }

  /// Clears all log files.
  Future<void> clearLogs() async {
    await _sink?.flush();
    await _sink?.close();
    _sink = null;

    final dir = await _getLogDirectory();
    final files = dir.listSync().whereType<File>().where(
        (f) => f.path.contains(_logFileName.replaceAll('.log', '')));
    for (final file in files) {
      await file.delete();
    }
    _currentFile = null;
  }

  /// Gets the total size of all log files in bytes.
  Future<int> getLogSize() async {
    final dir = await _getLogDirectory();
    int totalSize = 0;
    final files = dir.listSync().whereType<File>();
    for (final file in files) {
      totalSize += await file.length();
    }
    return totalSize;
  }

  /// Returns the path to the current log file.
  Future<String> getLogFilePath() async {
    final file = await _getLogFile();
    return file.path;
  }

  /// Rotates log files if the current file exceeds max size.
  Future<void> _rotateIfNeeded(File file) async {
    if (!await file.exists()) return;
    final size = await file.length();
    if (size < _maxFileSize) return;

    // Close current sink
    await _sink?.flush();
    await _sink?.close();
    _sink = null;

    // Rotate: rename existing files (e.g., .log.3 -> .log.4, .log.2 -> .log.3)
    final dir = await _getLogDirectory();
    final baseName = _logFileName.replaceAll('.log', '');

    // Delete oldest
    final oldest = File('${dir.path}/$baseName.$_maxFiles.log');
    if (await oldest.exists()) await oldest.delete();

    // Shift files
    for (var i = _maxFiles - 1; i >= 1; i--) {
      final from = File('${dir.path}/$baseName.$i.log');
      final to = File('${dir.path}/$baseName.${i + 1}.log');
      if (await from.exists()) await from.rename(to.path);
    }

    // Current -> .1.log
    await file.rename('${dir.path}/$baseName.1.log');
    _currentFile = File('${dir.path}/$_logFileName');
  }

  /// Disposes the logger, flushing and closing the file.
  Future<void> dispose() async {
    await _sink?.flush();
    await _sink?.close();
    _sink = null;
    _currentFile = null;
  }
}
