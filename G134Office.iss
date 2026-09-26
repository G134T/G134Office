#define MyAppName "G134Office"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "Анатолий Новиков"
#define MyAppExeName "G134Office.exe"
#define ProjectDir SourcePath
#define SourceDir ProjectDir + "\build\package\G134Office"
#define IconFile ProjectDir + "\g134.ico"

[Setup]
AppId={{A3C8E1B0-7D14-4F2A-9C61-G134OFFICE0001}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
OutputDir={#ProjectDir}\build\installer
OutputBaseFilename=G134Office-Setup-1.0.0
Compression=lzma2
SolidCompression=yes
PrivilegesRequired=admin
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
SetupIconFile={#IconFile}
UninstallDisplayIcon={app}\{#MyAppExeName}
VersionInfoDescription=G134Office — редактор документов и PDF
VersionInfoProductName=G134Office
VersionInfoProductVersion={#MyAppVersion}
VersionInfoCompany={#MyAppPublisher}
VersionInfoCopyright=Copyright © 2026 {#MyAppPublisher}
CloseApplications=yes
ChangesAssociations=yes
RestartApplications=no
WizardStyle=modern
DisableProgramGroupPage=yes

[Languages]
Name: "russian"; MessagesFile: "compiler:Languages\Russian.isl"

[Tasks]
Name: "fileassoc"; Description: "Открывать .docx и .odt в G134Office двойным щелчком"; GroupDescription: "Дополнительно:"
Name: "desktopicon"; Description: "Ярлык на рабочем столе"; GroupDescription: "Дополнительно:"

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"; IconFilename: "{app}\{#MyAppExeName}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"; IconFilename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Registry]
Root: HKA; Subkey: "Software\Classes\.docx"; ValueType: string; ValueName: ""; ValueData: "G134Office.Document"; Tasks: fileassoc; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Classes\.odt"; ValueType: string; ValueName: ""; ValueData: "G134Office.Document"; Tasks: fileassoc; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Classes\.docx\OpenWithProgids"; ValueType: string; ValueName: "G134Office.Document"; ValueData: ""; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Classes\.odt\OpenWithProgids"; ValueType: string; ValueName: "G134Office.Document"; ValueData: ""; Flags: uninsdeletevalue
Root: HKA; Subkey: "Software\Classes\G134Office.Document"; ValueType: string; ValueName: ""; ValueData: "G134Office Document"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\G134Office.Document\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppExeName},0"
Root: HKA; Subkey: "Software\Classes\G134Office.Document\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "Запустить G134Office"; WorkingDir: "{app}"; Flags: nowait postinstall skipifsilent
