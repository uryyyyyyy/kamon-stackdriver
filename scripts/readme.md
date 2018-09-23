
# setup

```
// ~/.sbt/1.0/plugins/gpg.sbt


addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.3")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.1")
```

```
// ~/.sbt/1.0/sonatype.sbt

credentials += Credentials("Sonatype Nexus Repository Manager",
                           "oss.sonatype.org",
                           "<sonatype user id>",
                           "<sonatype password>")
```

# publish

```
./scripts/publish.sh

# enter PGP passphrase
```

# release

- open https://oss.sonatype.org/index.html#stagingRepositories
- find & check your repository
- if there is no error, press "close" button
- if close operation success, press "release" button
