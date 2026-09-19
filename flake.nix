{
  description = "QTalk development environment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  };

  outputs = { self, nixpkgs }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs {
        inherit system;
      };

      nativeLibs = with pkgs; [
        libGL
        libx11
        fontconfig
        stdenv.cc.cc.lib
      ];
    in
    {
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
