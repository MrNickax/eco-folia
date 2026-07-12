# eco (Folia)

> ⚠️ **Unofficial fork.** This is an independent fork of [eco](https://github.com/Auxilor/eco),
> patched to run on **Folia**. It is not affiliated with, endorsed by, or supported by Auxilor
> or the original eco team.

**eco** is the plugin framework that powers EcoEnchants, libreforge and many other plugins.
This fork ports it to **Folia** (and Folia-based servers); all other functionality comes from
the upstream project.

## Disclaimer

This software is provided **as is, without any warranty**. It is an unofficial adaptation
maintained on my own, and **I take no responsibility for any bugs, crashes, data loss or damage**
resulting from its use. Use it at your own risk, and always test in a controlled environment
before deploying to production.

Issues that also occur in the official version should be reported upstream — not blamed on this
fork.

## For developers

The `com.willfp:eco` API is published to **GitHub Packages**. GitHub requires authentication with
a `read:packages` token even for public packages, so add your credentials to
`~/.gradle/gradle.properties` (`gpr.user` / `gpr.key`) or the `GITHUB_ACTOR` / `GITHUB_TOKEN`
environment variables.

```kotlin
repositories {
    maven("https://maven.pkg.github.com/MrNickax/eco-folia") {
        credentials {
            username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
            password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    compileOnly("com.willfp:eco:VERSION") // e.g. 7.6.3-folia
}
```

## Building

```bash
git clone https://github.com/MrNickax/eco-folia
cd eco-folia
./gradlew build
```

The runnable plugin jar is produced in `build/libs/` and attached to each
[release](https://github.com/MrNickax/eco-folia/releases).

## Credits & license

Based on [Auxilor/eco](https://github.com/Auxilor/eco), licensed under the MIT license. The
original license terms are preserved — see [LICENSE.md](LICENSE.md).
