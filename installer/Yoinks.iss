; Yoinks installer - build with build.ps1 (or: ISCC.exe installer\Yoinks.iss)
; Expects the published app (with tools\yt-dlp.exe, tools\ffmpeg.exe ...) in ..\dist\app

#define AppName "Yoinks"
#ifndef AppVersion
  #define AppVersion "1.0.0"
#endif
#define AppPublisher "Tamim Ahmed Rijan"
#define AppURL "https://github.com/tamimrijan/yoinks-windows"
#define AppExe "Yoinks.exe"
#define DenoUrl "https://github.com/denoland/deno/releases/latest/download/deno-x86_64-pc-windows-msvc.zip"

[Setup]
; never change AppId: upgrades and the uninstaller find the app by it
AppId={{6C1D7E3A-5B2F-4E8D-9A41-2F7C0B9E5D13}
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName={#AppName} {#AppVersion}
AppPublisher={#AppPublisher}
AppPublisherURL={#AppURL}
AppSupportURL={#AppURL}/issues
AppUpdatesURL={#AppURL}/releases
AppCopyright=Copyright (c) 2026 {#AppPublisher}
AppComments=Paste a link, yoink the video. YouTube, X, Instagram, Threads, TikTok and 1800+ more sites.
VersionInfoVersion={#AppVersion}
VersionInfoDescription={#AppName} Setup

; install just for me (no admin) by default, or for everyone
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
DefaultDirName={autopf}\{#AppName}
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
DisableDirPage=no
UsePreviousAppDir=yes
UsePreviousTasks=yes
LicenseFile=..\LICENSE

ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0.17763

WizardStyle=modern dynamic
WizardSizePercent=110
DisableWelcomePage=no
WizardImageFile=art\wizard-side-1x.bmp,art\wizard-side-2x.bmp
WizardSmallImageFile=art\wizard-small-1x.bmp,art\wizard-small-2x.bmp
SetupIconFile=..\src\Yoinks\Assets\Yoinks.ico
UninstallDisplayIcon={app}\{#AppExe}
UninstallDisplayName={#AppName}

OutputDir=..\dist
OutputBaseFilename=Yoinks-Setup-{#AppVersion}-x64
Compression=lzma2/max
SolidCompression=yes

; close a running Yoinks before replacing / removing files
CloseApplications=yes
RestartApplications=no
ChangesAssociations=yes

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Messages]
WelcomeLabel2=This will install [name/ver] on your computer.%n%nPaste a link from YouTube, X, Instagram, Threads, TikTok or 1,800+ other sites, pick a quality (or MP3), and it's yours. No popups, no fake download buttons, no sketchy redirects.%n%nEverything it needs (yt-dlp and FFmpeg) is included.%n%nMade with love by Tamim Ahmed Rijan.
FinishedLabel=Yoinks is ready. Copy a video link and open Yoinks: it will offer to paste it for you.

[Tasks]
Name: "desktopicon"; Description: "Create a &desktop shortcut"; GroupDescription: "Shortcuts:"
Name: "deno"; Description: "Download &Deno for full YouTube quality (about 45 MB, recommended)"; GroupDescription: "YouTube support:"
Name: "browserlinks"; Description: "Let your &browser send links to Yoinks (yoinks: links, used by the ""Yoink this"" bookmark)"; GroupDescription: "Integration:"

[Files]
Source: "..\dist\app\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\{#AppName}"; Filename: "{app}\{#AppExe}"; Comment: "Paste a link, yoink the video"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\{#AppExe}"; Tasks: desktopicon; Comment: "Paste a link, yoink the video"

[Registry]
; yoinks:<url> links (HKA = current user or all users, matching the install mode)
Root: HKA; Subkey: "Software\Classes\yoinks"; ValueType: string; ValueName: ""; ValueData: "URL:Yoinks link"; Tasks: browserlinks; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Classes\yoinks"; ValueType: string; ValueName: "URL Protocol"; ValueData: ""; Tasks: browserlinks
Root: HKA; Subkey: "Software\Classes\yoinks\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: """{app}\{#AppExe}"",0"; Tasks: browserlinks
Root: HKA; Subkey: "Software\Classes\yoinks\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#AppExe}"" ""%1"""; Tasks: browserlinks
; Win+R "yoinks" starts the app
Root: HKA; Subkey: "Software\Microsoft\Windows\CurrentVersion\App Paths\{#AppExe}"; ValueType: string; ValueName: ""; ValueData: "{app}\{#AppExe}"; Flags: uninsdeletekey
Root: HKA; Subkey: "Software\Microsoft\Windows\CurrentVersion\App Paths\{#AppExe}"; ValueType: string; ValueName: "Path"; ValueData: "{app}"

[Run]
Filename: "{app}\{#AppExe}"; Description: "Launch {#AppName} now"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
; also removes Deno and defaults.json, which setup created after copying files
Type: filesandordirs; Name: "{app}"

[Code]
var
  FolderPage: TInputDirWizardPage;
  DownloadPage: TDownloadWizardPage;
  DefaultDownloads: String;
  DenoDownloaded: Boolean;

// A running Yoinks (and any yt-dlp it started) locks its files.
procedure CloseYoinks();
var
  ResultCode: Integer;
begin
  Exec(ExpandConstant('{sys}\taskkill.exe'), '/F /T /IM {#AppExe}', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
  Sleep(400);
end;

function OnDownloadProgress(const Url, FileName: String; const Progress, ProgressMax: Int64): Boolean;
begin
  Result := True;
end;

procedure InitializeWizard();
begin
  DefaultDownloads := ExpandConstant('{%USERPROFILE}\Downloads');

  FolderPage := CreateInputDirPage(wpSelectDir,
    'Where should your videos go?',
    'Pick the folder Yoinks saves downloads to.',
    'Videos and MP3s you yoink will be saved here. You can change this any time in Yoinks > Settings.',
    False, '');
  FolderPage.Add('');
  FolderPage.Values[0] := GetPreviousData('DownloadFolder', DefaultDownloads);

  DownloadPage := CreateDownloadPage('Downloading Deno',
    'Getting the JavaScript runtime YouTube needs. This only takes a moment.',
    @OnDownloadProgress);
end;

procedure RegisterPreviousData(PreviousDataKey: Integer);
begin
  SetPreviousData(PreviousDataKey, 'DownloadFolder', FolderPage.Values[0]);
end;

function NextButtonClick(CurPageID: Integer): Boolean;
begin
  Result := True;
  if (CurPageID = wpReady) and WizardIsTaskSelected('deno') then
  begin
    DownloadPage.Clear;
    DownloadPage.Add('{#DenoUrl}', 'deno.zip', '');
    DownloadPage.Show;
    try
      try
        DownloadPage.Download;
        DenoDownloaded := True;
      except
        if not DownloadPage.AbortedByUser then
          SuppressibleMsgBox('Deno could not be downloaded right now:' + #13#10 + GetExceptionMessage + #13#10 + #13#10 +
            'Yoinks will still be installed. You can add Deno later from Settings > Components.',
            mbInformation, MB_OK, IDOK);
      end;
    finally
      DownloadPage.Hide;
    end;
  end;
end;

function UpdateReadyMemo(Space, NewLine, MemoUserInfoInfo, MemoDirInfo, MemoTypeInfo,
  MemoComponentsInfo, MemoGroupInfo, MemoTasksInfo: String): String;
begin
  Result := MemoDirInfo + NewLine + NewLine +
            'Save downloads to:' + NewLine + Space + FolderPage.Values[0] + NewLine + NewLine +
            'Included:' + NewLine + Space + 'yt-dlp (download engine)' + NewLine + Space + 'FFmpeg (HD merging, MP3)';
  if MemoTasksInfo <> '' then
    Result := Result + NewLine + NewLine + MemoTasksInfo;
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
begin
  CloseYoinks();
  Result := '';
end;

function JsonEscape(const S: String): String;
begin
  Result := S;
  StringChangeEx(Result, '\', '\\', True);
  StringChangeEx(Result, '"', '\"', True);
end;

procedure CurStepChanged(CurStep: TSetupStep);
var
  ResultCode: Integer;
  Zip, Tools: String;
begin
  if CurStep <> ssPostInstall then exit;

  // the download folder picked in the wizard (only stored when it differs from Downloads,
  // so a moved Downloads folder is still followed)
  if CompareText(RemoveBackslashUnlessRoot(FolderPage.Values[0]), DefaultDownloads) <> 0 then
  begin
    ForceDirectories(FolderPage.Values[0]);
    SaveStringToFile(ExpandConstant('{app}\defaults.json'),
      '{ "DownloadFolder": "' + JsonEscape(FolderPage.Values[0]) + '" }', False);
  end
  else
    DeleteFile(ExpandConstant('{app}\defaults.json'));

  if DenoDownloaded then
  begin
    WizardForm.StatusLabel.Caption := 'Setting up Deno for YouTube...';
    Zip := ExpandConstant('{tmp}\deno.zip');
    Tools := ExpandConstant('{app}\tools');
    ForceDirectories(Tools);
    if not Exec(ExpandConstant('{sys}\tar.exe'), '-xf "' + Zip + '" -C "' + Tools + '"', '', SW_HIDE, ewWaitUntilTerminated, ResultCode)
       or (ResultCode <> 0) or not FileExists(Tools + '\deno.exe') then
      Exec('powershell.exe', '-NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -LiteralPath ''' + Zip + ''' -DestinationPath ''' + Tools + ''' -Force"',
        '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
  end;
end;

function InitializeUninstall(): Boolean;
begin
  CloseYoinks();
  Result := True;
end;

// Ask before wiping the per-user data folder (%LOCALAPPDATA%\Yoinks). Downloaded videos are never touched.
procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
var
  DataDir: String;
begin
  if CurUninstallStep = usPostUninstall then
  begin
    DataDir := ExpandConstant('{localappdata}\Yoinks');
    if DirExists(DataDir) then
      if UninstallSilent or
         (SuppressibleMsgBox('Also remove your Yoinks settings and download history?' + #13#10 + #13#10 +
           'The videos and music you downloaded are never touched, only the app''s own settings, history and updated components.',
           mbConfirmation, MB_YESNO, IDYES) = IDYES) then
        DelTree(DataDir, True, True, True);
  end;
end;
