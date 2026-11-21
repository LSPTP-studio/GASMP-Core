# Build GASMP Core in GitHub Actions

The repository includes `.github/workflows/build.yml`. GitHub Actions builds the plugin using Temurin Java 25 and Gradle 9.9.2, then uploads the JAR as a downloadable workflow artifact.

## Create the repository

1. Sign in to GitHub as `LSPTP-studio`.
2. Create a new repository named `GASMP-Core` (Private is recommended).
3. Do not initialize it with a README, license or `.gitignore`; the source folder already contains them.
4. Download and extract `GASMP-Core-0.7.0-source.zip` on your computer.
5. Install GitHub Desktop from `https://desktop.github.com/` if you do not already use Git.
6. In GitHub Desktop, clone the new empty `LSPTP-studio/GASMP-Core` repository.
7. Copy the contents of the extracted `GASMP-Core-0.7.0` folder into that cloned repository folder (including the hidden `.github` folder).
8. In GitHub Desktop, commit the files with message `Import GASMP Core 0.7.0` and click **Push origin**.

## Download the JAR

1. Open the repository on GitHub.
2. Select **Actions** → **GASMP Core Build**.
3. Select **Run workflow** if the first build has not started automatically.
4. Wait for the workflow to finish. A green check means the build job completed; a red X means it failed and the logs must be inspected.
5. Open the successful workflow run and download the `GASMP-Core-<commit-sha>` artifact.
6. Extract the artifact ZIP. It contains `GASMP-Core-0.7.0.jar` under the artifact root/build output.

## Install on MCServerHost

1. Stop the server and back up `plugins/`, worlds and `plugins/GASMP-Core/data.db`.
2. Confirm the MCServerHost instance uses Paper 26.2 and Java 25.
3. Install LuckPerms, Vault and a Vault-compatible Coins provider first.
4. Upload the built JAR into `plugins/` using the host File Manager or SFTP.
5. If enabling custom crate models, install RP Engine and copy the separate crate content folder as described in `integrations/rp-engine/README.md`.
6. Start the server and inspect the complete console log before testing any gameplay commands.

Do not upload the source ZIP or GitHub Actions artifact ZIP into the `plugins/` directory. Only upload the extracted plugin JAR.

## Build workflow contents

- `actions/checkout@v7`
- `actions/setup-java@v5` with Temurin Java 25
- `actions/setup-python@v7` with Python 3.13
- `gradle/actions/setup-gradle@v6` with Gradle 9.9.2
- `python -m pip install PyYAML`
- `python scripts/validate_release.py`
- `python scripts/validate_release.py`
- `gradle --no-daemon clean build`
- `actions/upload-artifact@v4`

The Actions workflow is included in the source archive, but has not been run yet because no GASMP repository is currently connected/available to the GitHub connector. Creating and pushing the repository must be done from your GitHub account.
