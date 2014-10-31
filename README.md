# mono-maven-plugin

This plugin overriddes the default lifecycle and provides a package phase that creates the dll. It
generates the DLL using the command line tool `gmcs`. Dependencies of type dll are automatically
included, and other command line options can be configured.

## Usage

The plugin extends the default lifecycle and provides a package phase that creates the dll. Add
this in your `<build><plugins>` section:

    <plugin>
      <groupId>com.threerings.maven</groupId>
      <artifactId>mono-maven-plugin</artifactId>
      <version>0.0-SNAPSHOT</version>
    </plugin>

For a complete description of all the options, look at the source or in the maven help:

    mvn help:describe -Dplugin=com.threerings.maven:mono-maven-plugin -Ddetail

## License

mono-maven-plugin is released under the MIT License, which can be found in the [LICENSE] file.

[LICENSE]: https://github.com/jamie-threerings/mono-maven-plugin/blob/master/LICENSE
