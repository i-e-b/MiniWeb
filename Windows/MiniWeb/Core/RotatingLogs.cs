using System.IO;
using System.Reflection;
using System.Text;

namespace MiniWeb.Core;

/// <summary>
/// Log handler that redirects <c>Console.Write...</c> to a set of rotating files
/// </summary>
public class RotatingLogs: TextWriter
{
    private const long MaximumFileSize = (1024 * 1024) - 16;
    private const int MaximumFileCount = 10;

    private FileStream? _target;

    private static string?     _path;
    private static TextWriter? _originalWriter;

    private static readonly Lock          _lock     = new();
    private static readonly RotatingLogs  _instance = new();
    private static readonly StringBuilder _builder  = new();


    private RotatingLogs()
    {
    }

    /// <summary>
    /// Get current logging directory
    /// </summary>
    public static string GetDirectory()
    {
         var p = _path ?? Path.GetDirectoryName(Assembly.GetExecutingAssembly().Location) ?? "";
         if (!p.EndsWith(Path.DirectorySeparatorChar)) p += Path.DirectorySeparatorChar;

         return p;
    }

    /// <summary>
    /// Redirect logs to an output directory
    /// </summary>
    public static void SetOutputDirectory(string path)
    {
        Directory.CreateDirectory(path);
        _path = path;
        _instance.ResetPath();

        _originalWriter ??= Console.Out;
        Console.SetOut (_instance);
    }

    /// <summary>
    /// Clear existing writers, set new output directory
    /// </summary>
    private void ResetPath()
    {
        lock (_lock)
        {
            _target?.Close();
            _target?.Dispose();

            _builder.Clear();

            RotateLogs();
        }
    }

    /// <inheritdoc />
    protected override void Dispose(bool disposing)
    {
        lock (_lock)
        {
            _target?.Flush();
            _target?.Close();
            _target = null;
        }
    }

    /// <inheritdoc />
    public override void Write(char value)
    {
        _originalWriter?.Write(value);
        _builder.Append(value);

        if (value == '\n') Flush(); // Flush output on each new line
    }

    /// <inheritdoc />
    public override void Flush()
    {
        _originalWriter?.Flush();


        lock (_lock)
        {
            if (_target is null || _builder.Length <= 0) return;

            var bytes = Encoding.UTF8.GetBytes(_builder.ToString());
            _builder.Clear();
            _target.Write(bytes, 0, bytes.Length);
            _target.Flush();

            if (_target.Length > MaximumFileSize)
            {
                RotateLogs();
            }
        }
    }

    /// <summary>
    /// Open an append session to the most recent log file under the maximum size.
    /// <p>
    /// If no such file exists, create a new log file and open that
    /// </p>
    /// Keep only the most recent few files
    /// </summary>
    private void RotateLogs()
    {
        if (_path is null)
        {
            _originalWriter?.Write("[Invalid log path]");
            return;
        }

        // Close existing file
        if (_target is not null)
        {
            _target.Flush();
            _target.Close();
            _target = null;
        }

        // Get log files, ordered by most recent first
        var logs   = Directory.GetFiles(_path, "*.txt").OrderByDescending(s=>s).ToList();
        var prefix = DateTime.UtcNow.ToString("yyyy-MM-ddTHH-mm-ss"); // must not use ':'

        // If no logs, start a new one
        if (logs.Count < 1)
        {
            _target = File.Open(Path.Combine(_path, $"{prefix}_log.txt"), FileMode.Append);
            return;
        }

        // Clean up old files
        for (int i = MaximumFileCount; i < logs.Count; i++)
        {
            File.Delete(logs[i]);
        }

        // If newest log is ok, use it
        var info = new FileInfo(logs[0]);
        if (info.Length < MaximumFileSize)
        {
            _target = File.Open(logs[0], FileMode.Append);
            return;
        }

        // No usable log files. Start a new one
        _target = File.Open(Path.Combine(_path, $"{prefix}_log.txt"), FileMode.Append);
    }

    /// <inheritdoc />
    public override Encoding Encoding => Encoding.UTF8;
}