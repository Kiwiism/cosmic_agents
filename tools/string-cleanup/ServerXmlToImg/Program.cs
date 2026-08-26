using System.Text.Json;
using MapleLib.WzLib;
using MapleLib.WzLib.Serializer;
using MapleLib.WzLib.Util;

if (args.Length is < 2 or > 4)
{
    Console.Error.WriteLine("Usage: ServerXmlToImg <String.wz-xml-root> <Data/String-output-root> [GMS|EMS|BMS|CLASSIC] [comma-separated-file-stems]");
    return 2;
}

string sourceRoot = Path.GetFullPath(args[0]);
string outputRoot = Path.GetFullPath(args[1]);
WzMapleVersion version = args.Length >= 3
    ? Enum.Parse<WzMapleVersion>(args[2], ignoreCase: true)
    : WzMapleVersion.GMS;
HashSet<string> selectedFiles = (args.Length == 4 ? args[3] : "Cash,Consume,Eqp,Etc,Ins,Skill")
    .Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
    .Select(fileName => fileName.EndsWith(".img.xml", StringComparison.OrdinalIgnoreCase)
        ? fileName
        : fileName + ".img.xml")
    .ToHashSet(StringComparer.OrdinalIgnoreCase);

if (!Directory.Exists(sourceRoot))
{
    Console.Error.WriteLine($"String.wz XML root does not exist: {sourceRoot}");
    return 3;
}
if (Directory.Exists(outputRoot) && Directory.EnumerateFileSystemEntries(outputRoot).Any())
{
    Console.Error.WriteLine($"Output directory is not empty; refusing to overwrite it: {outputRoot}");
    return 4;
}

Directory.CreateDirectory(outputRoot);
byte[] iv = WzTool.GetIvByMapleVersion(version);
var rows = new List<ConversionRecord>();

foreach (string sourcePath in Directory.EnumerateFiles(sourceRoot, "*.img.xml")
             .Where(path => selectedFiles.Contains(Path.GetFileName(path)))
             .OrderBy(Path.GetFileName))
{
    string outputName = Path.GetFileNameWithoutExtension(sourcePath);
    string outputPath = Path.Combine(outputRoot, outputName);
    try
    {
        var deserializer = new WzXmlDeserializer(useMemorySaving: false, iv);
        List<WzObject> objects = deserializer.ParseXML(sourcePath);
        WzImage image = objects.OfType<WzImage>().Single();
        try
        {
            new WzImgSerializer(iv).SerializeImage(image, outputPath);
        }
        finally
        {
            image.Dispose();
        }

        rows.Add(new ConversionRecord(
            Path.GetFileName(sourcePath),
            outputName,
            new FileInfo(sourcePath).Length,
            new FileInfo(outputPath).Length,
            null));
    }
    catch (Exception exception)
    {
        rows.Add(new ConversionRecord(
            Path.GetFileName(sourcePath),
            outputName,
            new FileInfo(sourcePath).Length,
            0,
            exception.GetType().Name + ": " + exception.Message));
    }
}

var options = new JsonSerializerOptions { WriteIndented = true, PropertyNamingPolicy = JsonNamingPolicy.CamelCase };
string manifestPath = Path.Combine(Directory.GetParent(outputRoot)?.FullName ?? outputRoot, "string-img-manifest.json");
await File.WriteAllTextAsync(manifestPath, JsonSerializer.Serialize(rows, options));

int errors = rows.Count(row => row.Error is not null);
Console.Error.WriteLine($"Converted {rows.Count - errors:N0}/{rows.Count:N0} String IMG files to {outputRoot}; errors={errors:N0}");
return errors == 0 ? 0 : 1;

internal sealed record ConversionRecord(
    string SourceFile,
    string OutputFile,
    long SourceBytes,
    long OutputBytes,
    string? Error);
