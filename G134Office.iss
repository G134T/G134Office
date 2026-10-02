#define MyAppName "G134Office"
#define MyAppVersion "1.0.1"
#define MyAppPublisher "Анатолий Новиков"
#define MyAppExeName "G134Office.exe"
#define ProjectDir SourcePath
#define SourceDir ProjectDir + "\build\package\G134Office"
#define IconFile ProjectDir + "\g134.ico"
#define LiteExe ProjectDir + "\cpp\build\Release\G134OfficeLite.exe"

[Setup]
AppId={{A3C8E1B0-7D14-4F2A-9C61-G134OFFICE0001}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
OutputDir={#ProjectDir}\build\installer
OutputBaseFilename=G134Office-Setup-{#MyAppVersion}
Compression=lzma2
SolidCompression=yes
PrivilegesRequired=admin
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
SetupIconFile={#IconFile}
UninstallDisplayIcon={app}\g134.ico
VersionInfoDescription=G134Office — редактор документов и PDF
VersionInfoProductName=G134Office
VersionInfoProductVersion={#MyAppVersion}
VersionInfoCompany={#MyAppPublisher}
VersionInfoCopyright=Copyright © 2026 {#MyAppPublisher}
CloseApplications=yes
ChangesAssociations=yes
RestartApplications=no
WizardStyle=modern dynamic
ShowLanguageDialog=yes
DisableProgramGroupPage=yes

[Languages]
Name: "russian"; MessagesFile: "compiler:Languages\Russian.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[CustomMessages]
russian.ExtraTasksGroup=Дополнительно:
russian.DesktopIconTask=Ярлык на рабочем столе
russian.FormatPageTitle=Форматы файлов
russian.FormatPageDescription=Выберите форматы, которые установщик зарегистрирует для G134Office. Windows может отдельно предложить выбрать приложение по умолчанию в параметрах.
russian.SelectAllFormats=Выбрать все поддерживаемые форматы
russian.FormatWordOpenXml=Word: .docx, .docm, .dotx
russian.FormatWordLegacy=Word 97–2003: .doc, .dot
russian.FormatOpenDocument=OpenDocument: .odt, .ott
russian.FormatRichText=Форматированный текст: .rtf
russian.FormatWeb=Веб-страницы: .html, .htm
russian.FormatPdf=PDF: .pdf
russian.FormatText=Текст: .txt, .md, .csv, .xml, .json
russian.FormatBooks=Книги: .fb2, .epub
english.ExtraTasksGroup=Additional options:
english.DesktopIconTask=Create a desktop shortcut
english.FormatPageTitle=File formats
english.FormatPageDescription=Choose the formats to register for G134Office. Windows may ask you to choose a default app separately in Settings.
english.SelectAllFormats=Select all supported formats
english.FormatWordOpenXml=Word: .docx, .docm, .dotx
english.FormatWordLegacy=Word 97–2003: .doc, .dot
english.FormatOpenDocument=OpenDocument: .odt, .ott
english.FormatRichText=Rich text: .rtf
english.FormatWeb=Web pages: .html, .htm
english.FormatPdf=PDF: .pdf
english.FormatText=Text: .txt, .md, .csv, .xml, .json
english.FormatBooks=Books: .fb2, .epub
russian.LicensePageTitle=Лицензия G134Office
russian.LicensePageDescription=Прокрутите текст лицензии и примите условия, чтобы продолжить установку.
russian.LicenseAccept=Я ознакомился и принимаю MIT License для G134Office
russian.LicenseIntro=Ниже приведён полный текст MIT License, по которой распространяется исходный код G134Office. У сторонних библиотек собственные лицензии; происхождение проекта описано в NOTICE.
russian.ThemeLight=Светлая
russian.ThemeDark=Тёмная
english.LicensePageTitle=G134Office license
english.LicensePageDescription=Scroll through the license text and accept it to continue.
english.LicenseAccept=I have read and accept the MIT License for G134Office
english.LicenseIntro=The complete MIT License text follows. It applies to G134Office original source code. Third-party libraries retain their own licenses; the project origin is described in NOTICE.
english.ThemeLight=Light
english.ThemeDark=Dark
russian.TypeFull=Полная версия
russian.TypeLite=Облегчённая версия
russian.ComponentFull=G134Office — документы, PDF и страницы
russian.ComponentLite=G134Office Lite — обычный текст, один exe без Java
russian.LaunchFull=Запустить G134Office
russian.LaunchLite=Запустить G134Office Lite
english.TypeFull=Full version
english.TypeLite=Lite version
english.ComponentFull=G134Office — documents, PDF, and pages
english.ComponentLite=G134Office Lite — plain text, one exe, no Java
english.LaunchFull=Launch G134Office
english.LaunchLite=Launch G134Office Lite

[Types]
Name: "full"; Description: "{cm:TypeFull}"
Name: "lite"; Description: "{cm:TypeLite}"

[Components]
Name: "full"; Description: "{cm:ComponentFull}"; Types: full; Flags: exclusive
Name: "lite"; Description: "{cm:ComponentLite}"; Types: lite; Flags: exclusive

[Tasks]
Name: "desktopicon"; Description: "{cm:DesktopIconTask}"; GroupDescription: "{cm:ExtraTasksGroup}"

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Components: full; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "{#LiteExe}"; DestDir: "{app}"; DestName: "G134OfficeLite.exe"; Components: lite; Flags: ignoreversion
Source: "{#IconFile}"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#ProjectDir}\LICENSE"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#ProjectDir}\NOTICE"; DestDir: "{app}"; Flags: ignoreversion
Source: "{#ProjectDir}\LICENSE"; DestDir: "{tmp}"; Flags: dontcopy

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"; IconFilename: "{app}\{#MyAppExeName}"; Components: full
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; WorkingDir: "{app}"; IconFilename: "{app}\{#MyAppExeName}"; Components: full; Tasks: desktopicon
Name: "{autoprograms}\G134Office Lite"; Filename: "{app}\G134OfficeLite.exe"; WorkingDir: "{app}"; IconFilename: "{app}\G134OfficeLite.exe"; Components: lite
Name: "{autodesktop}\G134Office Lite"; Filename: "{app}\G134OfficeLite.exe"; WorkingDir: "{app}"; IconFilename: "{app}\G134OfficeLite.exe"; Components: lite; Tasks: desktopicon

[Registry]
Root: HKA; Components: full; Subkey: "Software\Classes\.docx"; ValueType: string; ValueName: ""; ValueData: "G134Office.Document"; Check: IsFormatSelected('.docx'); Flags: uninsdeletevalue
Root: HKA; Components: full; Subkey: "Software\Classes\.docm"; ValueType: string; ValueName: ""; ValueData: "G134Office.Document"; Check: IsFormatSelected('.docm'); Flags: uninsdeletevalue
Root: HKA; Components: full; Subkey: "Software\Classes\.dotx"; ValueType: string; ValueName: ""; ValueData: "G134Office.Document"; Check: IsFormatSelected('.dotx'); Flags: uninsdeletevalue
Root: HKA; Components: full; Subkey: "Software\Classes\.doc"; ValueType: string; ValueName: ""; ValueData: "G134Office.Document"; Check: IsFormatSelected('.doc'); Flags: uninsdeletevalue
Root: HKA; Components: full; Subkey: "Software\Classes\.dot"; ValueType: string; ValueName: ""; ValueData: "G134Office.Document"; Check: IsFormatSelected('.dot'); Flags: uninsdeletevalue
Root: HKA; Components: full; Subkey: "Software\Classes\.odt"; ValueType: string; ValueName: ""; ValueData: "G134Office.Document"; Check: IsFormatSelected('.odt'); Flags: uninsdeletevalue
Root: HKA; Components: full; Subkey: "Software\Classes\.ott"; ValueType: string; ValueName: ""; ValueData: "G134Office.Document"; Check: IsFormatSelected('.ott'); Flags: uninsdeletevalue
Root: HKA; Components: full; Subkey: "Software\Classes\.rtf"; ValueType: string; ValueName: ""; ValueData: "G134Office.Document"; Check: IsFormatSelected('.rtf'); Flags: uninsdeletevalue
Root: HKA; Components: full; Subkey: "Software\Classes\.html"; ValueType: string; ValueName: ""; ValueData: "G134Office.Document"; Check: IsFormatSelected('.html'); Flags: uninsdeletevalue
Root: HKA; Components: full; Subkey: "Software\Classes\.htm"; ValueType: string; ValueName: ""; ValueData: "G134Office.Document"; Check: IsFormatSelected('.htm'); Flags: uninsdeletevalue
Root: HKA; Components: full; Subkey: "Software\Classes\.pdf"; ValueType: string; ValueName: ""; ValueData: "G134Office.Document"; Check: IsFormatSelected('.pdf'); Flags: uninsdeletevalue
Root: HKA; Components: full; Subkey: "Software\Classes\.txt"; ValueType: string; ValueName: ""; ValueData: "G134Office.Document"; Check: IsFormatSelected('.txt'); Flags: uninsdeletevalue
Root: HKA; Components: full; Subkey: "Software\Classes\.md"; ValueType: string; ValueName: ""; ValueData: "G134Office.Document"; Check: IsFormatSelected('.md'); Flags: uninsdeletevalue
Root: HKA; Components: full; Subkey: "Software\Classes\.csv"; ValueType: string; ValueName: ""; ValueData: "G134Office.Document"; Check: IsFormatSelected('.csv'); Flags: uninsdeletevalue
Root: HKA; Components: full; Subkey: "Software\Classes\.xml"; ValueType: string; ValueName: ""; ValueData: "G134Office.Document"; Check: IsFormatSelected('.xml'); Flags: uninsdeletevalue
Root: HKA; Components: full; Subkey: "Software\Classes\.json"; ValueType: string; ValueName: ""; ValueData: "G134Office.Document"; Check: IsFormatSelected('.json'); Flags: uninsdeletevalue
Root: HKA; Components: full; Subkey: "Software\Classes\.fb2"; ValueType: string; ValueName: ""; ValueData: "G134Office.Document"; Check: IsFormatSelected('.fb2'); Flags: uninsdeletevalue
Root: HKA; Components: full; Subkey: "Software\Classes\.epub"; ValueType: string; ValueName: ""; ValueData: "G134Office.Document"; Check: IsFormatSelected('.epub'); Flags: uninsdeletevalue
Root: HKA; Components: full; Subkey: "Software\Classes\.docx\OpenWithProgids"; ValueType: string; ValueName: "G134Office.Document"; ValueData: ""; Check: IsFormatSelected('.docx'); Flags: uninsdeletevalue
Root: HKA; Components: full; Subkey: "Software\Classes\.docm\OpenWithProgids"; ValueType: string; ValueName: "G134Office.Document"; ValueData: ""; Check: IsFormatSelected('.docm'); Flags: uninsdeletevalue
Root: HKA; Components: full; Subkey: "Software\Classes\.dotx\OpenWithProgids"; ValueType: string; ValueName: "G134Office.Document"; ValueData: ""; Check: IsFormatSelected('.dotx'); Flags: uninsdeletevalue
Root: HKA; Components: full; Subkey: "Software\Classes\.doc\OpenWithProgids"; ValueType: string; ValueName: "G134Office.Document"; ValueData: ""; Check: IsFormatSelected('.doc'); Flags: uninsdeletevalue
Root: HKA; Components: full; Subkey: "Software\Classes\.dot\OpenWithProgids"; ValueType: string; ValueName: "G134Office.Document"; ValueData: ""; Check: IsFormatSelected('.dot'); Flags: uninsdeletevalue
Root: HKA; Components: full; Subkey: "Software\Classes\.odt\OpenWithProgids"; ValueType: string; ValueName: "G134Office.Document"; ValueData: ""; Check: IsFormatSelected('.odt'); Flags: uninsdeletevalue
Root: HKA; Components: full; Subkey: "Software\Classes\.ott\OpenWithProgids"; ValueType: string; ValueName: "G134Office.Document"; ValueData: ""; Check: IsFormatSelected('.ott'); Flags: uninsdeletevalue
Root: HKA; Components: full; Subkey: "Software\Classes\.rtf\OpenWithProgids"; ValueType: string; ValueName: "G134Office.Document"; ValueData: ""; Check: IsFormatSelected('.rtf'); Flags: uninsdeletevalue
Root: HKA; Components: full; Subkey: "Software\Classes\.html\OpenWithProgids"; ValueType: string; ValueName: "G134Office.Document"; ValueData: ""; Check: IsFormatSelected('.html'); Flags: uninsdeletevalue
Root: HKA; Components: full; Subkey: "Software\Classes\.htm\OpenWithProgids"; ValueType: string; ValueName: "G134Office.Document"; ValueData: ""; Check: IsFormatSelected('.htm'); Flags: uninsdeletevalue
Root: HKA; Components: full; Subkey: "Software\Classes\.pdf\OpenWithProgids"; ValueType: string; ValueName: "G134Office.Document"; ValueData: ""; Check: IsFormatSelected('.pdf'); Flags: uninsdeletevalue
Root: HKA; Components: full; Subkey: "Software\Classes\.txt\OpenWithProgids"; ValueType: string; ValueName: "G134Office.Document"; ValueData: ""; Check: IsFormatSelected('.txt'); Flags: uninsdeletevalue
Root: HKA; Components: full; Subkey: "Software\Classes\.md\OpenWithProgids"; ValueType: string; ValueName: "G134Office.Document"; ValueData: ""; Check: IsFormatSelected('.md'); Flags: uninsdeletevalue
Root: HKA; Components: full; Subkey: "Software\Classes\.csv\OpenWithProgids"; ValueType: string; ValueName: "G134Office.Document"; ValueData: ""; Check: IsFormatSelected('.csv'); Flags: uninsdeletevalue
Root: HKA; Components: full; Subkey: "Software\Classes\.xml\OpenWithProgids"; ValueType: string; ValueName: "G134Office.Document"; ValueData: ""; Check: IsFormatSelected('.xml'); Flags: uninsdeletevalue
Root: HKA; Components: full; Subkey: "Software\Classes\.json\OpenWithProgids"; ValueType: string; ValueName: "G134Office.Document"; ValueData: ""; Check: IsFormatSelected('.json'); Flags: uninsdeletevalue
Root: HKA; Components: full; Subkey: "Software\Classes\.fb2\OpenWithProgids"; ValueType: string; ValueName: "G134Office.Document"; ValueData: ""; Check: IsFormatSelected('.fb2'); Flags: uninsdeletevalue
Root: HKA; Components: full; Subkey: "Software\Classes\.epub\OpenWithProgids"; ValueType: string; ValueName: "G134Office.Document"; ValueData: ""; Check: IsFormatSelected('.epub'); Flags: uninsdeletevalue
Root: HKA; Components: full; Subkey: "Software\Classes\G134Office.Document"; ValueType: string; ValueName: ""; ValueData: "G134Office Document"; Flags: uninsdeletekey
Root: HKA; Components: full; Subkey: "Software\Classes\G134Office.Document\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#MyAppExeName},0"
Root: HKA; Components: full; Subkey: "Software\Classes\G134Office.Document\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchFull}"; WorkingDir: "{app}"; Components: full; Flags: nowait postinstall skipifsilent
Filename: "{app}\G134OfficeLite.exe"; Description: "{cm:LaunchLite}"; WorkingDir: "{app}"; Components: lite; Flags: nowait postinstall skipifsilent

[Code]
var
  LicensePage: TWizardPage;
  LicenseMemo: TNewMemo;
  LicenseAccepted: TNewCheckBox;
  FormatPage: TWizardPage;
  SelectAllFormats: TNewCheckBox;
  WordOpenXmlFormats: TNewCheckBox;
  WordLegacyFormats: TNewCheckBox;
  OpenDocumentFormats: TNewCheckBox;
  RichTextFormat: TNewCheckBox;
  WebFormats: TNewCheckBox;
  PdfFormat: TNewCheckBox;
  TextFormats: TNewCheckBox;
  BookFormats: TNewCheckBox;
  LightThemeButton: TNewButton;
  DarkThemeButton: TNewButton;
  InstallerUsesDarkTheme: Boolean;

procedure FormatSelectionChanged(Sender: TObject); forward;
procedure ApplyInstallerTheme(const UseDark: Boolean); forward;
procedure LicenseAcceptedClick(Sender: TObject); forward;
function LicensePageNext(Sender: TWizardPage): Boolean; forward;

procedure ApplyControlTheme(Control: TControl; const UseDark: Boolean);
var
  Index: Integer;
  BackgroundColor: TColor;
  ForegroundColor: TColor;
  EditColor: TColor;
begin
  if UseDark then
  begin
    BackgroundColor := StrToColor('#20242B');
    ForegroundColor := StrToColor('#F0F2F5');
    EditColor := StrToColor('#171A20');
  end
  else
  begin
    BackgroundColor := StrToColor('#F4F6F8');
    ForegroundColor := StrToColor('#20242B');
    EditColor := StrToColor('#FFFFFF');
  end;

  Control.StyleElements := [];
  if Control is TNewNotebookPage then
    TNewNotebookPage(Control).Color := BackgroundColor
  else if Control is TPanel then
    TPanel(Control).Color := BackgroundColor;

  if Control is TNewStaticText then
  begin
    TNewStaticText(Control).Color := BackgroundColor;
    TNewStaticText(Control).Font.Color := ForegroundColor;
  end
  else if Control is TNewButton then
    TNewButton(Control).Font.Color := ForegroundColor
  else if Control is TNewCheckBox then
  begin
    TNewCheckBox(Control).Color := BackgroundColor;
    TNewCheckBox(Control).Font.Color := ForegroundColor;
  end
  else if Control is TNewRadioButton then
  begin
    TNewRadioButton(Control).Color := BackgroundColor;
    TNewRadioButton(Control).Font.Color := ForegroundColor;
  end
  else if Control is TNewMemo then
  begin
    TNewMemo(Control).Color := EditColor;
    TNewMemo(Control).Font.Color := ForegroundColor;
  end
  else if Control is TNewComboBox then
  begin
    TNewComboBox(Control).Color := EditColor;
    TNewComboBox(Control).Font.Color := ForegroundColor;
  end
  else if Control is TNewListBox then
  begin
    TNewListBox(Control).Color := EditColor;
    TNewListBox(Control).Font.Color := ForegroundColor;
  end
  else if Control is TNewEdit then
  begin
    TNewEdit(Control).Color := EditColor;
    TNewEdit(Control).Font.Color := ForegroundColor;
  end
  else if Control is TPasswordEdit then
  begin
    TPasswordEdit(Control).Color := EditColor;
    TPasswordEdit(Control).Font.Color := ForegroundColor;
  end
  else if Control is TNewLinkLabel then
    TNewLinkLabel(Control).Font.Color := ForegroundColor;

  if Control is TWinControl then
    for Index := 0 to TWinControl(Control).ControlCount - 1 do
      ApplyControlTheme(TWinControl(Control).Controls[Index], UseDark);
end;

procedure ApplyInstallerTheme(const UseDark: Boolean);
begin
  InstallerUsesDarkTheme := UseDark;
  if UseDark then
    WizardForm.Color := StrToColor('#20242B')
  else
    WizardForm.Color := StrToColor('#F4F6F8');
  ApplyControlTheme(WizardForm, UseDark);
  LightThemeButton.Font.Style := [fsBold];
  DarkThemeButton.Font.Style := [fsBold];
  LightThemeButton.Enabled := UseDark;
  DarkThemeButton.Enabled := not UseDark;
  WizardForm.Refresh;
end;

procedure LightThemeButtonClick(Sender: TObject);
begin
  ApplyInstallerTheme(False);
end;

procedure DarkThemeButtonClick(Sender: TObject);
begin
  ApplyInstallerTheme(True);
end;

procedure LicenseAcceptedClick(Sender: TObject);
begin
  WizardForm.NextButton.Enabled := LicenseAccepted.Checked;
end;

procedure LicensePageActivate(Sender: TWizardPage);
begin
  WizardForm.NextButton.Enabled := LicenseAccepted.Checked;
end;

function LicensePageNext(Sender: TWizardPage): Boolean;
begin
  Result := LicenseAccepted.Checked;
end;

function AddFormatCheck(const Caption: String; Top: Integer): TNewCheckBox;
begin
  Result := TNewCheckBox.Create(WizardForm);
  Result.Parent := FormatPage.Surface;
  Result.Left := ScaleX(12);
  Result.Top := ScaleY(Top);
  Result.Width := ScaleX(440);
  Result.Height := ScaleY(22);
  Result.Caption := Caption;
  Result.Checked := True;
  Result.OnClick := @FormatSelectionChanged;
end;

procedure UpdateSelectAllState;
var
  CheckedCount: Integer;
begin
  CheckedCount := 0;
  if WordOpenXmlFormats.Checked then CheckedCount := CheckedCount + 1;
  if WordLegacyFormats.Checked then CheckedCount := CheckedCount + 1;
  if OpenDocumentFormats.Checked then CheckedCount := CheckedCount + 1;
  if RichTextFormat.Checked then CheckedCount := CheckedCount + 1;
  if WebFormats.Checked then CheckedCount := CheckedCount + 1;
  if PdfFormat.Checked then CheckedCount := CheckedCount + 1;
  if TextFormats.Checked then CheckedCount := CheckedCount + 1;
  if BookFormats.Checked then CheckedCount := CheckedCount + 1;

  if CheckedCount = 8 then
    SelectAllFormats.State := cbChecked
  else if CheckedCount = 0 then
    SelectAllFormats.State := cbUnchecked
  else
    SelectAllFormats.State := cbGrayed;
end;

procedure FormatSelectionChanged(Sender: TObject);
begin
  if Sender = SelectAllFormats then
  begin
    WordOpenXmlFormats.Checked := SelectAllFormats.Checked;
    WordLegacyFormats.Checked := SelectAllFormats.Checked;
    OpenDocumentFormats.Checked := SelectAllFormats.Checked;
    RichTextFormat.Checked := SelectAllFormats.Checked;
    WebFormats.Checked := SelectAllFormats.Checked;
    PdfFormat.Checked := SelectAllFormats.Checked;
    TextFormats.Checked := SelectAllFormats.Checked;
    BookFormats.Checked := SelectAllFormats.Checked;
  end;
  UpdateSelectAllState;
end;

procedure InitializeWizard;
var
  LicenseText: AnsiString;
begin
  ExtractTemporaryFile('LICENSE');
  if not LoadStringFromFile(ExpandConstant('{tmp}\LICENSE'), LicenseText) then
    RaiseException('Could not load the G134Office license text.');

  LicensePage := CreateCustomPage(wpWelcome,
    CustomMessage('LicensePageTitle'), CustomMessage('LicensePageDescription'));
  LicenseMemo := TNewMemo.Create(LicensePage);
  LicenseMemo.Parent := LicensePage.Surface;
  LicenseMemo.Left := 0;
  LicenseMemo.Top := 0;
  LicenseMemo.Width := LicensePage.SurfaceWidth;
  LicenseMemo.Height := LicensePage.SurfaceHeight - ScaleY(42);
  LicenseMemo.Anchors := [akLeft, akTop, akRight, akBottom];
  LicenseMemo.ScrollBars := ssVertical;
  LicenseMemo.WordWrap := True;
  LicenseMemo.ReadOnly := True;
  LicenseMemo.Text := CustomMessage('LicenseIntro') + #13#10#13#10 + LicenseText;

  LicenseAccepted := TNewCheckBox.Create(LicensePage);
  LicenseAccepted.Parent := LicensePage.Surface;
  LicenseAccepted.Left := 0;
  LicenseAccepted.Top := LicenseMemo.Top + LicenseMemo.Height + ScaleY(8);
  LicenseAccepted.Width := LicensePage.SurfaceWidth;
  LicenseAccepted.Height := ScaleY(28);
  LicenseAccepted.Anchors := [akLeft, akRight, akBottom];
  LicenseAccepted.Caption := CustomMessage('LicenseAccept');
  LicenseAccepted.Checked := False;
  LicenseAccepted.OnClick := @LicenseAcceptedClick;
  LicensePage.OnActivate := @LicensePageActivate;
  LicensePage.OnNextButtonClick := @LicensePageNext;

  FormatPage := CreateCustomPage(wpSelectTasks, CustomMessage('FormatPageTitle'),
    CustomMessage('FormatPageDescription'));

  SelectAllFormats := TNewCheckBox.Create(WizardForm);
  SelectAllFormats.Parent := FormatPage.Surface;
  SelectAllFormats.Left := ScaleX(12);
  SelectAllFormats.Top := ScaleY(8);
  SelectAllFormats.Width := ScaleX(440);
  SelectAllFormats.Height := ScaleY(24);
  SelectAllFormats.Caption := CustomMessage('SelectAllFormats');
  SelectAllFormats.Checked := True;
  SelectAllFormats.AllowGrayed := True;
  SelectAllFormats.OnClick := @FormatSelectionChanged;

  WordOpenXmlFormats := AddFormatCheck(CustomMessage('FormatWordOpenXml'), 42);
  WordLegacyFormats := AddFormatCheck(CustomMessage('FormatWordLegacy'), 68);
  OpenDocumentFormats := AddFormatCheck(CustomMessage('FormatOpenDocument'), 94);
  RichTextFormat := AddFormatCheck(CustomMessage('FormatRichText'), 120);
  WebFormats := AddFormatCheck(CustomMessage('FormatWeb'), 146);
  PdfFormat := AddFormatCheck(CustomMessage('FormatPdf'), 172);
  TextFormats := AddFormatCheck(CustomMessage('FormatText'), 198);
  BookFormats := AddFormatCheck(CustomMessage('FormatBooks'), 224);
  UpdateSelectAllState;

  LightThemeButton := TNewButton.Create(WizardForm);
  LightThemeButton.Parent := WizardForm;
  LightThemeButton.Caption := CustomMessage('ThemeLight');
  LightThemeButton.Width := ScaleX(76);
  LightThemeButton.Height := ScaleY(24);
  LightThemeButton.Top := WizardForm.WizardSmallBitmapImage.Top;
  LightThemeButton.Left := WizardForm.WizardSmallBitmapImage.Left - ScaleX(160);
  LightThemeButton.OnClick := @LightThemeButtonClick;

  DarkThemeButton := TNewButton.Create(WizardForm);
  DarkThemeButton.Parent := WizardForm;
  DarkThemeButton.Caption := CustomMessage('ThemeDark');
  DarkThemeButton.Width := ScaleX(76);
  DarkThemeButton.Height := ScaleY(24);
  DarkThemeButton.Top := WizardForm.WizardSmallBitmapImage.Top;
  DarkThemeButton.Left := WizardForm.WizardSmallBitmapImage.Left - ScaleX(80);
  DarkThemeButton.OnClick := @DarkThemeButtonClick;

  WizardForm.PageNameLabel.Width := LightThemeButton.Left - WizardForm.PageNameLabel.Left - ScaleX(8);
  WizardForm.PageDescriptionLabel.Width := LightThemeButton.Left - WizardForm.PageDescriptionLabel.Left - ScaleX(8);
  ApplyInstallerTheme(IsDarkInstallMode);
end;

function ShouldSkipPage(PageID: Integer): Boolean;
begin
  Result := False;
  if (FormatPage <> nil) and (PageID = FormatPage.ID) and (not WizardIsComponentSelected('full')) then
    Result := True;
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep <> ssPostInstall then
    Exit;
  if WizardIsComponentSelected('lite') then
    RegWriteStringValue(HKLM,
      'Software\Microsoft\Windows\CurrentVersion\Uninstall\{A3C8E1B0-7D14-4F2A-9C61-G134OFFICE0001}_is1',
      'DisplayName', 'G134Office Lite');
end;

function IsFormatSelected(const Extension: String): Boolean;
begin
  if not WizardIsComponentSelected('full') then
  begin
    Result := False;
    Exit;
  end;
  if (Extension = '.docx') or (Extension = '.docm') or (Extension = '.dotx') then
    Result := WordOpenXmlFormats.Checked
  else if (Extension = '.doc') or (Extension = '.dot') then
    Result := WordLegacyFormats.Checked
  else if (Extension = '.odt') or (Extension = '.ott') then
    Result := OpenDocumentFormats.Checked
  else if Extension = '.rtf' then
    Result := RichTextFormat.Checked
  else if (Extension = '.html') or (Extension = '.htm') then
    Result := WebFormats.Checked
  else if Extension = '.pdf' then
    Result := PdfFormat.Checked
  else if (Extension = '.txt') or (Extension = '.md') or (Extension = '.csv') or
          (Extension = '.xml') or (Extension = '.json') then
    Result := TextFormats.Checked
  else if (Extension = '.fb2') or (Extension = '.epub') then
    Result := BookFormats.Checked
  else
    Result := False;
end;
