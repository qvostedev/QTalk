#ifndef SourceDir
  #error SourceDir must point to the Compose application image
#endif

#ifndef OutputDir
  #define OutputDir "."
#endif

#ifndef AppVersion
  #define AppVersion "1.0.1"
#endif

#define AppName "QTalk"
#define AppPublisher "Qvoste"
#define AppExecutable "QTalk.exe"

[Setup]
AppId={{A915F19B-B466-4DB7-BC2F-5CC05EC43E75}
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName={#AppName} {#AppVersion}
AppPublisher={#AppPublisher}
DefaultDirName={autopf}\{#AppName}
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
OutputDir={#OutputDir}
OutputBaseFilename=QTalk-Setup-{#AppVersion}
SetupIconFile=..\..\desktopApp\src\main\resources\logo_black.ico
UninstallDisplayIcon={app}\{#AppExecutable}
UninstallDisplayName={#AppName}
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequired=admin
WizardStyle=modern dark includetitlebar hidebevels
WizardSizePercent=110
WizardBackColor=#0B0D10
WizardImageFile=wizard-large.bmp
WizardImageBackColor=#0B0D10
WizardSmallImageFile=wizard-small.bmp
WizardSmallImageBackColor=#0B0D10
Compression=lzma2/ultra64
SolidCompression=yes
CloseApplications=yes
CloseApplicationsFilter=QTalk.exe
RestartApplications=no
ChangesAssociations=yes
VersionInfoVersion={#AppVersion}.0
VersionInfoCompany={#AppPublisher}
VersionInfoDescription={#AppName} desktop installer
VersionInfoProductName={#AppName}
VersionInfoProductVersion={#AppVersion}

[Languages]
Name: "russian"; MessagesFile: "compiler:Languages\Russian.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Создать ярлык на рабочем столе"; GroupDescription: "Дополнительные ярлыки:"; Flags: unchecked

[InstallDelete]
; jpackage добавляет хэш к именам JAR. Без очистки обновление оставляет старые
; версии рядом с новой, и Java может загрузить устаревший класс из app\*.
Type: filesandordirs; Name: "{app}\*"

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\{#AppName}"; Filename: "{app}\{#AppExecutable}"; WorkingDir: "{app}"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\{#AppExecutable}"; WorkingDir: "{app}"; Tasks: desktopicon

[Registry]
Root: HKCR; Subkey: "qtalk"; ValueType: string; ValueData: "URL:QTalk Protocol"; Flags: uninsdeletekey
Root: HKCR; Subkey: "qtalk"; ValueType: string; ValueName: "URL Protocol"; ValueData: ""
Root: HKCR; Subkey: "qtalk\shell\open\command"; ValueType: string; ValueData: """{app}\{#AppExecutable}"" ""%1"""

[Run]
Filename: "{app}\{#AppExecutable}"; Description: "Запустить {#AppName}"; Flags: nowait postinstall skipifsilent
