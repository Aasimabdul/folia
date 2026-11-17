# Folia sideload signing key

`folia-sideload.jks` is the key that signs the release APKs published on GitHub.

**It is public on purpose.** Android only installs an update over an installed app when both APKs are signed
with the same key. Without a fixed key, every CI build used a new throwaway debug key, so each release needed an
uninstall first ("App not installed").

- Alias: `folia`, store and key password: `folia-sideload`, PKCS12, RSA 2048, valid until 2056.
- Because the key is public, anyone could build an APK that Android accepts as an update to Folia. Install Folia
  only from the official releases of this repository. Folia has no network permission and requests no dangerous permissions.
- **Not for Google Play.** If Folia is ever published in a store, use a private key kept in secrets instead.
- To use a private key for GitHub releases: add the repository secrets `FOLIA_KEYSTORE_BASE64`,
  `FOLIA_KEYSTORE_PASSWORD`, `FOLIA_KEY_ALIAS`, `FOLIA_KEY_PASSWORD`. The build prefers them automatically.
  Changing the key means users uninstall once.
