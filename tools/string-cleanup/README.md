# Stock Cosmic String cleanup

This workflow resets `wz/String.wz` from an untouched stock Cosmic XML dump and
then applies presentation-only cleanup. MapleRoot is a wording and formatting
reference; it is never used as the authoritative item set or gameplay-value
source.

The cleanup covers:

- scroll names/descriptions, with success rates read from `Item.wz`;
- `ATT` terminology only within scroll entries (`204*`); other strings use `Attack`;
- mastery-book names/descriptions, with levels and requirements read from `Item.wz`;
- canonical equipment terminology and safe typo/spacing fixes;
- Explorer skill presentation text;
- Cash, Etc, Install/chair, equipment, and consumable name/description formatting;
- malformed newline escape casing, trailing color tokens, and stray String.wz null nodes.

NPC dialogue, map lore, pet dialogue, and EULA prose are not rewritten.
Unresolved stock placeholders are reported rather than guessed.

```powershell
powershell -ExecutionPolicy Bypass -File tools/string-cleanup/Update-StockCosmicStrings.ps1 `
  -StockStringRoot tmp/cosmic-clean-string-audit-20260825/Cosmic-master/wz/String.wz `
  -MapleRootStringRoot "C:/Users/user/Downloads/MapleRoot Full Repack/Server/wz/String.wz"

powershell -ExecutionPolicy Bypass -File tools/string-cleanup/Test-StockCosmicStrings.ps1 `
  -StockStringRoot tmp/cosmic-clean-string-audit-20260825/Cosmic-master/wz/String.wz
```

For an unpacked v83 `Data` client, build the six cleaned standalone String IMG
files (`Cash`, `Consume`, `Eqp`, `Etc`, `Ins`, and `Skill`):

```powershell
dotnet run --project tools/string-cleanup/ServerXmlToImg/ServerXmlToImg.csproj -- `
  wz/String.wz tmp/string-cleanup/client/Data/String GMS
```

The converter defaults to only those six files so untouched dialogue and lore
IMGs remain byte-for-byte unchanged in the client. An optional fourth argument
can provide a comma-separated file-stem allowlist. The converter refuses to
overwrite a non-empty output directory. Validate the client output by converting
it back with `tools/client-repack/ImgToServerXml` and comparing every node, type,
field, and value:

```powershell
powershell -ExecutionPolicy Bypass -File tools/string-cleanup/Test-StringImgRoundTrip.ps1 `
  -RoundTripStringRoot tmp/string-cleanup/roundtrip-wz/String.wz
```
