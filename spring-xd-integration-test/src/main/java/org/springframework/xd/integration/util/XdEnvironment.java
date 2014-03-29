/*
 * Copyright 2011-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.xd.integration.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.util.StringUtils;

/**
 * Extracts the host and port information for the XD Instances
 * 
 * @author Glenn Renfro
 */
public class XdEnvironment {

	// Environment Keys
	public static final String XD_ADMIN_HOST = "xd_admin_host";

	public static final String XD_CONTAINERS = "xd_containers";

	public static final String XD_HTTP_PORT = "xd_http_port";

	public static final String XD_JMX_PORT = "xd_jmx_port";

	public static final String XD_CONTAINER_LOG_DIR = "xd_container_log_dir";

	public static final String XD_BASE_DIR = "xd_base_dir";

	// EC2 Environment Keys

	public static final String XD_PRIVATE_KEY_FILE = "xd_private_key_file";

	public static final String XD_RUN_ON_EC2 = "xd_run_on_ec2";

	public static final String XD_PAUSE_TIME = "xd_pause_time";

	// JDBC Environment Keys

	public static final String JDBC_URL = "jdbc_url";

	public static final String JDBC_USERNAME = "jdbc_username";

	public static final String JDBC_PASSWORD = "jdbc_password";

	public static final String JDBC_DATABASE = "jdbc_database";

	public static final String JDBC_DRIVER = "jdbc_driver";


	// Result Environment Variables
	public final static String RESULT_LOCATION = "/tmp/xd/output";

	public final static String LOGGER_LOCATION = "/home/ubuntu/spring-xd-1.0.0.BUILD-SNAPSHOT/xd/logs/container.log";

	public final static String BASE_XD = "/home/ubuntu/spring-xd-1.0.0.BUILD-SNAPSHOT/xd";

	public static final String HTTP_PREFIX = "http://";

	// Each line in the artifact has a entry to identify it as admin, container or singlenode.
	// These entries represent the supported types.
	public static final String ADMIN_TOKEN = "adminNode";

	public static final String CONTAINER_TOKEN = "containerNode";

	public static final String SINGLENODE_TOKEN = "singleNode";

	// The artifacts file name
	private static final String ARTIFACT_NAME = "ec2servers.csv";


	private transient final URL adminServer;

	private transient final List<URL> containers;

	private transient final int jmxPort;

	private transient final String containerLogLocation;

	private transient final String baseXdDir;

	private transient final int httpPort;

	private transient String privateKey;

	private transient boolean isOnEc2 = true;

	private transient int pauseTime = 1;

	private static final int SERVER_TYPE_OFFSET = 0;

	private static final int HOST_OFFSET = 1;

	private static final int XD_PORT_OFFSET = 2;

	private static final int HTTP_PORT_OFFSET = 3;

	private static final int JMX_PORT_OFFSET = 4;


	// JDBC Attributes
	private transient String jdbcUrl;

	private transient String jdbcUsername;

	private transient String jdbcPassword;

	private transient String jdbcDatabase;

	private transient String jdbcDriver;

	private static final Logger LOGGER = LoggerFactory
			.getLogger(XdEnvironment.class);

	private final Properties systemProperties;

	public XdEnvironment() throws Exception {
		systemProperties = System.getProperties();
		final Properties properties = getXDDeploymentProperties();
		containers = getContainers(properties);
		adminServer = getAdminServer(properties);
		jmxPort = Integer.parseInt(properties.getProperty(XD_JMX_PORT));
		httpPort = Integer.parseInt(properties.getProperty(XD_HTTP_PORT));

		jdbcUrl = systemProperties.getProperty(JDBC_URL);
		jdbcUsername = systemProperties.getProperty(JDBC_USERNAME);
		jdbcPassword = systemProperties.getProperty(JDBC_PASSWORD);
		jdbcDatabase = systemProperties.getProperty(JDBC_DATABASE);
		jdbcDriver = systemProperties.getProperty(JDBC_DRIVER);

		containerLogLocation = getContainerLogLocation(systemProperties);
		baseXdDir = getBaseDirLocation(systemProperties);

		isOnEc2 = getOnEc2Flag();
		pauseTime = getPauseTimeFromEnv();
		if (isOnEc2) {
			String keyFile = getPrivateKeyFile();
			isFilePresent(keyFile);
			privateKey = getPrivateKey(keyFile);
		}
	}


	public int getPauseTime() {
		return pauseTime;
	}


	public void setPauseTime(int pauseTime) {
		this.pauseTime = pauseTime;
	}

	public URL getAdminServer() {
		return adminServer;
	}

	public List<URL> getContainers() {
		return containers;
	}

	public int getJMXPort() {
		return jmxPort;
	}

	public int getHttpPort() {
		return httpPort;
	}

	public String getPrivateKey() {
		return privateKey;
	}

	public boolean isOnEc2() {
		return isOnEc2;
	}

	public String getContainerLogLocation() {
		return containerLogLocation;
	}

	public String getBaseDir() {
		return baseXdDir;
	}

	/**
	 * retrieves the private key from a file, so we can execute commands on the container.
	 * 
	 * @param privateKeyFile The location of the private key file
	 * @return
	 * @throws IOException
	 */
	private String getPrivateKey(String privateKeyFile) throws IOException {
		String result = "";
		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader(privateKeyFile));
			while (br.ready()) {
				result += br.readLine() + "\n";
			}
		}
		finally {
			if (br != null) {
				try {
					br.close();
				}
				catch (Exception ex) {
					// ignore error.
				}
			}
		}
		return result;
	}

	/**
	 * Retrieves the admin host url from properties
	 * 
	 * @param properties
	 * @return
	 */
	private URL getAdminServer(Properties properties) {

		URL result = null;
		final String host = properties.getProperty(XD_ADMIN_HOST);
		try {
			result = new URL(host);
		}
		catch (MalformedURLException mue) {
			LOGGER.info("XD_ADMIN_HOST was not identified in either an artifact or system environment variables");
		}
		return result;
	}

	/**
	 * Retrieves a list o containers from the properties file.
	 * 
	 * @param properties
	 * @return
	 */
	private List<URL> getContainers(Properties properties) {
		final List<URL> containers = new ArrayList<URL>();
		final Set<String> containerHosts = StringUtils
				.commaDelimitedListToSet(properties.getProperty(XD_CONTAINERS));
		final Iterator<String> iter = containerHosts.iterator();
		while (iter.hasNext()) {
			final String containerHost = iter.next();
			try {
				containers.add(new URL(containerHost));
			}
			catch (MalformedURLException ex) {
				LOGGER.error("Container Host IP is invalid ==>" + containerHost);
			}

		}
		return containers;
	}

	/**
	 * retrieves the location of the container log from a server.
	 * 
	 * @param properties
	 * @return
	 */
	private String getContainerLogLocation(Properties properties) {
		String result = LOGGER_LOCATION;
		if (properties.containsKey(XD_CONTAINER_LOG_DIR)) {
			result = properties.getProperty(XD_CONTAINER_LOG_DIR);
		}
		return result;
	}

	private String getBaseDirLocation(Properties properties) {
		String result = BASE_XD;
		if (properties.containsKey(XD_BASE_DIR)) {
			result = properties.getProperty(XD_BASE_DIR);
		}
		return result;
	}

	/**
	 * Retrieves the properties necessary for testing from either the environment or from the artifact file generated by
	 * the XD-EC2 application.
	 * 
	 * @return
	 */
	private Properties getXDDeploymentProperties() {
		Properties result = getPropertiesFromArtifact();
		// if the artifact does not contain the admin try the System
		if (!result.containsKey(XD_ADMIN_HOST)
				|| !result.containsKey(XD_CONTAINERS)) {
			result = getPropertiesFromSystem();
		}
		if (!result.containsKey(XD_ADMIN_HOST)
				|| !result.containsKey(XD_CONTAINERS)) {
			throw new IllegalArgumentException(
					"No admin server host or XD_Containers has been set."
							+ "  This can be set via an artifact or setting the "
							+ XD_ADMIN_HOST + " and " + XD_CONTAINERS
							+ " environment variables");
		}

		return result;
	}

	private Properties getPropertiesFromArtifact() {
		final Properties props = new Properties();
		BufferedReader reader = null;
		String containerHosts = null;
		try {
			final File file = new File(ARTIFACT_NAME);
			if (file.exists()) {
				reader = new BufferedReader(new FileReader(ARTIFACT_NAME));
				while (reader.ready()) {
					final String line = reader.readLine();
					final String tokens[] = StringUtils
							.commaDelimitedListToStringArray(line);
					if (tokens.length < 4) {
						continue;// skip invalid lines
					}
					if (tokens[SERVER_TYPE_OFFSET].equals(ADMIN_TOKEN)) {
						props.setProperty(XD_ADMIN_HOST, HTTP_PREFIX
								+ tokens[HOST_OFFSET] + ":"
								+ tokens[XD_PORT_OFFSET]);
						props.setProperty(XD_HTTP_PORT,
								tokens[HTTP_PORT_OFFSET]);
						props.setProperty(XD_JMX_PORT, tokens[JMX_PORT_OFFSET]);

					}
					if (tokens[SERVER_TYPE_OFFSET].equals(CONTAINER_TOKEN)) {
						if (containerHosts == null) {
							containerHosts = HTTP_PREFIX
									+ tokens[HOST_OFFSET].trim() + ":"
									+ tokens[XD_PORT_OFFSET];
						}
						else {
							containerHosts = containerHosts + "," + HTTP_PREFIX
									+ tokens[HOST_OFFSET].trim() + ":"
									+ tokens[XD_PORT_OFFSET];
						}
					}
					if (tokens[SERVER_TYPE_OFFSET].equals(SINGLENODE_TOKEN)) {
						props.setProperty(XD_ADMIN_HOST, HTTP_PREFIX
								+ tokens[HOST_OFFSET] + ":"
								+ tokens[XD_PORT_OFFSET]);
						props.setProperty(XD_HTTP_PORT,
								tokens[HTTP_PORT_OFFSET]);
						props.setProperty(XD_JMX_PORT, tokens[JMX_PORT_OFFSET]);

						containerHosts = HTTP_PREFIX
								+ tokens[HOST_OFFSET].trim() + ":" + tokens[XD_PORT_OFFSET];
						props.put(XD_CONTAINERS, containerHosts);
					}
				}
			}
		}
		catch (IOException ioe) {
			// Ignore file open error. Default to System variables.
		}
		finally {
			try {
				if (reader != null) {
					reader.close();
				}
			}
			catch (IOException ioe) {
				// ignore
			}
		}
		if (containerHosts != null) {
			props.put(XD_CONTAINERS, containerHosts);
		}
		return props;
	}

	private Properties getPropertiesFromSystem() {
		final Properties props = new Properties();
		if (systemProperties.containsKey(XD_ADMIN_HOST)) {
			props.put(XD_ADMIN_HOST,
					systemProperties.getProperty(XD_ADMIN_HOST));
		}
		if (systemProperties.containsKey(XD_CONTAINERS)) {
			props.put(XD_CONTAINERS,
					systemProperties.getProperty(XD_CONTAINERS));
		}
		if (systemProperties.containsKey(XD_JMX_PORT)) {
			props.put(XD_JMX_PORT, systemProperties.getProperty(XD_JMX_PORT));
		}
		if (systemProperties.containsKey(XD_HTTP_PORT)) {
			props.put(XD_HTTP_PORT, systemProperties.getProperty(XD_HTTP_PORT));
		}

		return props;
	}

	private String getPrivateKeyFile() {
		if (!systemProperties.containsKey(XD_PRIVATE_KEY_FILE)) {
			throw new IllegalArgumentException(
					"No ec2 private key file has been set."
							+ "  This can be set via the "
							+ XD_PRIVATE_KEY_FILE + " environment variable");
		}
		return systemProperties.getProperty(XD_PRIVATE_KEY_FILE);
	}

	private boolean getOnEc2Flag() {
		boolean result = isOnEc2;
		if (systemProperties.containsKey(XD_RUN_ON_EC2)) {
			result = Boolean.getBoolean(XD_RUN_ON_EC2);
		}
		return result;
	}

	private int getPauseTimeFromEnv() {
		int result = pauseTime;
		if (systemProperties.containsKey(XD_PAUSE_TIME)) {
			result = Integer.getInteger(XD_PAUSE_TIME);
		}
		return result;
	}

	private void isFilePresent(String keyFile) {
		File file = new File(keyFile);
		if (!file.exists()) {
			throw new IllegalArgumentException("The XD Private Key File ==> " + keyFile + " does not exist.");
		}
	}


	public String getJdbcUrl() {
		return jdbcUrl;
	}

	public String getJdbcUsername() {
		return jdbcUsername;
	}


	public String getJdbcPassword() {
		return jdbcPassword;
	}

	public String getJdbcDriver() {
		return jdbcDriver;
	}

	public String getJdbcDatabase() {
		return jdbcDatabase;
	}

	public int getJmxPort() {
		return jmxPort;
	}

}