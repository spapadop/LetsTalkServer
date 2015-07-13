package se.bth.swatkats.letstalk.server.connection;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.MissingResourceException;
import java.util.Properties;

public class MessageBundle {
	private static final String BUNDLE_NAME = "lib" + File.separator + "server.properties"; //$NON-NLS-1$

	private static final Properties prop = new Properties();

	static {
		try {
			prop.load(new FileInputStream(new File(BUNDLE_NAME)));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private MessageBundle() {
	}

	public static String getString(String key) {
		try {
			return prop.getProperty(key);
		} catch (MissingResourceException e) {
			return '!' + key + '!';
		}
	}
}
