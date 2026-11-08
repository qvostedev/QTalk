{
  description = "QTalk desktop package and development environment";

  inputs = {
        nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
        # Старый nixpkgs нужен только для GLEW 2.2,
        nixpkgs-old.url = "github:NixOS/nixpkgs/nixos-24.11";
  };

  outputs = { self, nixpkgs, nixpkgs-old }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs {
        inherit system;
      };

      oldPkgs = import nixpkgs-old {
        inherit system;
      };

      nativeLibs = with pkgs; [
        libGL
        libx11
        libxv
        fontconfig
        freetype
        alsa-lib
        libpulseaudio
        glib
        gtk3
        zlib
        stdenv.cc.cc.lib
        oldPkgs.glew
      ];
      fontsConf = pkgs.makeFontsConf { fontDirectories = [ pkgs.dejavu_fonts ]; };
      qtalk = pkgs.callPackage ./nix/package.nix { nativeLibraries = nativeLibs; inherit fontsConf; };
    in
    {
      packages.${system} = { inherit qtalk; default = qtalk; };
      apps.${system} = {
        qtalk = { type = "app"; program = "${qtalk}/bin/qtalk"; };
        default = self.apps.${system}.qtalk;
      };
      devShells.${system}.default = pkgs.mkShell {
        packages = with pkgs; [
          jdk21
          gradle
          cmake
          ninja
          pkg-config
          gcc
          gdb
        ];

        LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath nativeLibs;

        shellHook = ''
          echo "QTalk development environment"
          echo "Java: $(java -version 2>&1 | head -1)"
        '';
      };
    };
}
