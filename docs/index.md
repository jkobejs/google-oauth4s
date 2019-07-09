---
layout: home
title: "Home"
section: "home"
---

# Google Oauth 2.0 for Scala

Effectfull API for [Google OAuth 2.0][google-oauth] flows for Scala which is constrained by cats effect **Sync** typeclass. Currently it supports only server to server interaction.

Quick start
------------
The current version is **{{site.googleOauth4sVersion}}** for **Scala 2.11/12** with
- [tsec][tsec] {{site.tsecVersion}}
- [http4s][http4s] {{site.http4sVersion}}

To use library publish it locally.
```scala
scalacOptions += "-Ypartial-unification" // 2.11.9+

libraryDependencies += "com.jkobejs" %% "google-oauth4s" % "{{site.googleOauth4sVersion}}"
```

Server to server
----------------
[Examples][server-to-server] of server to server auth.


[google-oauth]: https://developers.google.com/identity/protocols/OAuth2
[tsec]: https://jmcardon.github.io/tsec/
[http4s]: https://http4s.org/
[server-to-server]: /server-to-server
