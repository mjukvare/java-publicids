# Contributing to PublicIds

## Building

The library targets Java 21, and building it requires JDK 21 or newer.

```shell
./build.sh
```

This runs `./mvnw clean verify`, which compiles, tests and packages every module. Each library module produces a main
jar, a `-sources.jar` and a `-javadoc.jar`. The script always builds with JDK 21: it uses `JAVA_HOME_21` if set,
otherwise on macOS it finds an installed JDK 21, otherwise it checks that `JAVA_HOME` points at one.

To go further than `verify`, name the phase first. Any other arguments go to Maven:

```shell
./build.sh install
./build.sh -Dtest=PublicIdsTest
```

## Publishing to Maven Central

Releases go to Maven Central through the [Central Portal](https://central.sonatype.com). The `release` profile in the
root `pom.xml` signs every artifact with GPG and uploads it with the `central-publishing-maven-plugin`. Builds that
don't use the profile never sign or upload anything.

### One-time setup

1. **Central Portal account.** You need an account with publishing rights for the `com.mjukvare` namespace. Generate a
   user token under *View Account → Generate User Token*.
2. **GPG key.** Create a signing key (or use an existing one) and publish the public key to a keyserver so Central can
   verify signatures:

   ```shell
   gpg --gen-key
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
   ```

3. **Maven settings.** Add the portal token to `~/.m2/settings.xml`. The server id must be `central-sonatype` to match
   the `publishingServerId` in `pom.xml`:

   ```xml
   <settings>
       <servers>
           <server>
               <id>central-sonatype</id>
               <username><!-- token username --></username>
               <password><!-- token password --></password>
           </server>
       </servers>
   </settings>
   ```

   Keep credentials in your local `settings.xml` or your CI secret store. Never commit them to this repository.

### Releasing a version

The version comes from the `revision` property (CI-friendly versions), so a release doesn't need any POM edits.

1. Tag the commit you are releasing, on a clean working tree, so the published artifacts match the tag exactly:

   ```shell
   git tag v1.2.3
   ```

2. Build, sign and upload from that tag:

   ```shell
   ./release.sh -Dgpg.keyname=<KEY_ID>
   ```

   The script stops if the working tree has uncommitted or untracked changes, or if HEAD doesn't carry exactly one
   `vX.Y.Z` tag. It then runs `./build.sh deploy -P release -Drevision=X.Y.Z`, taking the version from the tag, so
   a release goes through the same JDK 21 build and tests as a local build. It passes any arguments you give it on
   to Maven.

   - `-Dgpg.keyname` picks the signing key. Leave it out to use your default GPG key.
   - The GPG passphrase comes from `gpg-agent`, which prompts for it. For a non-interactive build such as CI, set the
     `MAVEN_GPG_PASSPHRASE` environment variable instead.

3. The upload isn't published automatically. Open *Deployments* in the Central Portal, check that validation passed,
   and press *Publish*. Once a version is published on Central it can't be changed or removed.

4. Push the tag:

   ```shell
   git push origin v1.2.3
   ```

If the upload fails validation, drop the deployment in the portal, delete the local tag (`git tag -d v1.2.3`), fix
the problem and start again. The tag isn't public until you push it, so it is safe to recreate.
