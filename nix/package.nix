{ lib, stdenvNoCC, fetchurl, makeWrapper, jdk21, python3, binutils, runCommand
, nativeLibraries, fontsConf, appArchive ? null }:
let
  versionLine = lib.findFirst (lib.hasPrefix "qtalkVersion=")
    (throw "qtalkVersion is missing") (lib.splitString "\n" (builtins.readFile ../gradle.properties));
  version = lib.removeSuffix "\r" (lib.removePrefix "qtalkVersion=" versionLine);
  application = builtins.fromJSON (builtins.readFile ./application.json);
  runtime = builtins.fromJSON (builtins.readFile ./runtime-lock.json);
  appSource = if appArchive != null then appArchive else fetchurl {
    inherit (application) url hash;
    name = application.name;
  };
  artifacts = map (artifact:
    let original = fetchurl { inherit (artifact) name url hash; };
    in if artifact.linphone then runCommand "linphone-sdk-stripped.jar" {
      nativeBuildInputs = [ python3 binutils ];
    } ''
      python3 ${../installer/linux/strip_sdk.py} ${original} "$out"
    '' else original
  ) runtime.dependencies;
  classpath = lib.concatStringsSep ":" (map toString artifacts);
in
assert application.version == version;
stdenvNoCC.mkDerivation {
  pname = "qtalk";
  inherit version;
  src = appSource;
  sourceRoot = ".";
  nativeBuildInputs = [ makeWrapper ];
  dontBuild = true;
  installPhase = ''
    runHook preInstall
    mkdir -p "$out/bin" "$out/share/qtalk/app" "$out/share/applications" "$out/share/pixmaps"
    install -m644 desktopApp.jar shared-jvm.jar "$out/share/qtalk/app/"
    cp ${../installer/linux/qtalk.desktop} "$out/share/applications/qtalk.desktop"
    substituteInPlace "$out/share/applications/qtalk.desktop" \
      --replace-fail /usr/bin/qtalk "$out/bin/qtalk"
    cp ${../branding/logo.png} "$out/share/pixmaps/qtalk.png"
    echo ${lib.escapeShellArg version} > "$out/share/qtalk/version"
    makeWrapper ${jdk21}/bin/java "$out/bin/qtalk" \
      --set QTALK_INSTALL_METHOD nix \
      --set-default _JAVA_AWT_WM_NONREPARENTING 1 \
      --set-default FONTCONFIG_FILE ${fontsConf} \
      --prefix LD_LIBRARY_PATH : "${lib.makeLibraryPath nativeLibraries}:/run/opengl-driver/lib" \
      --add-flags "-cp \"$out/share/qtalk/app/*:${classpath}\"" \
      --add-flags com.qvoste.qtalk.MainKt
    runHook postInstall
  '';
  passthru = { runtimeArtifacts = artifacts; inherit jdk21; };
  meta = {
    description = "SIP calls and contacts";
    homepage = "https://github.com/qvostedev/QTalk";
    license = lib.licenses.agpl3Only;
    platforms = [ "x86_64-linux" ];
    mainProgram = "qtalk";
  };
}
