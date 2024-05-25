SedeveModules Repository
====================

[This is an open repository](https://github.com/scuptio/SedeveModules/) 

(For us to gauge demand, please star (`eyes up and right`) this repository if you use the SedeveModules.)


-----------

| [StateDB.tla](https://github.com/scuptio/TLAPlusSedeveModules/blob/master/modules/StateDB.tla)               | Store/Load a value to/from a given database. | [&#10004;](https://github.com/scuptio/TLAPlusSedeveModules/blob/master/modules/tlc2/overrides/StateDB.java)    | [@ybbh](https://github.com/ybbh) |  
| [GenID.tla](https://github.com/scuptio/TLAPlusSedeveModules/blob/master/modules/GenID.tla)                    | Generate a random UUID with its string representation. | [&#10004;](https://github.com/scuptio/TLAPlusSedeveModules/blob/master/modules/tlc2/overrides/GenUUID.java)       | [@ybbh](https://github.com/ybbh) | 

How to use it
-------------

You must be running [Java 9 or higher](https://github.com/tlaplus/SedeveModules/issues/34#issuecomment-756571840).

Just copy & paste the snippet, the operators, or the set of modules you are interested in.

Alternatively, clone this repository and pass ```-DTLA-Library=/path/to/SedeveModules/modules``` when running TLC.

Another option is to download a [library archive](https://github.com/tlaplus/SedeveModules/releases) and add it to TLC's or the Toolbox's *TLA+ library path*. The advantage of doing this is that TLC will evaluate an operator faster if the operator comes with a Java implementation (see e.g. [SequencesExt.Java](https://github.com/tlaplus/SedeveModules/blob/master/modules/tlc2/overrides/SequencesExt.java)). The latest release is at the stable URL https://github.com/tlaplus/SedeveModules/releases/latest/download/SedeveModules-deps.jar.

If you are using the Toolbox, add the library archive under `File > Preferences > TLA+ Preferences > TLA+ library path locations`.
[![Screencast how to install the SedeveModules into the TLA+ Toolbox](https://img.youtube.com/vi/w9t6JnmxV2E/0.jpg)](https://www.youtube.com/watch?v=w9t6JnmxV2E); add JVM arguments,  `-Dtlc2.overrides.TLCOverrides=tlc2.overrides.TLCOverrides:tlc2.overrides.SedeveTLCOverrides`.
`tlc2.overrides.TLCOverrides` is class name for the original [CommunityModules](https://github.com/tlaplus/CommunityModules/), if it is not used, this can be skipped.


If you are using the [VS Code extension](https://github.com/tlaplus/vscode-tlaplus), a recent version of the community modules is bundled with the nightly build. If you are not using the nightly build or need to use another version, see [this](https://github.com/tlaplus/vscode-tlaplus/issues/249).

If you are running TLC via tla2tools.jar, ensure the JAR is on the *classpath*: either place it next to tla2tools.jar or add it explicitly with `java -Dtlc2.overrides.TLCOverrides=tlc2.overrides.TLCOverrides:tlc2.overrides.SedeveTLCOverrides -cp tla2tools.jar:SedeveModules-deps.jar ...`.

Being a community-driven repository puts the community in charge of checking the validity and correctness of submissions. The maintainers of this repository will try to keep this place in order. Still, we can't guarantee the quality of the modules and, therefore, cannot provide any assistance on eventual malfunctions.

Contributing
------------

If you have one or more snippets, operators, or modules you'd like to share, please open an issue or create
a pull request.  Before submitting your operator or module, please consider adding documentation.  The more documentation there is, the more likely it is that someone will find it useful.

If you change an existing module and tests start failing, check all tests that assert (usually `AssertError` operator) specific error messages, i.e., line numbers and module names.
Note that even an unrelated change further up in the file might have changed the line number and could lead to a failing test case.

Test
------------
Run

``` shell
ant test
```

Download
--------

![CI](https://github.com/scuptio/TLAPlusSedeveModules/workflows/CI/badge.svg)
