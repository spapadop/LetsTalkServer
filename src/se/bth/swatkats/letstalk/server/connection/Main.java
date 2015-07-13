package se.bth.swatkats.letstalk.server.connection;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Reader;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;

import se.bth.swatkats.letstalk.Constants;
import se.bth.swatkats.letstalk.filedownload.download;
import se.bth.swatkats.letstalk.fileupload.upload;

import com.mchange.v2.c3p0.ComboPooledDataSource;

/**
 * The main class to start the Server functionality.
 * 
 * @author Johannes Grohmann and Gautam Vij
 *
 */
public class Main implements Runnable {

	private static HashMap<Integer, ClientHandler> onlineUsers;

	private static Database database;
	private static ComboPooledDataSource dataSource = new ComboPooledDataSource();

	static final String JDBC_DRIVER = MessageBundle
			.getString("Database.JDBC_DRIVER"); //$NON-NLS-1$
	static final String DB_URL = MessageBundle.getString("Database.DB_URL"); //$NON-NLS-1$
	static final String DB_URL_SC = MessageBundle
			.getString("Database.DB_URL_SC"); //$NON-NLS-1$
	static final String USER = MessageBundle.getString("Database.USER"); //$NON-NLS-1$
	static final String PASS = MessageBundle.getString("Database.PASS"); //$NON-NLS-1$
	static final String SQL_SCRIPT = MessageBundle
			.getString("Database.SQL_SCRIPT"); //$NON-NLS-1$

	public static void main(String[] args) {
		System.out.println("Starting Server."); //$NON-NLS-1$
		// JDBC driver name and database URL
		Connection conn = null;
		try {
			Class.forName("com.mysql.jdbc.Driver"); //$NON-NLS-1$

			// Open a connection
			System.out.println("Connecting to server..."); //$NON-NLS-1$

			conn = DriverManager.getConnection(DB_URL, USER, PASS);
			ScriptRunner sr = new ScriptRunner(conn, false, false);
			System.out.println("Creating database..."); //$NON-NLS-1$
			Reader reader = new BufferedReader(new FileReader(SQL_SCRIPT));
			sr.runScript(reader);
			System.out.println(" Database Constructed "); //$NON-NLS-1$

			// passing conn descriptor in the database connection object
			conn = DriverManager.getConnection(DB_URL_SC, USER, PASS);

			System.out.println(" Shifting to new database "); //$NON-NLS-1$
			dataSource.setDriverClass(JDBC_DRIVER);
			dataSource.setJdbcUrl(DB_URL_SC);
			dataSource.setUser(USER);
			dataSource.setPassword(PASS);

			conn = dataSource.getConnection();

			database = new Database();
			database.setConnection(conn);
			System.out.println(" Database Successful "); //$NON-NLS-1$
			
			database.createUser("admin", "admin", //$NON-NLS-1$ //$NON-NLS-2$
					"admin@swatkats.com", true); //$NON-NLS-1$

			// System.out.println(database.authenticateLogin("g", "g_p"));
			//
			// System.out.println(database.authenticateLogin("g1", "g1_p"));
			// System.out.println( database.changeUserPassword(2,"g3","g3_p",
			// "love uh") );
			// database.deleteuser(1);
			// first argument: userid in whose phonebook contact to be added
			// second argument: userid of whose contact to be added
			// database.addLocalUser(2, 3);
			// database.addLocalUser(2, 4);
			// database.addLocalUser(2, 5);
			// database.addLocalUser(3, 2);
			// database.addLocalUser(4, 5);
			// database.blockUserFromLocalBook(1, 3);
			// database.addLocalUser(5, 1);
			// database.addLocalUser(3, 2);
			// System.out.println("creating conversations ");
			// System.out.println(database.createConversation(2, 3, 1,""));
			// System.out.println(database.createConversation(3, 2, 1,""));
			// System.out.println(database.createConversation(1, 5, 1,""));
			// database.sendText(1, 1, 5, "dfd", "user_s_ip", "user_r_ip",conn);
			// database.sendText(1, 5, 1, "wfhwekfdfd", "user_s_ip",
			// "user_r_ip");
			//
			// System.out.println(database.createConversation(2, 1, 1,""));
			// System.out.println(database.createConversation(1, 3, 1,""));
			// System.out.println(database.createConversation(3, 1, 1,""));
			// System.out.println("sending messages");
			// System.out.println(database.sendText(3, 3, 1, "works",
			// "sender_ip", "receiver ip"));
			// System.out.println(database.sendText(3, 1, 3, "dfdf",
			// "sender_ip", "receiver ip"));
			// System.out.println("fetching localusers for user 1");
			// ArrayList<User> try2 = database.searchLocalUsers(1, "");
			// for(User u : try2){
			// System.out.print(u.getId() + " ");
			// System.out.print("dwedwe");
			// }
			//
			// ArrayList<Conversation> try3 =
			// database.fetchConversationsForUser(1);
			//			System.out.println("printing the chat converasations for user 1"); //$NON-NLS-1$
			// for(Conversation u : try3){
			//				System.out.print("this is the id "); //$NON-NLS-1$
			// System.out.print(u.getId() );
			//				System.out.print("this is the name of the conversation "); //$NON-NLS-1$
			// System.out.println(u.getName());
			// //System.out.println(u.getUsername());
			// }
			// ArrayList<Conversation> try4 =
			// database.fetchConversationsForUser(5);
			//			System.out.println("printing the chat converasations for user 5"); //$NON-NLS-1$
			// for(Conversation u : try4){
			//				System.out.print("this is the id "); //$NON-NLS-1$
			// System.out.print(u.getId() );
			//				System.out.print("this is the name of the conversation "); //$NON-NLS-1$
			// System.out.println(u.getName());
			// //System.out.println(u.getUsername());
			// }
			// ArrayList<TextMessage> try2 =
			// database.fetchTextConversationHistory(1, 1);
			// //System.out.println(try2.size());
			//			System.out.println("printing the chat converasation of chat 1 of user 1"); //$NON-NLS-1$
			// for(TextMessage u : try2){
			//				System.out.print(u.getSenderid() + " "); //$NON-NLS-1$
			// System.out.println(u.getText());
			// //System.out.println(u.getUsername());
			// //
			// }
			//
			// ArrayList<TextMessage> try1 =
			// database.fetchTextConversationHistory(1, 5);
			//			System.out.println("printing the chat converasation of chat 1 of user 5"); //$NON-NLS-1$
			// for(TextMessage u : try1){
			//				System.out.print(u.getSenderid() + " "); //$NON-NLS-1$
			// System.out.println(u.getText());
			// System.out.println(u.getUsername());
			//
			// }
			//
			// File folder = new
			// File("C:\\Users\\Sokratis\\Desktop\\GitLab\\SWAT-Kats\\src\\LetsTalkServer\\repo");
			// File[] listOfFiles = folder.listFiles();
			//
			// for (int i = 0; i < listOfFiles.length; i++) {
			// if (listOfFiles[i].isFile()) {
			// System.out.println("File " + listOfFiles[i].getName());
			// database.insertFile(listOfFiles[i].getAbsolutePath(), true);
			// } else if (listOfFiles[i].isDirectory()) {
			// System.out.println("Found a directory.");
			// }
			// }

			// stmt.close();
			conn.close();
			database.setConnection(null);
		} catch (SQLException se) {
			// Handle errors for JDBC
			se.printStackTrace();
		} catch (Exception e) {
			// Handle errors for Class.forName
			e.printStackTrace();
		} /*
		 * finally { // finally block used to close resources try { if (stmt !=
		 * null) stmt.close(); } catch (SQLException se2) { }// nothin g we can
		 * do try { if (conn != null) conn.close(); } catch (SQLException se) {
		 * se.printStackTrace(); }// end finally try
		 * 
		 * }// end try
		 */

		// run upload and download listener
		new download().start();
		new upload().start();
		new se.bth.swatkats.letstalk.filerepo.download().start();
		new se.bth.swatkats.letstalk.filerepo.upload().start();

		Main main = new Main();
		Thread t = new Thread(main);
		t.start();
	}

	@Override
	public void run() {

		try {
			onlineUsers = new HashMap<Integer, ClientHandler>();
			@SuppressWarnings("resource")
			ServerSocket welcomeSocket = new ServerSocket(Constants.SERVERPORT);
			System.out.println("Server established. Waiting for Clients..."); //$NON-NLS-1$

			while (true) {
				try {
					// Create the Client Socket
					Socket clientSocket = welcomeSocket.accept();
					System.out.println("Socket Extablished..."); //$NON-NLS-1$
					// Create input and output streams to client
					ObjectOutputStream outToClient = new ObjectOutputStream(
							clientSocket.getOutputStream());
					ObjectInputStream inFromClient = new ObjectInputStream(
							clientSocket.getInputStream());

					// give information to a (parallel) handler
					ClientHandler processor = new ClientHandler(inFromClient,
							outToClient, welcomeSocket.getInetAddress()
									.getHostAddress());
					Thread t = new Thread(processor);
					t.start();

				} catch (Exception e) {
					System.err.println("Server Error: " + e.getMessage()); //$NON-NLS-1$
					System.err.println("Localized: " + e.getLocalizedMessage()); //$NON-NLS-1$
					System.err.println("Stack Trace: " + e.getStackTrace()); //$NON-NLS-1$
					System.err.println("To String: " + e.toString()); //$NON-NLS-1$
					System.err.println("Stacktrace: "); //$NON-NLS-1$
					e.printStackTrace();
				}

			}

		} catch (Exception e) {
			System.err.println("Could not create a server."); //$NON-NLS-1$
			e.printStackTrace();
		}
	}

	/**
	 * A Hashmap of the currently online Userids, and their corresponding
	 * handlers
	 * 
	 * @return onlineUsers
	 */
	public static HashMap<Integer, ClientHandler> getOnlineUsers() {
		return onlineUsers;
	}

}
