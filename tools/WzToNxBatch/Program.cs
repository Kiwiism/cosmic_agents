using MapleLib.WzLib;
using MapleLib.WzLib.Serializer;

if (args.Length < 2)
{
    Console.Error.WriteLine("Usage: WzToNxBatch <wzInputDirectory> <nxOutputDirectory> [--skip-existing]");
    return 2;
}

var wzInputDirectory = Path.GetFullPath(args[0]);
var nxOutputDirectory = Path.GetFullPath(args[1]);
var skipExisting = args.Skip(2).Any(arg => string.Equals(arg, "--skip-existing", StringComparison.OrdinalIgnoreCase));

if (!Directory.Exists(wzInputDirectory))
{
    Console.Error.WriteLine($"Input directory does not exist: {wzInputDirectory}");
    return 2;
}

Directory.CreateDirectory(nxOutputDirectory);

var wzFiles = Directory.GetFiles(wzInputDirectory, "*.wz", SearchOption.TopDirectoryOnly)
    .Where(path => !string.Equals(Path.GetFileName(path), "List.wz", StringComparison.OrdinalIgnoreCase))
    .OrderBy(path => Path.GetFileName(path), StringComparer.OrdinalIgnoreCase)
    .ToArray();

if (wzFiles.Length == 0)
{
    Console.Error.WriteLine($"No .wz files found in: {wzInputDirectory}");
    return 2;
}

Console.WriteLine($"Input:  {wzInputDirectory}");
Console.WriteLine($"Output: {nxOutputDirectory}");
Console.WriteLine($"Files:  {wzFiles.Length}");

var serializer = new WzToNxSerializer();
var failures = new List<string>();

foreach (var wzPath in wzFiles)
{
    var wzName = Path.GetFileName(wzPath);
    var nxPath = Path.Combine(nxOutputDirectory, Path.ChangeExtension(wzName, ".nx"));

    if (skipExisting && File.Exists(nxPath) && new FileInfo(nxPath).Length > 0)
    {
        Console.WriteLine($"Skipping existing {Path.GetFileName(nxPath)}");
        continue;
    }

    Console.WriteLine($"Converting {wzName} -> {Path.GetFileName(nxPath)}");

    try
    {
        using var wzFile = new WzFile(wzPath, WzMapleVersion.GMS);
        var status = wzFile.ParseWzFile();
        if (status != WzFileParseStatus.Success)
        {
            failures.Add($"{wzName}: parse failed with {status}");
            Console.Error.WriteLine($"FAILED {wzName}: parse failed with {status}");
            continue;
        }

        serializer.SerializeFile(wzFile, nxPath);

        var outputFile = new FileInfo(nxPath);
        if (!outputFile.Exists || outputFile.Length == 0)
        {
            failures.Add($"{wzName}: output missing or empty");
            Console.Error.WriteLine($"FAILED {wzName}: output missing or empty");
            continue;
        }

        Console.WriteLine($"OK {outputFile.Name} ({outputFile.Length:N0} bytes)");
    }
    catch (Exception ex)
    {
        failures.Add($"{wzName}: {ex.GetType().Name}: {ex.Message}");
        Console.Error.WriteLine($"FAILED {wzName}: {ex.GetType().Name}: {ex.Message}");
    }
}

if (failures.Count > 0)
{
    Console.Error.WriteLine("Failures:");
    foreach (var failure in failures)
    {
        Console.Error.WriteLine($"- {failure}");
    }
    return 1;
}

Console.WriteLine("Done.");
return 0;
