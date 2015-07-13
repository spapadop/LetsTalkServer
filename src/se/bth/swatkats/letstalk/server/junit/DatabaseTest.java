package se.bth.swatkats.letstalk.server.junit;

import static org.junit.Assert.*;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.junit.Test;
import org.junit.runner.JUnitCore;

import com.mchange.v2.c3p0.ComboPooledDataSource;

import se.bth.swatkats.letstalk.server.connection.Database;
import se.bth.swatkats.letstalk.server.connection.MessageBundle;

/**
 * 
 * @author Inanc
 *
 */
public class DatabaseTest {

	private static Database database;

	static final String JDBC_DRIVER = MessageBundle
			.getString("Database.JDBC_DRIVER"); //$NON-NLS-1$
	static final String DB_URL = MessageBundle.getString("Database.DB_URL"); //$NON-NLS-1$
	static final String DB_URL_SC = MessageBundle
			.getString("Database.DB_URL_SC"); //$NON-NLS-1$
	static final String USER = MessageBundle.getString("Database.USER"); //$NON-NLS-1$
	static final String PASS = MessageBundle.getString("Database.PASS"); //$NON-NLS-1$
	static final String SQL_SCRIPT = MessageBundle
			.getString("Database.SQL_SCRIPT"); //$NON-NLS-1$

	private static ComboPooledDataSource dataSource = new ComboPooledDataSource();

	Connection conn = null;
	BufferedWriter output;

	public DatabaseTest() {

		try {
			output = new BufferedWriter(
					new FileWriter(
							"./logs/log",
							true));
			output.append("\n\n\n");
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		System.out.println("Starting Test Server.");
		try {

			Class.forName("com.mysql.jdbc.Driver");

			// Open a connection
			System.out.println("Connecting to server...");

			openConnection();

			createTestSet();

			database = new Database();
		} catch (Exception e) {
			// Handle errors for Class.forName
			e.printStackTrace();
			fail();
		}
	}

	/*
	 * Creates a copy of the users in initial state. Used as Test Set for some
	 * tests.
	 */
	public void createTestSet() {
		try {
			String raw_query = "select * from users limit " + TEST_USERS_COUNT;
			Statement stmt = conn.createStatement();
			ResultSet rs = stmt.executeQuery(raw_query);
			int count = 0; // For filling the Test Set
			while (rs.next() && count < testSet.length) {
				int user_id = rs.getInt("user_id");
				String user_name = rs.getString("username");
				String pwd = rs.getString("pwd");
				String email = rs.getString("email");
				int adminFlag = rs.getInt("admin_flag");
				int status = rs.getInt("status");
				testSet[count] = new TestUser(user_id, user_name, pwd, email,
						adminFlag, status);
				count++;
			}
		} catch (SQLException se) {
			se.printStackTrace();
		}
	}

	/*
	 * Sets users' status's according to their id's. Passes if succeeded.
	 */
	@Test
	public void statusTest() {
		try {
			output.append("Started Status Test on " + getNow() + "\n");

			output.append("Setup\n");
			for (TestUser user : testSet) {
				int id = user.id;
				if (id < STATUS_TEST_COUNT) {
					output.append("\nUser #" + id + "'s status is set to ");
					switch (id % 4) {
					case 1:
						assertEquals(true, database.setUserStatusAvailable(id));
						output.append("'Available'");// Log
						break;
					case 2:
						assertEquals(true, database.setUserStatusBusy(id));
						output.append("'Busy'");// Log
						break;
					case 3:
						assertEquals(true, database.setUserStatusIdle(id));
						output.append("'Idle'");// Log
						break;
					case 0:
						assertEquals(true, database.setUserStatusOffline(id));
						output.append("'Offline'");// Log
						break;
					default:
						break;
					}
				}
			}
			output.append("\nTesting\n");
			String raw_query = "select status from users where user_id < ?";
			try {
				PreparedStatement stmt = conn.prepareStatement(raw_query);
				stmt.setInt(1, STATUS_TEST_COUNT);
				ResultSet rs = stmt.executeQuery();
				int count = 1;
				output.append("\nTests done for ");
				while (rs.next()) {
					int status = rs.getInt("status");
					if (count % 4 == 1) {
						assertEquals(status, AVAILABLE);
					} else if (count % 4 == 2) {
						assertEquals(status, BUSY);
					} else if (count % 4 == 3) {
						assertEquals(status, IDLE);
					} else if (count % 4 == 0) {
						assertEquals(status, OFFLINE);
					} else {
						System.out.println("Extra");
					}
					output.append("User #" + count + " ");
					count++;
				}
				output.append("\nAll test passed on " + getNow());
			} catch (SQLException e) {
				System.out.println(e);
				fail();
			}
			output.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}

	/*
	 * Tries to authenticate each user with correct password and a wrong
	 * password. Passes if both tests pass for every user.
	 */
	@Test
	public void loginTest() {
		try {
			output.append("Started Login Test on " + getNow() + "\n");
			try {
				output.append("\nAuthenticated Users");
				for (TestUser user : testSet) {
					assertEquals(
							user.id,
							database.authenticateLogin(user.userName, user.pwd,
									conn).getId());
					output.append(" #" + user.id);
					openConnection();
					assertEquals(
							WRONG_PASSWORD,
							database.authenticateLogin(user.userName,
									SAMPLE_WRONG_PWD, conn).getId());
					;
				}
				output.append(" with correct passwords, and authentication failed with a wrong password");
				output.append("\nAll test passed on " + getNow());
			} catch (Exception e) {
				// Handle errors for Class.forName
				e.printStackTrace();
				fail();
			}

			output.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}

	/*
	 * Tries a wrong password, changes passwords, tries old password, changes
	 * back old password accordingly for some users. Passes if all tests pass
	 * for every user.
	 */
	@Test
	public void changePasswordTest() {
		try {
			output.append("Started Password Change Test on " + getNow() + "\n");
			for (int i = 0; i < TEST_SUBJECT_COUNT; i++) {
				output.append("\nUser #" + (i + 1) + " updates : ");
				TestUser user = testSet[i];

				openConnection();
				assertEquals(
						// Wrong password
						AUTH_FAIL_CPW,
						database.changeUserPassword(user.id, user.userName,
								SAMPLE_WRONG_PWD, SAMPLE_NEW_PWD));
				output.append("Wrong password tried, ");

				openConnection();
				assertEquals( // New password
						SUCCESS_CPW, database.changeUserPassword(user.id,
								user.userName, user.pwd, SAMPLE_NEW_PWD));
				output.append("Password is changed, ");

				openConnection();
				assertEquals( // Old password won't work
						AUTH_FAIL_CPW, database.changeUserPassword(user.id,
								user.userName, user.pwd, SAMPLE_NEW_PWD));
				output.append("Old password tried; thus failed to change, ");

				openConnection();
				assertEquals( // Initial State
						SUCCESS_CPW, database.changeUserPassword(user.id,
								user.userName, SAMPLE_NEW_PWD, user.pwd));
				output.append("Reverted to old password.");
			}
			output.append("\nAll tests passed on " + getNow());
			output.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/*
	 * Adds i+1'th user to local address book for i'th user. Tests for each
	 * user. Passes if all tests pass for every user.
	 */
	@Test
	public void addLocalUserTest() {
		try {
			output.append("Started Add Local User Test on " + getNow() + "\n\n");

			database.setConnection(database.getConnection());

			try { // Delete previous addresses.
				String raw = "delete from address";
				Statement st = conn.createStatement();
				st.executeUpdate(raw);
			} catch (SQLException e) {
				e.printStackTrace();
			}

			database.setConnection(database.getConnection());
			for (int i = 1; i < TEST_USERS_COUNT; i++) {
				assertEquals(SUCCESS, database.addLocalUser(i, i + 1));
				output.append("User #" + i + " have added User #" + (i + 1)
						+ " to local address book\n");
			}
			String raw_query = "select * from address where user_id = ? and user_id_has = ?";
			try {
				output.append("Tests done for ");
				int sID = -1, oID = -1;
				for (int i = 1; i < TEST_USERS_COUNT; i++) {
					PreparedStatement stmt = conn.prepareStatement(raw_query);
					SUBJECT = i;
					OBJECT = i + 1;
					stmt.setInt(1, SUBJECT);
					stmt.setInt(2, OBJECT);
					ResultSet rs = stmt.executeQuery();
					if (rs.next()) {
						sID = rs.getInt("user_id");
						oID = rs.getInt("user_id_has");
					}
					assertEquals(sID, SUBJECT);
					assertEquals(oID, OBJECT);
					output.append("User #" + sID + " ");
				}
				output.append("\nAll tests passed on " + getNow());
				output.close();
			} catch (SQLException e) {
				System.out.println(e);
				fail();
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}

	/*
	 * Reverts addLocalUserTest changes in database by deleting them. Passes if
	 * all tests pass.
	 */
	@Test
	public void deleteUserLocalTest() {
		try {
			output.append("Started Delete Local User Test on " + getNow()
					+ "\n\n");
			database.setConnection(database.getConnection());
			for (int i = 1; i < TEST_USERS_COUNT; i++) {
				assertEquals(SUCCESS,
						database.deleteUserFromLocalBook(i, i + 1));
				output.append("User #" + i + " have deleted User #" + (i + 1)
						+ " from local address book\n");
			}

			String raw_query = "select * from address where user_id = ? and user_id_has = ?";
			try {
				output.append("Tests done for ");
				for (int i = 1; i < TEST_USERS_COUNT; i++) {
					PreparedStatement stmt = conn.prepareStatement(raw_query);
					stmt.setInt(1, i);
					stmt.setInt(2, i + 1);
					ResultSet rs = stmt.executeQuery();
					if (rs.next()) {
						fail();
					}
					output.append("User #" + i + " ");
				}
				output.append("\nAll tests passed on " + getNow());
				output.close();
			} catch (SQLException e) {
				System.out.println(e);
				fail();
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		clearDatabase();
	}

	/*
	 * i'th user blocks i+1'th user. Tests for each user. Passes if all tests
	 * pass for every user.
	 */
	@Test
	public void blockUserLocalTest() {
		try {
			output.append("Started Block User Test on " + getNow() + "\n\n");

			database.setConnection(database.getConnection());
			for (int i = 1; i < TEST_USERS_COUNT; i++) {
				assertEquals(SUCCESS, database.blockUserFromLocalBook(i, i + 1));
				output.append("User #" + i + " have blocked User #" + (i + 1)
						+ " \n");
			}

			String raw_query = "select * from block_list where user_id_by = ? and user_id_to = ?";
			try {
				int sID = -1, oID = -1;
				output.append("Tests done for ");
				for (int i = 1; i < TEST_USERS_COUNT; i++) {
					PreparedStatement stmt = conn.prepareStatement(raw_query);
					SUBJECT = i;
					OBJECT = i + 1;
					stmt.setInt(1, SUBJECT);
					stmt.setInt(2, OBJECT);
					ResultSet rs = stmt.executeQuery();
					if (rs.next()) {
						sID = rs.getInt("user_id_by");
						oID = rs.getInt("user_id_to");
					}
					assertEquals(sID, SUBJECT);
					assertEquals(oID, OBJECT);
					output.append("User #" + sID + " ");
				}
				output.append("\nAll tests passed on " + getNow());
				output.close();
			} catch (SQLException e) {
				System.out.println(e);
				fail();
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}

	/*
	 * Reverts blockUserLocalTest changes in database by unblocking(deleting)
	 * them. Passes if all tests pass.
	 */
	@Test
	public void unblockUserLocalTest() {
		try {
			output.append("Started Unblock User Test on " + getNow() + "\n\n");
			database.setConnection(database.getConnection());

			for (int i = 1; i < TEST_USERS_COUNT; i++) {
				assertEquals(SUCCESS,
						database.unblockUserFromLocalBook(i, i + 1));
				output.append("User #" + i + " have unblocked User #" + (i + 1)
						+ " \n");
			}

			String raw_query = "select * from block_list where user_id_by = ? and user_id_to = ?";
			try {
				output.append("Tests done for ");
				for (int i = 1; i < TEST_USERS_COUNT; i++) {
					PreparedStatement stmt = conn.prepareStatement(raw_query);
					stmt.setInt(1, i);
					stmt.setInt(2, i + 1);
					ResultSet rs = stmt.executeQuery();
					if (rs.next()) {
						fail();
					}
					output.append("User #" + i + " ");
				}
				output.append("\nAll tests passed on " + getNow());
				output.close();
			} catch (SQLException e) {
				System.out.println(e);
				fail();
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}

	public void createConversations() {
		database.setConnection(database.getConnection());
		for (int i = 1; i < TEST_USERS_COUNT; i++) {
			database.createConversation(i, i + 1, 1, null);
		}
	}

	@Test
	public void sendTextTest() {
		try {
			output.append("Started Send Text Message Test on " + getNow()
					+ "\n\n");
			createConversations();
			database.setConnection(database.getConnection());
			for (int i = 1; i < TEST_USERS_COUNT; i++) {
				assertEquals(SUCCESS_SEND_TEXT, database.sendText(i, i,
						(i + 1), SAMPLE_TEXT_MESSAGE, (SAMPLE_IP + i),
						(SAMPLE_IP + (i + 1)), database.getConnection()));
				output.append("User #" + i
						+ " have sent a text message to User #" + (i + 1)
						+ " \n");
			}
			output.append("\nAll tests passed on " + getNow());
			output.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}

	@Test
	public void sendFileTest() {
		try {
			output.append("Started Send File Message Test on " + getNow()
					+ "\n\n");
			int firstFile = createFileTestSet();
			database.setConnection(database.getConnection());

			for (int i = 1; i < TEST_USERS_COUNT; i++) {
				assertEquals(
						firstFile + i,
						database.sendFile(i, i, (i + 1), firstFile + i,
								(SAMPLE_IP + i), SAMPLE_IP + (i + 1),
								database.getConnection()).intValue());
				output.append("User #" + i + " have sent a file to User #"
						+ (i + 1) + " \n");
			}
			output.append("\nAll tests passed on " + getNow());
			output.close();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}

	private int createFileTestSet() {
		database.setConnection(database.getConnection());
		int lastfile = 0;
		for (int i = 1; i < TEST_USERS_COUNT; i++) {
			lastfile = database.insertFile(SAMPLE_FILE_NAME, false);
		}
		System.out.println("qweqeweqwqewqewqewqeweqw" + lastfile);
		return lastfile - TEST_USERS_COUNT + 1;
	}

	/**
	 * (Re)Establishes connection.
	 */
	private void openConnection() {
		try {
			conn = DriverManager.getConnection(DB_URL_SC, USER, PASS);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Clears database.
	 */
	private void clearDatabase() {
		try {
			conn = database.getConnection();
			Statement stmt = conn.createStatement();
			String raw_query = "delete from users where user_id != 1 ;";
			stmt.executeUpdate(raw_query);
			raw_query = "delete from address;";
			stmt.executeUpdate(raw_query);
			raw_query = "delete from block_list;";
			stmt.executeUpdate(raw_query);
			raw_query = "delete from conv_msg;";
			stmt.executeUpdate(raw_query);
			raw_query = "delete from conversations;";
			stmt.executeUpdate(raw_query);
			raw_query = "delete from file_repo;";
			stmt.executeUpdate(raw_query);
			raw_query = "delete from files;";
			stmt.executeUpdate(raw_query);
			raw_query = "delete from group_users;";
			stmt.executeUpdate(raw_query);
			raw_query = "delete from text_msg;";
			stmt.executeUpdate(raw_query);
			raw_query = "delete from user_activity;";
			stmt.executeUpdate(raw_query);
			stmt.executeUpdate(raw_query);
		} catch (SQLException e2) {
			e2.printStackTrace();
		}
	}

	private String getNow() {
		return new SimpleDateFormat("yyyy/MM/dd hh:mm:ss").format(new Date());
	}

	public static void main(String[] args) throws Exception {
		System.out.println("Starting Server."); //$NON-NLS-1$
		// JDBC driver name and database URL
		Connection conn = null;
		Class.forName("com.mysql.jdbc.Driver"); //$NON-NLS-1$

		// Open a connection
		System.out.println("Connecting to server..."); //$NON-NLS-1$

		conn = DriverManager.getConnection(DB_URL, USER, PASS);

		conn = DriverManager.getConnection(DB_URL_SC, USER, PASS);

		dataSource.setDriverClass(JDBC_DRIVER);
		dataSource.setJdbcUrl(DB_URL_SC);
		dataSource.setUser(USER);
		dataSource.setPassword(PASS);

		conn = dataSource.getConnection();

		database = new Database();
		database.setConnection(conn);
		database.createUser("Johannes Grohmann", "1414", //$NON-NLS-1$ //$NON-NLS-2$
				"sokpao@gmail.com", false); //$NON-NLS-1$
		database.createUser("Sokratis Papadopoulos", "1414", //$NON-NLS-1$ //$NON-NLS-2$
				"sokpao@gmail.com", false); //$NON-NLS-1$
		database.createUser("David Alarcon Prada", "1414", //$NON-NLS-1$ //$NON-NLS-2$
				"sokpao@gmail.com", false); //$NON-NLS-1$
		database.createUser("Gautam Vij", "1414", //$NON-NLS-1$ //$NON-NLS-2$
				"sokpao@gmail.com", false); //$NON-NLS-1$
		database.createUser("Akanksha Gupta", "1414", //$NON-NLS-1$ //$NON-NLS-2$
				"sokpao@gmail.com", false); //$NON-NLS-1$
		database.createUser("Jyoti ", "1414", //$NON-NLS-1$ //$NON-NLS-2$
				"sokpao@gmail.com", false); //$NON-NLS-1$
		database.createUser("Inanc Gurkan", "1414", //$NON-NLS-1$ //$NON-NLS-2$
				"sokpao@gmail.com", false); //$NON-NLS-1$
		database.createUser("Jibraan", "1414", //$NON-NLS-1$ //$NON-NLS-2$
				"sokpao@gmail.com", false); //$NON-NLS-1$
		database.createUser("Rohit", "1414", //$NON-NLS-1$ //$NON-NLS-2$
				"sokpao@gmail.com", false); //$NON-NLS-1$

		JUnitCore.main("se.bth.swatkats.letstalk.server.junit.DatabaseTest");
	}

	// Test Set
	private static final int TEST_USERS_COUNT = 10;
	private static TestUser[] testSet = new TestUser[TEST_USERS_COUNT];
	// Login Test
	private static final int WRONG_PASSWORD = -1;
	// Status Test
	private static final int AVAILABLE = 0;
	private static final int BUSY = 1;
	private static final int IDLE = 2;
	private static final int OFFLINE = 3;
	private static final int STATUS_TEST_COUNT = TEST_USERS_COUNT + 1;
	// Change Password Test
	private static final Integer SUCCESS_CPW = 1;
	private static final Integer AUTH_FAIL_CPW = -1;
	private static final int TEST_SUBJECT_COUNT = 3;
	// General
	private static final boolean SUCCESS = true;
	private static final String SAMPLE_WRONG_PWD = "Insert a wrong password here";
	private static final String SAMPLE_NEW_PWD = "New password";
	// Add/Remove-Block/Unblock
	private static int SUBJECT = 1;
	private static int OBJECT = 2;
	// Send Message
	private static final String SAMPLE_TEXT_MESSAGE = "Text Message";
	private static final String SAMPLE_FILE_NAME = "File Name";
	private static final String SAMPLE_IP = "192.168.1.";
	private static final int SUCCESS_SEND_TEXT = 1;
}
