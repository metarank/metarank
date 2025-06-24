# Custom logging

In a cases when you need to override default Metarank logging configuration, you may need to build a custom docker image,
based on an original upstream one from Metarank.

Metarank uses the following entrypoint for the docker container:

```shell
#!/bin/bash

set -euxo pipefail
OPTS=${JAVA_OPTS:-"-Xmx1g -verbose:gc"}

exec /usr/bin/java $OPTS -cp "/app/*" ai.metarank.main.Main "$@"

```

So you should note the following configuration toggles here:

* env variable JAVA_OPTS can be used to pass custom JVM flags, like path to a custom logger configuration.
* by default Metarank loads all the JAR files found in the `/app/` directory.

## Example: logstash-logback-encoder

To enable structured logging via [logstash-logback-encoder](https://github.com/logfellow/logstash-logback-encoder), you
can build a custom Docker image with the following Dockerfile:

```shell
FROM metarank/metarank:0.7.11-amd64

# add logback configuration file to the image
ADD logback.xml /app/

# add the logstash-logback-encoder with all its runtime dependencies to the classpath
ADD https://repo1.maven.org/maven2/net/logstash/logback/logstash-logback-encoder/7.4/logstash-logback-encoder-7.4.jar /app/
ADD https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-core/2.16.0/jackson-core-2.16.0.jar /app/
ADD https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.16.0/jackson-databind-2.16.0.jar /app/

# override default logback configuration
ENV JAVA_OPTS="-Xmx1g -Dlogback.configurationFile=/app/logback.xml -Dlogback.debug=true"

```

Such a custom image will successfully load the custom configuration with non-default appender:

```
+ OPTS='-Xmx1g -Dlogback.configurationFile=/app/logback.xml -Dlogback.debug=true'
+ exec /usr/bin/java -Xmx1g -Dlogback.configurationFile=/app/logback.xml -Dlogback.debug=true -cp '/app/*' ai.metarank.main.Main --help
19:14:30,083 |-INFO in ch.qos.logback.classic.LoggerContext[default] - This is logback-classic version 0.7.3
19:14:30,113 |-INFO in ch.qos.logback.classic.LoggerContext[default] - Found resource [/app/logback.xml] at [file:/app/logback.xml]
19:14:30,175 |-WARN in ch.qos.logback.core.joran.action.IncludeAction - Could not find resource corresponding to [logback-properties.xml]
19:14:30,225 |-INFO in ch.qos.logback.core.model.processor.StatusListenerModelHandler - Added status listener of type [ch.qos.logback.core.status.OnConsoleStatusListener]
19:14:30,227 |-INFO in ch.qos.logback.core.model.processor.AppenderModelHandler - Processing appender named [CONSOLE_JSON]
19:14:30,227 |-INFO in ch.qos.logback.core.model.processor.AppenderModelHandler - About to instantiate appender of type [ch.qos.logback.core.ConsoleAppender]
19:14:30,242 |-INFO in ch.qos.logback.core.model.processor.ImplicitModelHandler - Assuming default type [net.logstash.logback.composite.loggingevent.LoggingEventJsonProviders] for [providers] property
19:14:30,250 |-INFO in ch.qos.logback.core.model.processor.ImplicitModelHandler - Assuming default type [net.logstash.logback.composite.loggingevent.LoggingEventFormattedTimestampJsonProvider] for [timestamp] property
19:14:30,253 |-INFO in ch.qos.logback.core.model.processor.ImplicitModelHandler - Assuming default type [net.logstash.logback.composite.loggingevent.LoggingEventPatternJsonProvider] for [pattern] property
19:14:30,258 |-INFO in ch.qos.logback.core.model.processor.ImplicitModelHandler - Assuming default type [net.logstash.logback.composite.loggingevent.StackTraceJsonProvider] for [stackTrace] property
19:14:30,383 |-INFO in ch.qos.logback.classic.model.processor.RootLoggerModelHandler - Setting level of ROOT logger to INFO
19:14:30,383 |-INFO in ch.qos.logback.core.model.processor.AppenderRefModelHandler - Attaching appender named [CONSOLE_JSON] to Logger[ROOT]
19:14:30,383 |-INFO in ch.qos.logback.core.model.processor.DefaultProcessor@24fcf36f - End of configuration.
19:14:30,384 |-INFO in ch.qos.logback.classic.joran.JoranConfigurator@4d02f94e - Registering current configuration as safe fallback point
```