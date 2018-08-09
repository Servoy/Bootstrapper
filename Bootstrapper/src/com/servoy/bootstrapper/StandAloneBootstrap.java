package com.servoy.bootstrapper;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class StandAloneBootstrap extends BaseBootstrap {

	public static final String CODEBASE_PROPERTY = "codebase";
	public static final String SOLUTION_PROPERTY = "solution";

	public static void main(String[] args) throws MalformedURLException, FileNotFoundException, IOException {
		InputStream is = BaseBootstrap.class.getResourceAsStream("/bootstrap.properties");
		if (is != null) {
			Properties properties = new Properties();
			properties.load(is);
			String codebaseString = properties.getProperty(CODEBASE_PROPERTY);
			if (codebaseString == null) throw new RuntimeException("Code base not set in the boostrap properties file" );
			
			URL codebase =new URL(codebaseString) ;
			String solution = properties.getProperty(SOLUTION_PROPERTY, "servoy_client");
			int threadPoolSize = Integer.parseInt(properties.getProperty(THREAD_POOL_SIZE_NAME, "8"));
			
			String homeDir = properties.getProperty(BOOTSTRAPPER_HOME_DIR_PROPERTY);
			if (homeDir != null) {
				System.setProperty(BOOTSTRAPPER_HOME_DIR_PROPERTY, homeDir);
			}
			
			System.setProperty("com.servoy.remote.codebase", codebaseString);
			System.setProperty("com.servoy.remote.checker", "com.servoy.j2db.smart.StandAloneRemoteChecker");
			
			List<String> arguments = new ArrayList<>();
			int i = 0;
			while(true) {
				String value = properties.getProperty("arg"+i);
				if (value == null) break;
				arguments.add(value);
				i++;
			}

			loadAndStartClient(solution, codebase, threadPoolSize, arguments.toArray(new String[arguments.size()]));
		} else
			throw new RuntimeException("bootstrap.properties not found");
	}

}
