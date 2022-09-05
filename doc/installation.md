# Installation and setup

Metarank is available as a Docker and JAR packages for MacOS, Windows and Linux.

## Docker image

Metarank docker images are published on DockerHub as [metarank/metarank](https://hub.docker.com/r/metarank/metarank):
* only x86_64 images are published officially.
* `latest` tag may point to pre-release versions, use an exact pinned version for stability.
* on Mac M1 you can use x86_64 docker images, or try running the JAR file directly.

To start using metarank with docker, just run:
```bash
docker run metarank/metarank:0.5.2 --help
```

## JAR File

Metarank is a JVM application and also available as a JAR application on [Releases](https://github.com/metarank/metarank/releases)
page. As it bundles a couple of native libraries (interfaces to [LightGBM](https://github.com/metarank/lightgbm4j) and
[XGBoost](https://github.com/metarank/xgboost-java)), it supports the following platforms and operating systems:
* Linux: x86_64, JVM 11+
* Windows: x86_64, Windows 10+, JVM 11+
* MacOS: x86_64, AArch64, MacOS 11+, JVM 11+

To start metarank JAR file, just run:
```bash
java -jar metarank.jar --help
```

### Java

To run JVM applications, you need the JVM itself. If you have no JRE/JDK installed,
check out the [Eclipse Temurin JDK](https://adoptium.net/installation/) tutorials for different
operating systems.

Metarank is tested on JDK 11 and 17, but will probably work on 18+.

### Installing on MacOS

Metarank JAR app requires a [libomp](https://formulae.brew.sh/formula/libomp) to be installed:
```bash
brew install libomp
```

Without libomp you may encounter a strange UnsatisfiedLinkError while training the model:
```
15:32:03.936 INFO  ai.metarank.main.command.Train$ - training model for train=7067 test=1706
Loading native lib osx/x86_64/lib_lightgbm.dylib
Extracting native lib /var/folders/nl/2p5w70jj5_50ztn25q2xll380000gn/T/lib_lightgbm.dylib
Copied 3775632 bytes
Extracted file: exists=true path=/var/folders/nl/2p5w70jj5_50ztn25q2xll380000gn/T/lib_lightgbm.dylib
Cannot load library: java.lang.UnsatisfiedLinkError: Can't load library: /var/folders/nl/2p5w70jj5_50ztn25q2xll380000gn/T/lib_lightgbm.dylib cause: Can't load library: /var/folders/nl/2p5w70jj5_50ztn25q2xll380000gn/T/lib_lightgbm.dylib
Loading native lib osx/x86_64/lib_lightgbm_swig.dylib
Extracting native lib /var/folders/nl/2p5w70jj5_50ztn25q2xll380000gn/T/lib_lightgbm_swig.dylib
Copied 89308 bytes
Extracted file: exists=true path=/var/folders/nl/2p5w70jj5_50ztn25q2xll380000gn/T/lib_lightgbm_swig.dylib
Cannot load library: java.lang.UnsatisfiedLinkError: Can't load library: /var/folders/nl/2p5w70jj5_50ztn25q2xll380000gn/T/lib_lightgbm_swig.dylib cause: Can't load library: /var/folders/nl/2p5w70jj5_50ztn25q2xll380000gn/T/lib_lightgbm_swig.dylib
Exception in thread "io-compute-1" java.lang.UnsatisfiedLinkError: 'long com.microsoft.ml.lightgbm.lightgbmlibJNI.new_voidpp()'
```
