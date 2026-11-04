// Test-only core validation process: never opens a socket or enables TUN.
// The broker must terminate it when its authenticated connection is revoked.
if (!args.Contains("-t")) return 2;
var index = Array.IndexOf(args, "-d");
if (index < 0 || index + 1 >= args.Length) return 2;
var session = Path.GetFullPath(args[index + 1]);
var root = Directory.GetParent(session)?.FullName ?? throw new InvalidDataException();
if (!Path.GetFileName(root).StartsWith("weave-broker-session-", StringComparison.Ordinal)) return 2;
File.WriteAllText(Path.Combine(root, "probe.pid"), Environment.ProcessId.ToString());
await Task.Delay(Timeout.Infinite);
return 0;
