//
// monotouch-maven-plugin - builds and deploys MonoTouch projects
// http://github.com/samskivert/monotouch-maven-plugin/blob/master/LICENSE

package com.threerings.maven.mono;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.cli.Commandline;

/**
 * A compile phase which uses {@code mcs} to compile a cs files.
 *
 * <p>IMPORTANT: requires the plugin to be registered as an extension so it can run in place
 * of javac for the compile phase</p>
 */
@Mojo(name="package", defaultPhase=LifecyclePhase.PACKAGE,
    requiresDependencyResolution=ResolutionScope.COMPILE)
public class PackageMojo extends AbstractMojo
{
    /**
     * Location of {@code mcs} binary.
     */
    @Parameter(defaultValue="mcs", property="mono.mcs")
    private String mcsPath;

    /**
     * Source file patterns to include.
     */
    @Parameter
    private String[] sources = {};

    /**
     * Additional library files to include. Dependencies of type {@code dll} are automatically
     * included.
     */
    @Parameter
    private String[] additionalLibs = {};

    /**
     * Disables the default compiler configuration.
     */
    @Parameter(defaultValue="false", property="mono.noconfig")
    private boolean noconfig;

    /**
     * Disables importing the system standard libraries.
     */
    @Parameter(defaultValue="false", property="mono.nostdlib")
    private boolean nostdlib;

    /**
     * Defines the packaging version to use (-pkg).
     */
    @Parameter(defaultValue="dotnet35", property="mono.pkg")
    private String pkg;

    /**
     * Defines the target type (-target).
     */
    @Parameter(defaultValue="library", property="mono.target")
    private String target;

    /**
     * Defines additional parameters.
     */
    @Parameter(defaultValue="", property="mono.parameters")
    private String parameters;

    /**
     * Sets whether the fully qualified artifact name should be used when creating the dll. This
     * can be an issue when assembly names are being checked downstream.
     */
    @Parameter(defaultValue="false", property="mono.useFullArtifactName")
    private boolean useFullArtifactName;

    /**
     * Defines the verbose flag (-v).
     */
    @Parameter(defaultValue="false", property="mono.verbose")
    private boolean verbose;

    @Parameter(defaultValue="${project}")
    private MavenProject project;

    public void execute ()
            throws MojoExecutionException
    {
        getLog().debug("Base dir: " + project.getBasedir().getPath());

        Commandline cli = new Commandline(mcsPath);
        cli.setWorkingDirectory(project.getBasedir());
        if (verbose) {
            cli.createArg().setValue("-v");
        }

        // create target directory if needed
        File projectDir = new File(project.getBuild().getDirectory());
        getLog().debug("Project dir: " + project.getBuild().getDirectory());
        if (!projectDir.isDirectory()) {
            getLog().debug("Creating project directory");
            projectDir.mkdirs();
        }

        // configure artifact file
        String targetFilename = (useFullArtifactName ?
            project.getBuild().getFinalName(): project.getArtifactId());
        getLog().debug("Target filename: " + targetFilename);

        File artifactFile = new File(projectDir, targetFilename + ".dll");
        project.getArtifact().setFile(artifactFile);

        cli.createArg().setValue("-out:" + artifactFile.getPath());
        if (!nostdlib) {
            cli.createArg().setValue("-pkg:" + pkg);
        } else {
            cli.createArg().setValue("-nostdlib");
        }
        if (noconfig) {
            cli.createArg().setValue("-noconfig");
        }
        cli.createArg().setValue("-target:" + target);
        cli.createArg().setLine(parameters);

        for (Object obj : project.getArtifacts()) {
            Artifact artifact = (Artifact)obj;
            getLog().debug("Considering artifact [" + artifact.getGroupId() + ":" +
                artifact.getArtifactId() + "]");
            // I think @requiresDependencyResolution compile prevents this, but let's be sure.
            if (artifact.getScope().equals(Artifact.SCOPE_TEST)) {
                continue;
            }
            if ("dll".equals(artifact.getType())) {
                cli.createArg().setValue("-r:" + artifact.getFile().getPath());
            }
        }

        for (String lib : findFiles(additionalLibs)) {
            cli.createArg().setValue(lib);
        }

        for (int ii = 0; ii < sources.length; ii++) {
            if (sources[ii].contains("*") && !sources[ii].contains("**")) {
                cli.createArg().setValue("-recurse:'" + sources[ii] + "'");
                sources[ii] = "";
            }
        }

        for (String source : findFiles(sources)) {
            cli.createArg().setValue(source);
        }

        getLog().debug("Command line:");
        for (String arg : cli.getCommandline()) {
            getLog().debug("  " + arg);
        }

        try {
            // create our own process so we can call redirectErrorStream
            Process process = new ProcessBuilder().
                directory(cli.getWorkingDirectory()).
                command(Arrays.asList(cli.getShellCommandline())).
                redirectErrorStream(true).
                start();
            BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()));
            while (true) {
                String line = input.readLine();
                if (line == null) {
                    break;
                }
                getLog().error("[mcs] " + line);
            }
            try {
                int code = process.waitFor();
                if (code != 0) {
                    throw new MojoExecutionException("mcs failed: " + code);
                }
            } catch (InterruptedException ex) {
                throwMojo("Interrupted", ex);
            }
        } catch (IOException ex) {
            throwMojo("Failed to execute mcs", ex);
        }
    }

    private String[] findFiles (String[] includes)
    {
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(project.getBasedir());
        scanner.setIncludes(includes);
        // scanner.setExcludes(excludes);
        scanner.scan();
        return scanner.getIncludedFiles();
    }

    private static void throwMojo (String message, Throwable t)
            throws MojoExecutionException
    {
        MojoExecutionException mex = new MojoExecutionException(message);
        mex.initCause(t);
        throw mex;
    }
}
