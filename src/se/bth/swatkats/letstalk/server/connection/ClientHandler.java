package se.bth.swatkats.letstalk.server.connection;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Iterator;

import javax.crypto.SealedObject;

import se.bth.swatkats.letstalk.Constants;
import se.bth.swatkats.letstalk.connection.encryption.CryptModule;
import se.bth.swatkats.letstalk.connection.packet.DatabaseQuery;
import se.bth.swatkats.letstalk.connection.packet.LoginMessage;
import se.bth.swatkats.letstalk.connection.packet.NotificationChangeMessage;
import se.bth.swatkats.letstalk.connection.packet.Packet;
import se.bth.swatkats.letstalk.connection.packet.internal.CloseConnectionMessage;
import se.bth.swatkats.letstalk.connection.packet.internal.NotificationChangeScope;
import se.bth.swatkats.letstalk.connection.packet.internal.OpenConnectionMessage;
import se.bth.swatkats.letstalk.connection.packet.internal.QueryNotificationScope;
import se.bth.swatkats.letstalk.connection.packet.internal.QueryNotificationType;
import se.bth.swatkats.letstalk.connection.packet.message.FileMessage;
import se.bth.swatkats.letstalk.connection.packet.message.Message;
import se.bth.swatkats.letstalk.connection.packet.message.TextMessage;
import se.bth.swatkats.letstalk.server.mail.SendMail;
import se.bth.swatkats.letstalk.server.mailwithfile.SendMailWithFile;
import se.bth.swatkats.letstalk.user.User;
import se.bth.swatkats.letstalk.user.UserFactory;

/**
 * This class is responsible for reacting on different kinds of Message Objects
 * on the server side
 * 
 * @author JS
 *
 */
public class ClientHandler implements Runnable {

	private User myUser;

	private ObjectInputStream in;

	private ObjectOutputStream out;

	private CryptModule crypto;

	private String serverip;

	private Database database;

	private Timestamp login;

	public ClientHandler(ObjectInputStream in, ObjectOutputStream out,
			String serverip) {
		this.in = in;
		this.out = out;
		this.serverip = serverip;
		this.database = new Database();
	}

	/**
	 * @return the myUser
	 */
	public User getMyUser() {
		return myUser;
	}

	/**
	 * @param myUser
	 *            the myUser to set
	 */
	public void setMyUser(User myUser) {
		this.myUser = myUser;
	}

	@Override
	public void run() {
		initConnection();
		int counter = 0;
		while (true) {
			/* Create Message object and retrieve information */
			Packet message = null;
			try {
				message = receiveOnePacket();

			} catch (Exception e) {
				System.err
						.print("\nCaught Exception: " + e.getMessage() + "\n");
				counter++;
				// if more than 5 exception in one row occur -> shutdown
				// connection.
				if (counter > 5) {
					logOut();
					String user = (myUser != null) ? String.valueOf(myUser
							.getId()) : "null";
					System.err.print("\nShut down Client " + user
							+ ". Malfunctionous Connection.\n");
					break;
				}
				continue;
			}
			// reset counter;
			counter = 0;
			if (message instanceof CloseConnectionMessage) {
				sendToClient(message);
				logOut();
				break;
			}
			processMessage(message);

		}
	}

	private void initConnection() {
		Packet message = null;
		try {
			message = (Packet) in.readObject();
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (message instanceof OpenConnectionMessage) {
			OpenConnectionMessage m = (OpenConnectionMessage) message;
			crypto = new CryptModule();
			try {
				OpenConnectionMessage back = new OpenConnectionMessage(
						crypto.generateKeyPairFromClientKey(m.getKey()));
				out.writeObject(back);
			} catch (Exception e) {
				System.err.print("Key exchange failed.");
				e.printStackTrace();
			}

		} else {
			System.err.print("Need to initialize Connection first.");
		}
	}

	/**
	 * Process a packet, that has been received on the in-stream and react
	 * properly.
	 * 
	 * @param message
	 *            the received packet
	 */
	public void processMessage(Packet message) {
		if (message == null)
			return;
		if (message instanceof TextMessage) {
			TextMessage text = (TextMessage) message;
			System.out.printf("Received Text Message by %s for %s\n",
					text.getSenderid(), text.getReceiverid());
			System.out.println(text.getText());
			database.sendText(text.getConversationid(), text.getSenderid(),
					text.getReceiverid(), text.getText(), text.getSenderip(),
					null, null);
			ArrayList<User> receivers = database.usersFromGroup(
					text.getConversationid(), getMyUser().getId());
			for (Iterator<User> iterator = receivers.iterator(); iterator
					.hasNext();) {
				User user = (User) iterator.next();
				message.setReceiverid(user.getId());
				sendMessageObject(message);
			}

		} else if (message instanceof LoginMessage) {

			LoginMessage m = (LoginMessage) message;
			User user = database.authenticateLogin(
					((LoginMessage) message).getUsername(),
					((LoginMessage) message).getPw(), database.getConnection());
			int id = user.getId();
			database.closeConnection();
			// unnecesary, just for checks
			m.setReceiverid(id);
			m.setUser(user);
			if (id > 0) {
				logIn(id);
			}
			sendToClient(m);
		} else if (message instanceof DatabaseQuery) {
			executeDatabaseQuery((DatabaseQuery) message);
		} else if (message instanceof FileMessage) {
			FileMessage m = (FileMessage) message;
			database.sendFile(m.getConversationid(), m.getSenderid(),
					m.getReceiverid(), m.getFileid(), m.getSenderip(), null,
					database.getConnection());
			database.closeConnection();
			sendMessageObject(message);
		} else {
			System.err.print("Error. Message type " + message.getClass()
					+ "not supported.");
		}
	}

	private void executeDatabaseQuery(DatabaseQuery message) {
		message.setResult(database.executeFunction(message.getMethod(),
				message.getParams()));
		sendToClient(message);
		// notifications
		notifyScope(message.getNotificationscope());
	}

	/**
	 * This method is used to send to the connected client of this handler
	 * object.
	 * 
	 * @param message
	 *            the message to send
	 */
	public void sendToClient(Packet message) {
		message.setSenderip(serverip);
		if (message instanceof Message) {
			((Message) message).setSenderid(UserFactory.getClientById(
					Constants.SERVERID).getId());
		}
		forwardToClient(message);
	}

	/**
	 * This method is used to forward to the connected client of this handler
	 * object. Sender-id remains unchanged so it is usually used to forward
	 * messages from other clients.
	 * 
	 * @param message
	 *            the message to send
	 */
	public void forwardToClient(Packet message) {
		try {
			out.writeObject(crypto.encrypt(message));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void sendMessageObject(Packet message) {
		ClientHandler receiver = Main.getOnlineUsers().get(
				message.getReceiverid());
		if (receiver != null) {
			// client is online
			receiver.forwardToClient(message);
		} else if (database.idToEmail(message.getReceiverid(),
				database.getConnection()) != "0") {
			// client is offline
			sendEmail(message);
		} else {
			// client doesn't exist.
			System.err.print("Client doesn't exist.");
		}
	}

	private void sendEmail(Packet message) {
		// client is offline
		// receiver.forwardToClient(message);
		if (message instanceof TextMessage) {
			// if message is a text message
			TextMessage text = (TextMessage) message;
			SendMail mailobject = new SendMail(database.idToName(
					text.getSenderid(), null), database.idToEmail(
					text.getReceiverid(), null), text.getText());
			mailobject.mailToOfflineUser();
		} else if (message instanceof FileMessage) {
			// if message is a file message
			FileMessage m = (FileMessage) message;
			String extension = getFileExtension(database.getFilename(
					m.getFileid(), null));
			String FilePath = m.getFileid() + "." + extension; // Should be
																// changed
																// according to
																// server
			SendMailWithFile mailwithfileobject = new SendMailWithFile(
					database.idToName(m.getSenderid(), null),
					database.idToEmail(m.getReceiverid(), null), FilePath,
					database.getFilename(m.getFileid(), null));
			mailwithfileobject.mailFileToOfflineUser();
		} else {
			// Message type not supported.
			System.err
					.print("Error dialogue is in ClientHandler.java (in SendMessageObject function)...Message type is not supported.");
		}
	}

	private static String getFileExtension(String fileName) {
		// Function to extract the extension
		if (fileName.lastIndexOf(".") != -1 && fileName.lastIndexOf(".") != 0)
			return fileName.substring(fileName.lastIndexOf(".") + 1);
		else
			return "";
	}

	private Packet receiveOnePacket() throws RuntimeException {
		SealedObject message = null;
		try {
			message = (SealedObject) in.readObject();
		} catch (ClassNotFoundException | IOException e) {

			e.printStackTrace();
		}
		try {
			return ((Packet) crypto.decrypt(message));
		} catch (Exception e) {
			throw new RuntimeException("Did not receive a packet.", e);
		}
	}

	private void logIn(int id) {
		// register user as online
		if (Main.getOnlineUsers().containsKey(id)) {
			System.err.printf("User-id %d already online.", id);
		}
		Main.getOnlineUsers().put(id, this);
		login = new Timestamp(System.currentTimeMillis());
		setMyUser(new User());
		getMyUser().setId(id);
		notifyScope(new NotificationChangeScope(
				QueryNotificationScope.LOCALADDRESSBOOK,
				QueryNotificationType.CONVERSATIONHISTORIES));
	}

	private void logOut() {
		// register user as offline
		if (getMyUser() == null)
			return;
		System.out.printf("Client %s disconnected.\n", getMyUser().getId());
		Main.getOnlineUsers().remove(getMyUser().getId());
		Timestamp logout = new Timestamp(System.currentTimeMillis());
		database.markLogOut(myUser.getId(), login, logout);
		// open connection and set offline
		database.setConnection(database.getConnection());
		database.setUserStatusOffline(myUser.getId());
		database.closeConnection();

		notifyScope(new NotificationChangeScope(
				QueryNotificationScope.LOCALADDRESSBOOK,
				QueryNotificationType.CONVERSATIONHISTORIES));
	}

	private void notifyScope(NotificationChangeScope scope) {
		// notifications
		switch (scope.getScope()) {
		case NOONE:
			// nothing
			break;
		case LOCALADDRESSBOOK:
			// inform local address book
			ArrayList<User> notifiers = database
					.getLocalAddressBook(getMyUser().getId());
			for (Iterator<User> iterator = notifiers.iterator(); iterator
					.hasNext();) {
				User user = (User) iterator.next();
				NotificationChangeMessage notification = new NotificationChangeMessage(
						user.getId(), scope);
				ClientHandler receiver = Main.getOnlineUsers()
						.get(user.getId());
				if (receiver != null)
					receiver.sendToClient(notification);
			}
			break;
		case SINGLEUSER:
			int id = scope.getId();
			NotificationChangeMessage notification = new NotificationChangeMessage(
					id, scope);
			ClientHandler receiver = Main.getOnlineUsers().get(id);
			if (receiver != null)
				receiver.sendToClient(notification);
			break;
		default:
			// should not occurr
			System.err.print("Unknown Notification Level" + scope.getScope());
			break;
		}
	}

}
