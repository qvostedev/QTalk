{
  description = "QTalk development environment";

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
        stdenv.cc.cc.lib
        oldPkgs.glew
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
