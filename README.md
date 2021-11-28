# Astrolabe

This is a utility for working with closed loop stepper drivers
over serial connections.

# Compiling

Create a shadow jar that can be run using `java -jar`: 

```bash
./gradlew shadowJar
```

Create a precompiled native image:

```bash
./gradlew nativeImage
```

# Connecting

## Simulated Device Mode

This is included for exploration and testing purposes.

```
a7e connect [-d <device type>]
```

# Developing

## Maintaining the Native Image Config

The native image is generated using Graal, which analyzes the Java code to make it more compact and performant. In order
to deal with things like reflection, the Graal compiler must be provided with configurations that describe the resources
and classes used by the application.

These configurations can be difficult to create by hand. Graal includes instrumentation to produce these configurations
automatically based on application execution though. To do so:

1. Ensure you have Graal installed
   - On OSX, run `./install-graal.sh`
2. Create a shadow jar using the commands in the above section
3. Run the jar with instrumentation. This will update the image config.
   ```bash
   ${GRAAL_HOME}/bin/java -agentlib:native-image-agent=config-merge-dir=./src/main/resources/META-INF/native-image \
   -jar ./build/libs/astrolabe-all.jar <...>
   ```
   You can use `config-output-dir` instead of `config-merge-dir` to rewrite it completely. You may need to do this if 
   you change Graal versions.
4. Review the proposed changes to make sure they make sense before merging them with the source.

## Debugging the Console

The console doesn't run properly in the IntelliJ IDE Launch terminal and perhaps other terminals. To launch a version
that can be connected to using remote debugging, run:

```
./gradlew shadowJar && java '-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005' \
-jar build/libs/astrolabe-11-all.jar -v TRACE connect -d simulated
```
