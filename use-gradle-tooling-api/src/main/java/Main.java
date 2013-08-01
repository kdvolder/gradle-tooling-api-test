import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.net.URI;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.LongRunningOperation;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProgressEvent;
import org.gradle.tooling.ProgressListener;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.eclipse.EclipseProject;
import org.gradle.tooling.model.eclipse.HierarchicalEclipseProject;

public class Main {

	/**
	 * Build a gradle project model using the same api calls as the gradle tooling.
	 * This code was put together by copying from gradle tooling code base and stripping
	 * out eclipse apis.
	 * 
	 * 'External' information such as that may come from Eclipse preferences has been 
	 * replaced by constants. The constanst can all be set to null but may be set to
	 * something else (so as to emulate the same setup as inside the tooling/eclipse).
	 * 
	 * When different preferences are set different api calls are made to intialize
	 * model build operations. (I.e. typically is some pref is set the info
	 * is passed to gradle via an api call, if it is not set then that api
	 * is not called and Gradle is left to pick its own default.
	 * 
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		//Try two Eclipse model builds a 'summary' and 'full' model.
		buildModel(new File("/home/kdvolder/workspaces-sts/spring-ide/use-gradle-tooling-api"), HierarchicalEclipseProject.class);
		buildModel(new File("/home/kdvolder/workspaces-sts/spring-ide/use-gradle-tooling-api"), EclipseProject.class);
	}
	
	private static final URI DISTRIBUTION_PREF = null;
	private static final File GRADLE_USER_HOME_PREF = null;
	private static final File JAVA_HOME_PREF = null;
	private static final String[] JVM_ARGS_PREF = null;
	private static final String[] PROGRAM_ARGS_PREF = null;
	
	/**
	 * In tooling IO is redirected to display in an Eclipse console. This class mocks that behavior
	 */
	public static class Console {

		public OutputStream out = new ByteArrayOutputStream();
		public OutputStream err = new ByteArrayOutputStream();
		public void close() throws Exception {
			System.out.println("Closing the console, dumping output received");
			out.close();
			err.close();
			
			System.out.println("=== System.out ===");
			System.out.println(out.toString());
			System.out.println("=== System.err ===");
			System.out.println(err.toString());
			System.out.println("==================");
		}
	}


	private static ProjectConnection getGradleConnector(File projectLoc, URI distributionPref, File gradleUserHomePref) {
		GradleConnector connector = GradleConnector.newConnector();
		if (gradleUserHomePref!=null) {
			connector.useGradleUserHomeDir(gradleUserHomePref);
		}
		// Configure the connector and create the connection
		if (distributionPref!=null) {
			boolean distroSet = false;
			if ("file".equals(distributionPref.getScheme())) {
				File maybeFolder = new File(distributionPref);
				if (maybeFolder.isDirectory()) {
					connector.useInstallation(maybeFolder);
					distroSet = true;
				}
			}
			if (!distroSet) {
				connector.useDistribution(distributionPref);
			}
		}
		connector.forProjectDirectory(projectLoc);
		return connector.connect();
	}
	
	/**
	 * Tries to connect to gradle, using the distrubution set by the preferences page. If this fails and the prefs page wasn't
	 * actually set, then we try to fall back on the distribution zip that's packaged up into the core plugin.
	 */
	public static ProjectConnection getGradleConnector(File projectLoc) throws Exception {
		ProjectConnection connection;
		URI distribution = DISTRIBUTION_PREF;
		File gradleUserHome = GRADLE_USER_HOME_PREF;
		connection = getGradleConnector(projectLoc, distribution, gradleUserHome);
		return connection;
	}
	
	/**
	 * @param conf May be null in contexts where there is no launch configuration (e.g. build model operations, or tasks executed for an import
	 * rather than directly by the user). 
	 */
	public static void configureOperation(LongRunningOperation gradleOp) {
		File javaHome = JAVA_HOME_PREF;
		if (javaHome!=null) {
			gradleOp.setJavaHome(javaHome);
		}
		String[] jvmArgs = JVM_ARGS_PREF;
		if (jvmArgs!=null) {
			gradleOp.setJvmArguments(jvmArgs);
		}
		String[] pgmArgs = PROGRAM_ARGS_PREF;
		if (pgmArgs!=null) {
			gradleOp.withArguments(pgmArgs);
		}
//			gradleLaunchConfigurationDelegate_configureOperation(gradleOp, conf);
	}
	
	public static <T extends HierarchicalEclipseProject> T buildModel(File projectLoc, Class<T> requiredType) throws Exception {
		ProjectConnection connection = null;
		Console console = null;
		try {
			connection = getGradleConnector(projectLoc);

			// Load the Eclipse model for the project
			
			ModelBuilder<T> builder = connection.model(requiredType);
			configureOperation(builder);
			console = getConsole("Building Gradle Model '"+projectLoc+"'");
			builder.setStandardOutput(console.out);
			builder.setStandardError(console.err);
			builder.addProgressListener(new ProgressListener() {
				public void statusChanged(ProgressEvent evt) {
					System.out.println("progress = '"+evt.getDescription()+"'");
				}

			});
			T model = builder.get();  // blocks until the model is available
			return model;
		} finally {
			if (connection!=null) {
				connection.close();
			}
			if (console!=null) {
				console.close();
			}
		}
	}

	private static Console getConsole(String name) {
		return new Console();
	}
	
}
