{
  description = "A Nix-flake based Java 21 + Maven 3.5.4 dev env for midpoint";
  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";

  outputs = {
    self,
    nixpkgs,
  }: let
    javaVersion = 21;
    overlays = [
      (final: prev: {
        jdk = prev."jdk${toString javaVersion}_headless";
      })
    ];
    supportedSystems = ["x86_64-linux" "aarch64-linux" "x86_64-darwin" "aarch64-darwin"];
    forEachSupportedSystem = f:
      nixpkgs.lib.genAttrs supportedSystems (system:
        f {
          pkgs = import nixpkgs {inherit overlays system;};
        });
  in {
    devShells = forEachSupportedSystem ({pkgs}: {
      default = pkgs.mkShell {
        packages = with pkgs; [jdk maven pgadmin];
        shellHook = ''
          echo "Atricore Midpoint: Java dev env ("${pkgs.jdk.name}" / ${pkgs.maven.name})"
          export JAVA_HOME="${pkgs.jdk}/lib/openjdk"
          export MAVEN_HOME="${pkgs.maven}"
          export MAVEN_OPTS="-Xmx2048m"
        '';
      };
    });
  };
}
