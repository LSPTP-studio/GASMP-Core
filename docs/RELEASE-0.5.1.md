# Release cleanup — 0.5.1

This maintenance step aligns the Gradle project version, generated JAR version, plugin metadata and documentation. It also removes duplicate permission keys from `plugin.yml`, declares the internal permission nodes once, and checks that every command declared by the plugin is bound during startup. No gameplay mechanics were changed in this step.

The Paper API dependency is pinned to the stable `26.2.build.129-stable` artifact for a reproducible 26.2 API target.
