package com.liferay.blade.cli;

import aQute.lib.getopt.Options;
import aQute.lib.justif.Justif;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Formatter;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Gregory Amerson
 * @author David Truong
 */
public class Util {

	public static final String APP_SERVER_PARENT_DIR_PROPERTY =
		"app.server.parent.dir";

	public static final String APP_SERVER_TYPE_PROPERTY = "app.server.type";

	public static boolean canConnect(String host, int port) {
		InetSocketAddress address = new InetSocketAddress(
			host, Integer.valueOf(port));
		InetSocketAddress local = new InetSocketAddress(0);

		InputStream in = null;

		try (Socket socket = new Socket()) {
			socket.bind(local);
			socket.connect(address, 3000);
			in = socket.getInputStream();

			return true;
		}
		catch (Exception e) {
		}

		finally {
			if (in != null) {
				try {
					in.close();
				}
				catch (Exception e) {
				}
			}
		}

		return false;
	}
	public static File findParentFile(
		File dir, String[] fileNames, boolean checkParents) {

		if (dir == null) {
			return null;
		}

		for (String fileName : fileNames) {
			File file = new File(dir, fileName);

			if (file.exists()) {
				return dir;
			}
		}

		if (checkParents) {
			return findParentFile(dir.getParentFile(), fileNames, checkParents);
		}

		return null;
	}

	public static List<Properties> getAppServerProperties(File dir) {
		File projectRoot = findParentFile(
			dir, _APP_SERVER_PROPERTIES_FILE_NAMES, true);

		List<Properties> properties = new ArrayList<>();

		for (String fileName : _APP_SERVER_PROPERTIES_FILE_NAMES) {
			File file = new File(projectRoot, fileName);

			if (file.exists()) {
				properties.add(getProperties(file));
			}
		}

		return properties;
	}

	public static Properties getGradleProperties(File dir) {
		File file = getGradlePropertiesFile(dir);

		return getProperties(file);
	}

	public static File getGradlePropertiesFile(File dir) {
		File gradlePropertiesFile = new File(
			getWorkspaceDir(dir), _GRADLE_PROPERTIES_FILE_NAME);

		return gradlePropertiesFile;
	}

	public static File getGradleWrapper(File dir) {
		File gradleRoot = findParentFile(
			dir,
			new String[] {
				_GRADLEW_UNIX_FILE_NAME, _GRADLEW_WINDOWS_FILE_NAME },
			true);

		if (gradleRoot != null) {
			if (isWindows()) {
				return new File(gradleRoot, _GRADLEW_WINDOWS_FILE_NAME);
			}
			else {
				return new File(gradleRoot, _GRADLEW_UNIX_FILE_NAME);
			}
		}

		return null;
	}

	public static Properties getProperties(File file) {
		try (InputStream inputStream = new FileInputStream(file)) {
			Properties properties = new Properties();

			properties.load(inputStream);

			return properties;
		}
		catch (Exception e) {
			return null;
		}
	}

	public static File getWorkspaceDir(blade blade) {
		return getWorkspaceDir(blade.getBase());
	}

	public static File getWorkspaceDir(File dir) {
		return findParentFile(
			dir,
			new String[] {
				_SETTINGS_GRADLE_FILE_NAME, _GRADLE_PROPERTIES_FILE_NAME
			},
			true);
	}

	public static boolean isWindows() {
		return System.getProperty("os.name").toLowerCase().contains("windows");
	}

	public static boolean isWorkspace(blade blade) {
		return isWorkspace(blade.getBase());
	}

	public static boolean isWorkspace(File dir) {
		File workspaceDir = getWorkspaceDir(dir);

		File gradleFile = new File(workspaceDir, _SETTINGS_GRADLE_FILE_NAME);

		if (!gradleFile.exists()) {
			return false;
		}

		try {
			String script = read(gradleFile);

			Matcher matcher = Workspace.PATTERN_WORKSPACE_PLUGIN.matcher(
				script);

			if (matcher.find()) {
				return true;
			}
			else {
				//For workspace plugin < 1.0.5

				gradleFile = new File(workspaceDir, _BUILD_GRADLE_FILE_NAME);

				script = read(gradleFile);

				matcher = Workspace.PATTERN_WORKSPACE_PLUGIN.matcher(script);

				return matcher.find();
			}
		}
		catch (Exception e) {
			return false;
		}
	}

	public static void printHelp(
			blade blade, Options options, String cmd,
			Class< ? extends Options> optionClass)
		throws Exception {

		Justif j = new Justif();

		try (Formatter f = j.formatter()) {
			options._command().help(f, null, cmd, optionClass);

			j.wrap();

			blade.err().println(f);
		}
	}

	public static String read(File file) throws IOException {
		return new String(Files.readAllBytes(file.toPath()));
	}

	public static void readProcessStream(
		final InputStream is, final PrintStream ps) {

		Thread t = new Thread(new Runnable() {
			public void run() {
				try (InputStreamReader isr = new InputStreamReader(is);
					 BufferedReader br = new BufferedReader(isr)) {

					String line = null;

					while ( (line = br.readLine()) != null) {
						ps.println(line);
					}

					is.close();
				}

				catch (IOException ioe) {
					ioe.printStackTrace();
				}
			}

		});

		t.start();
	}

	public static void setShell(ProcessBuilder processBuilder, String cmd) {
		Map<String, String> env = processBuilder.environment();

		List<String> commands = new ArrayList<>();

		if (Util.isWindows()) {
			commands.add("cmd.exe");
			commands.add("/c");
		}
		else {
			env.put("PATH", env.get("PATH") + ":/usr/local/bin");

			commands.add("sh");
			commands.add("-c");
		}

		commands.add(cmd);

		processBuilder.command(commands);
	}

	public static Process startProcess(blade blade, String command)
		throws Exception {

		return startProcess(blade, command, null, null, true);
	}

	public static Process startProcess(
			blade blade, String command, File dir, boolean inheritIO)
		throws Exception {

		return startProcess(blade, command, dir, null, inheritIO);
	}

	public static Process startProcess(
			blade blade, String command, File dir,
			Map<String, String> environment)
		throws Exception {

		return startProcess(blade, command, dir, environment, true);
	}

	public static Process startProcess(
			blade blade, String command, File dir,
			Map<String, String> environment, boolean inheritIO)
		throws Exception {

		ProcessBuilder processBuilder = new ProcessBuilder();

		Map<String, String> env = processBuilder.environment();

		if (environment != null) {
			env.putAll(environment);
		}

		if ((dir != null) && dir.exists()) {
			processBuilder.directory(dir);
		}

		setShell(processBuilder, command);

		if(inheritIO) {
			processBuilder.inheritIO();
		}

		Process process = processBuilder.start();

		if (!inheritIO) {
			readProcessStream(process.getInputStream(), blade.out());
			readProcessStream(process.getErrorStream(), blade.err());
		}

		process.getOutputStream().close();

		return process;
	}

	public static void unzip(File srcFile, File destDir, String entryToStart)
		throws IOException {

		try (final ZipFile zip = new ZipFile(srcFile)) {
			final Enumeration<? extends ZipEntry> entries = zip.entries();

			boolean foundStartEntry = entryToStart == null;

			while (entries.hasMoreElements()) {
				final ZipEntry entry = entries.nextElement();

				String entryName = entry.getName();

				if (!foundStartEntry) {
					foundStartEntry = entryToStart.equals(entryName);
					continue;
				}

				if (entry.isDirectory() ||
					((entryToStart != null) &&
					 !entryName.startsWith(entryToStart))) {

					continue;
				}

				if (entryToStart != null) {
					entryName = entryName.replaceFirst(entryToStart, "");
				}

				final File f = new File(destDir, entryName);
				final File dir = f.getParentFile();

				if (!dir.exists() && !dir.mkdirs()) {
					final String msg = "Could not create dir: " + dir.getPath();
					throw new IOException(msg);
				}

				try (final InputStream in = zip.getInputStream(entry);
						final FileOutputStream out = new FileOutputStream(f)) {

					final byte[] bytes = new byte[1024];
					int count = in.read(bytes);

					while (count != -1) {
						out.write(bytes, 0, count);
						count = in.read(bytes);
					}

					out.flush();
				}
			}
		}
	}

	private static final String[] _APP_SERVER_PROPERTIES_FILE_NAMES = {
		"app.server." + System.getProperty("user.name") + ".properties",
		"app.server." + System.getenv("COMPUTERNAME") + ".properties",
		"app.server." + System.getenv("HOST") + ".properties",
		"app.server." + System.getenv("HOSTNAME") + ".properties",
		"app.server.properties",
		"build." + System.getProperty("user.name") + ".properties",
		"build." + System.getenv("COMPUTERNAME") + ".properties",
		"build." + System.getenv("HOST") + ".properties",
		"build." + System.getenv("HOSTNAME") + ".properties",
		"build.properties"
	};

	private static final String _BUILD_GRADLE_FILE_NAME = "build.gradle";

	private static final String _GRADLE_PROPERTIES_FILE_NAME =
		"gradle.properties";

	private static final String _GRADLEW_UNIX_FILE_NAME = "gradlew";

	private static final String _GRADLEW_WINDOWS_FILE_NAME = "gradlew.bat";

	private static final String _SETTINGS_GRADLE_FILE_NAME = "settings.gradle";

}