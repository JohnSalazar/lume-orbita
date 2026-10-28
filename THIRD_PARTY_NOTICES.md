# Third-party licenses and rights

The MIT license in LICENSE covers Lume's original code and documentation. It does not replace third-party licenses or grant rights to platform trademarks, logos, titles, or images.

- **Gradle Wrapper**: distributed under its Apache 2.0 license. The original text is preserved in `third_party/gradle-wrapper-LICENSE.txt`; script headers and the JAR are preserved. The Gradle distribution is obtained separately and verified against the SHA pinned in the wrapper.
- **Kotlin 2.1.20 / runtime**: Apache 2.0, with copyright and notices from JetBrains and contributors. Official texts from tag v2.1.20 are preserved in `third_party/kotlin-*`. Dependencies retain their own licenses.
- **Android SDK / Android Gradle Plugin / JUnit / JaCoCo**: tools and dependencies obtained separately through their official package managers; using them in this project does not relicense them under MIT. JUnit and JaCoCo are used for tests and reports, not in the application interface.
- **30 bundled atmospheres**: original artwork created for Lume using OpenAI image generation, with provenance in `docs/wallpaper-sources.json`. Included with the project under MIT to the extent of the applicable rights; these are not photographs by the authors listed in the screensaver section.
- **Remote screensaver photographs**: CC0 1.0 or CC BY 4.0, depending on the image. Authors, sources, and license links are listed in `docs/photo-credits.md` and the `screensaver.json` asset. Resizing, cropping, and dimming are display modifications. None of these photographs has been incorporated into this repository.
- **Installed app icons and recommendations**: queried on the device at runtime. They belong to their respective rights holders and are not redistributed as repository assets. Private prototypes containing extracted icons were excluded from publication.

Lume is independent and is not affiliated with Apple, Google, KONKA, or streaming services. Its liquid-glass aesthetic reference does not imply affiliation or use of those interfaces' proprietary icons.