package se.bth.swatkats.letstalk.server.connection;

import java.beans.PropertyVetoException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Hashtable;

import se.bth.swatkats.letstalk.connection.IDatabase;
import se.bth.swatkats.letstalk.connection.packet.FileRepo;
import se.bth.swatkats.letstalk.connection.packet.UserActivity;
import se.bth.swatkats.letstalk.connection.packet.message.FileMessage;
import se.bth.swatkats.letstalk.connection.packet.message.TextMessage;
import se.bth.swatkats.letstalk.gui.Conversation;
import se.bth.swatkats.letstalk.statistics.TopFileChats;
import se.bth.swatkats.letstalk.statistics.TopTextChats;
import se.bth.swatkats.letstalk.user.User;

import com.mchange.v2.c3p0.ComboPooledDataSource;

;
/**
 * All database methods
 * 
 * @author Gautam Vij, Inanc Gurkan and Jibraan Singh Chahal
 *
 */
public class Database implements IDatabase {

	private Connection conn;
	private ComboPooledDataSource dataSource = new ComboPooledDataSource();

	/*
	 * Creating a new instance requires a database connection.
	 */

	@SuppressWarnings("unchecked")
	public synchronized Object executeFunction(String name, Object... params) {
		Object result = null;
		try {
			conn = dataSource.getConnection();
		} catch (SQLException e1) {
			e1.printStackTrace();
		}
		Class<? extends Object>[] paratypes = null;
		Method method = null;
		if (params != null) {
			paratypes = new Class[params.length];
			for (int i = 0; i < params.length; i++) {
				paratypes[i] = params[i].getClass();
			}
			// Class<? extends Object> para = params.getClass();
		}
		try {
			method = Database.class.getMethod(name, paratypes);
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		} catch (SecurityException e) {
			e.printStackTrace();
		}

		try {
			result = method.invoke(this, params);
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (InvocationTargetException e) {
			e.printStackTrace();
		}

		try {
			conn.close();
			conn = null;
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return result;
	}

	public Database() {

		try {
			dataSource.setDriverClass(Main.JDBC_DRIVER);
		} catch (PropertyVetoException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		dataSource.setJdbcUrl(Main.DB_URL_SC);
		dataSource.setUser(Main.USER);
		dataSource.setPassword(Main.PASS);

	}

	public void setConnection(Connection conn) {
		this.conn = conn;
	}

	public Connection getConnection() {
		try {
			this.conn = dataSource.getConnection();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return conn;
	}

	public void closeConnection() {
		try {
			this.conn.close();
			this.conn = null;
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Authenticates login.
	 * 
	 * @param username
	 * @param password
	 * @return user_id ,if authenticated; -1, if username exists but password is
	 *         wrong; 0, if authentication didn't succeed; -2, if an SQL
	 *         Exception caught
	 */
	public User authenticateLogin(String username, String password,
			Connection fconn) {

		int flag = 0;
		if (fconn == null) {
			try {
				flag = 1;
				fconn = dataSource.getConnection();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			setConnection(fconn);
		}

		String raw_query = "select pwd from users where username = ?";
		try {

			PreparedStatement stmt = fconn.prepareStatement(raw_query);
			stmt.setString(1, username);
			ResultSet rs = stmt.executeQuery();
			if (rs.next()) {
				if (rs.getString("pwd").equals(password)) {
					raw_query = "select * from users where username = ? and pwd = ?";
					stmt = fconn.prepareStatement(raw_query);
					stmt.setString(1, username);
					stmt.setString(2, password);
					rs = stmt.executeQuery();
					while (rs.next()) {
						int user_id = rs.getInt("user_id");
						String name = rs.getString("username");
						String email = rs.getString("email");
						int admin_flag = rs.getInt("admin_flag");
						int status = rs.getInt("status");
						this.setUserStatusAvailable(user_id);
						if (flag == 1) {
							fconn.close();
						}

						// this.setUserStatusAvailable(user_id);
						return new User(user_id, name, email, status,
								admin_flag);

					}
				} else
				// username exists but wrong password
				if (flag == 1) {
					fconn.close();
				}

				return new User(-1);

			}
			// If authentication did not succeed.
			if (flag == 1) {
				fconn.close();
			}
			return new User(0);

		} catch (SQLException e) {
			System.out.println(e);
			// something went wrong
			try {
				if (flag == 1) {
					fconn.close();
				}
			} catch (SQLException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			return new User(-2);

		}

	}

	@Override
	public ArrayList<User> usersFromGroup(Integer conv_id, Integer id) {
		int flag =0 ;
		int chat_type;
		try {
			if(conn == null){
				flag =1 ;
				conn = this.getConnection();
			}
			String raw_query = "select chat_type from conversations where conv_id = ?";  
			PreparedStatement stmt = conn.prepareStatement(raw_query);
			stmt.setInt(1,conv_id);
			ResultSet rs = stmt.executeQuery();
			if(rs.next()){
				chat_type = rs.getInt("chat_type");
			}
			else
				return null;
			if(chat_type == 0){				
				raw_query = " Select U.user_id , U.username,  U.email, U.admin_flag, U.status from users U, group_users G "
						+ " where "
						+ " G.conv_id  = ? "
						+ " and "
						+ " G.user_id = U.user_id ; ";
				stmt = conn.prepareStatement(raw_query,
						Statement.RETURN_GENERATED_KEYS);
				stmt.setInt(1, conv_id);
				rs = stmt.executeQuery();
				ArrayList<User> userlist = new ArrayList<User>();
	
				while (rs.next()) {
					int user_id = rs.getInt("user_id");
					String name = rs.getString("username");
					String email = rs.getString("email");
					int admin_flag = rs.getInt("admin_flag");
					int status = rs.getInt("status");
					if(id!=user_id){
						userlist.add(new User(user_id, name, email, status, admin_flag));
					}
				}
				stmt.close();
				System.out.println(userlist.size());
				if(flag==1){
					conn.close();
				}
				return userlist;
			}
			else{
				raw_query = " select U.user_id, U.username, U.email, U.status, U.admin_flag "
						+ " from "
						+ " conversations C, users U "
						+ " where"
						+ " C.conv_id = ?"
						+ " and "
						+ " case "
						+ " when C.user_one = ? then U.user_id = C.user_two "
						+ " when C.user_two = ? then U.user_id = C.user_one end ; ";
				stmt = conn.prepareStatement(raw_query);
				stmt.setInt(1, conv_id);
				stmt.setInt(2, id);
				stmt.setInt(3, id);
				rs = stmt.executeQuery();
				ArrayList<User> userlist = new ArrayList<User>();
	
				while (rs.next()) {
					int user_id = rs.getInt("user_id");
					String name = rs.getString("username");
					String email = rs.getString("email");
					int status = rs.getInt("status");
					int admin_flag = rs.getInt("admin_flag");
					userlist.add(new User(user_id, name, email, status, admin_flag));
				}
				stmt.close();
				System.out.println(userlist.size());
				if(flag==1){
					conn.close();
				}
				return userlist;
	
			}
		} catch (SQLException e) {
			System.out.println(e);
			e.printStackTrace();
			if(flag==1){
				try {
					conn.close();
				} catch (SQLException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
			return null;
		}
	}

	public Integer changeUserPassword(Integer user_id, String username,
			String oldPass, String newPass) {

		if (this.authenticateLogin(username, oldPass, conn).getId() <= 0)
			return -1;
		else {
			String raw_query = "update users set pwd = ? where user_id = ?";
			try {
				conn = dataSource.getConnection();
				PreparedStatement stmt = conn.prepareStatement(raw_query,
						Statement.RETURN_GENERATED_KEYS);
				stmt.setString(1, newPass);
				stmt.setInt(2, user_id);
				stmt.executeUpdate();
				return 1;
			} catch (SQLException e) {
				System.out.println(e);
				e.printStackTrace();
				return 0;
			}
		}

	}
	
	@SuppressWarnings("unchecked")
	public ArrayList<User> getLocalAddressBook(Integer userid){
		return (ArrayList<User>) executeFunction("searchLocalUsers", userid, "");
	}

	/**
	 * Author : Akanksha Gupta Returns email id for a respective user id.
	 * 
	 * @param user_id
	 * @return email, if user exists; "0", if user doesn't exist;
	 */
	public String idToEmail(int user_id, Connection fconn) {

		if (fconn == null) {
			try {
				fconn = dataSource.getConnection();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		String raw_query = "select email from users where user_id = ?";
		String user_mailid = "0";
		try {
			PreparedStatement stmt = fconn.prepareStatement(raw_query);
			stmt.setInt(1, user_id);
			ResultSet rs = stmt.executeQuery();
			if (rs.next()) {
				user_mailid = rs.getString("email");
				try {
					fconn.close();
				} catch (SQLException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				return user_mailid;
			}
			try {
				fconn.close();
			} catch (SQLException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			return user_mailid;

		} catch (SQLException e) {
			System.out.println(e);
			try {
				fconn.close();
			} catch (SQLException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

			return user_mailid;
		}
	}

	/**
	 * Author : Akanksha Gupta Returns name for a respective user id.
	 * 
	 * @param user_id
	 * @return username, if user exists; "0", if user doesn't exist;
	 */
	public String idToName(int user_id, Connection fconn) {
		if (fconn == null) {
			try {
				fconn = dataSource.getConnection();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		String raw_query = "select username from users where user_id = ?";
		String user_name = "0";
		try {
			PreparedStatement stmt = fconn.prepareStatement(raw_query);
			stmt.setInt(1, user_id);
			ResultSet rs = stmt.executeQuery();
			if (rs.next()) {
				user_name = rs.getString("username");
				try {
					fconn.close();
				} catch (SQLException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				return user_name;

			}
			try {
				fconn.close();
			} catch (SQLException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			return user_name;

		} catch (SQLException e) {
			System.out.println(e);
			try {
				fconn.close();
			} catch (SQLException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			return user_name;
		}
	}
	

	@Override
	public ArrayList<TopTextChats> fetchTopTextConversationsOto() {
		String raw_query = "select conv_id, user_id_s, user_id_r, count(*) as text_count "
				+ " from "
				+ " conv_msg "
				+ " where (msg_type in ('text') and user_id_r != -10) "
				+ " group by conv_id order by text_count DESC LIMIT 10; ";
		try {
			PreparedStatement stmt = conn.prepareStatement(raw_query);
			ResultSet rs = stmt.executeQuery();
			ArrayList<TopTextChats> countList = new ArrayList<TopTextChats>();

			while (rs.next()) {
				Integer conv_id = rs.getInt("conv_id");
				Integer userOne = rs.getInt("user_id_s");
				Integer userTwo = rs.getInt("user_id_r");
				Integer textCount = rs.getInt("text_count");
				countList.add(new TopTextChats(conv_id, userOne, userTwo, null,
						textCount));
			}
			return countList;

		} catch (SQLException e) {
			System.out.println(e);
			e.printStackTrace();
			return null;
		}

	}
	@Override
	public ArrayList<TopTextChats> fetchTopTextConversationsGroup() {
		String raw_query = " select G.conv_id, G.g_name, C.user_id_r, count(*) as text_count "
				+ " from "
				+ " group_users G, conv_msg C "
				+ " where "
				+ " ("
				+ " C.user_id_s=G.user_id "
				+ " and G.conv_id=C.conv_id "
				+ " and C.msg_type in ('text')) "
				+ " group by G.conv_id "
				+ " order by text_count DESC LIMIT 10;";
		try {
			PreparedStatement stmt = conn.prepareStatement(raw_query);
			ResultSet rs = stmt.executeQuery();
			ArrayList<TopTextChats> countList = new ArrayList<TopTextChats>();
			
			while (rs.next()) {
				Integer conv_id = rs.getInt("conv_id");
				String groupName = rs.getString("g_name");
				Integer userTwo = rs.getInt("user_id_r");
				Integer textCount = rs.getInt("text_count");
				countList.add(new TopTextChats(conv_id, null, userTwo,
						groupName, textCount));
			}
			return countList;

		} catch (SQLException e) {
			System.out.println(e);
			e.printStackTrace();
			return null;
		}

	}

	@Override
	public ArrayList<TopFileChats> fetchTopFileConversationsOto(){
		String raw_query = "select conv_id, user_id_s, user_id_r, count(*) as file_count "
				+ " from "
				+ " conv_msg "
				+ " where (msg_type in ('file') and user_id_r != -10) "
				+ " group by conv_id order by file_count DESC LIMIT 10; ";
		try {
			PreparedStatement stmt = conn.prepareStatement(raw_query);
			ResultSet rs = stmt.executeQuery();
			ArrayList<TopFileChats> countList = new ArrayList<TopFileChats>();

			while (rs.next()) {
				Integer conv_id = rs.getInt("conv_id");
				Integer userOne = rs.getInt("user_id_s");
				Integer userTwo = rs.getInt("user_id_r");
				Integer fileCount = rs.getInt("file_count");
				countList.add(new TopFileChats(conv_id, userOne, userTwo, null,
						fileCount));
			}
			return countList;

		} catch (SQLException e) {
			System.out.println(e);
			e.printStackTrace();
			return null;
		}
	}
	@Override
	public ArrayList<TopFileChats> fetchTopFileConversationsGroup(){
		String raw_query = " select G.conv_id, G.g_name, C.user_id_r, count(*) as file_count "
				+ " from "
				+ " group_users G, conv_msg C "
				+ " where "
				+ " ("
				+ " C.user_id_s=G.user_id "
				+ " and G.conv_id=C.conv_id "
				+ " and C.msg_type in ('file')) "
				+ " group by G.conv_id "
				+ " order by file_count DESC LIMIT 10;";
		try {
			PreparedStatement stmt = conn.prepareStatement(raw_query);
			ResultSet rs = stmt.executeQuery();
			ArrayList<TopFileChats> countList = new ArrayList<TopFileChats>();

			while (rs.next()) {
				Integer conv_id = rs.getInt("conv_id");
				String groupName = rs.getString("g_name");
				Integer userTwo = rs.getInt("user_id_r");
				Integer fileCount = rs.getInt("file_count");
				countList.add(new TopFileChats(conv_id, null, userTwo,
						groupName, fileCount));
			}
			return countList;

		} catch (SQLException e) {
			System.out.println(e);
			e.printStackTrace();
			return null;
		}
	}
	
	@Override
	public ArrayList<TopTextChats> fetchTopTextChatsForUserOto(Integer user_id){
		String raw_query = "select conv_id, "
				+ " (case when user_id_s=? then user_id_r when user_id_r=? then user_id_s end) as chat_with,"
				+ " count(*) as text_count " + " from conv_msg " + " where "
				+ " msg_type in ('text') " + " group by chat_with; ";
		try {
			PreparedStatement stmt = conn.prepareStatement(raw_query);
			stmt.setInt(1, user_id);
			stmt.setInt(2, user_id);
			ResultSet rs = stmt.executeQuery();
			ArrayList<TopTextChats> countList = new ArrayList<TopTextChats>();

			while (rs.next()) {
				Integer conv_id = rs.getInt("conv_id");
				Integer userOne = rs.getInt("chat_with");
				// Integer userTwo = rs.getInt("user_id_r");
				Integer textCount = rs.getInt("text_count");
				if (userOne != null && userOne != -10 && userOne!= 0) {
					countList.add(new TopTextChats(conv_id, userOne, null,
							null, textCount));
				}

			}return countList;
		}
		catch (SQLException e) {
			System.out.println(e);
			e.printStackTrace();
			return null;
		}
	}
	@Override
	public ArrayList<TopTextChats> fetchTopTextChatsForUserGroup(Integer user_id){
		String raw_query = " select G.conv_id, G.g_name, count(*) as text_count "
				+ "	from group_users G, conv_msg C "
				+ "	where "
				+ "	(C.msg_type in ('text') and G.conv_id=C.conv_id and G.user_id=?) "
				+ "	group by g_name ; ";
		try {
			PreparedStatement stmt = conn.prepareStatement(raw_query);
			stmt.setInt(1, user_id);
			ResultSet rs = stmt.executeQuery();
			ArrayList<TopTextChats> countList = new ArrayList<TopTextChats>();

			while (rs.next()) {
				Integer conv_id = rs.getInt("conv_id");
				String groupName = rs.getString("g_name");
				// Integer userTwo = rs.getInt("user_id_r");
				Integer textCount = rs.getInt("text_count");
				countList.add(new TopTextChats(conv_id, -10, null, groupName,
						textCount));
			}return countList;
		}
		catch (SQLException e) {
			System.out.println(e);
			e.printStackTrace();
			return null;
		}
	}
	
	@Override
	public ArrayList<TopFileChats> fetchTopFileChatsForUserOto(Integer user_id){
		String raw_query = "select conv_id, "
				+ " (case when user_id_s=? then user_id_r when user_id_r=? then user_id_s end) as chat_with,"
				+ " count(*) as file_count " + " from conv_msg " + " where "
				+ " msg_type in ('file') " + " group by chat_with; ";
		try {
			PreparedStatement stmt = conn.prepareStatement(raw_query);
			stmt.setInt(1, user_id);
			stmt.setInt(2, user_id);
			ResultSet rs = stmt.executeQuery();
			ArrayList<TopFileChats> countList = new ArrayList<TopFileChats>();
			while (rs.next()) {
				Integer conv_id = rs.getInt("conv_id");
				Integer userOne = rs.getInt("chat_with");
				// Integer userTwo = rs.getInt("user_id_r");
				Integer fileCount = rs.getInt("file_count");
				if (userOne != null && userOne != -10 && userOne!= 0) {
					countList.add(new TopFileChats(conv_id, userOne, null,
							null, fileCount));
				}

			}
			return countList;

		} catch (SQLException e) {
			System.out.println(e);
			e.printStackTrace();
			return null;
		}
	}
	
	@Override
	public ArrayList<TopFileChats> fetchTopFileChatsForUserGroup(Integer user_id){
		String raw_query = " select G.conv_id, G.g_name, count(*) as file_count "
				+ "	from group_users G, conv_msg C "
				+ "	where "
				+ "	(C.msg_type in ('file') and G.conv_id=C.conv_id and G.user_id=?) "
				+ "	group by g_name ; ";
		
		try {
			PreparedStatement stmt = conn.prepareStatement(raw_query);
			stmt.setInt(1, user_id);
			ResultSet rs = stmt.executeQuery();
			ArrayList<TopFileChats> countList = new ArrayList<TopFileChats>();
			while (rs.next()) {
				Integer conv_id = rs.getInt("conv_id");
				String groupName = rs.getString("g_name");
				// Integer userTwo = rs.getInt("user_id_r");
				Integer fileCount = rs.getInt("file_count");
				countList.add(new TopFileChats(conv_id, -10, null, groupName,
						fileCount));
			}
			return countList;

		} catch (SQLException e) {
			System.out.println(e);
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public Integer createUser(String username, String password, String email,
			Boolean isAdmin) {
		int admin_flag = 0;
		if (isAdmin)
			admin_flag = 1;
		String raw_query = "select * from users where username = ?";
		try {
			PreparedStatement stmt = conn.prepareStatement(raw_query,
					Statement.RETURN_GENERATED_KEYS);
			stmt.setString(1, username);
			ResultSet rs = stmt.executeQuery();
			while (rs.next()) {
				return -1;
				// username already exists
			}
		} catch (SQLException e) {
			System.out.println(e);
			return 0;
		}
		raw_query = "insert into users(user_id,username,pwd,email,admin_flag,status) values(null,?,?,?,?,3)";

		try {
			PreparedStatement stmt = conn.prepareStatement(raw_query,
					Statement.RETURN_GENERATED_KEYS);

			stmt.setString(1, username);
			stmt.setString(2, password);
			stmt.setString(3, email);
			stmt.setInt(4, admin_flag);

			stmt.executeUpdate();
			ResultSet rs = stmt.getGeneratedKeys();
			if (rs.next()) {
				int userid = rs.getInt(1);
				if (1 != userid) {
					this.addLocalUser(userid, 1);
				}
				return rs.getInt(1);
			}
		} catch (SQLException e) {
			System.out.println(e);
			return 0;
			// return null;
		}
		return 0;
	}

	public Boolean deleteUser(Integer user_id) {
		String raw_query = "delete from users where user_id = ?";
		try {
			PreparedStatement stmt = conn.prepareStatement(raw_query);
			stmt.setInt(1, user_id);
			stmt.executeUpdate();
			return true;
		} catch (SQLException e) {
			System.out.println(e);
			e.printStackTrace();
			return false;
		}

	}

	public Boolean addLocalUser(Integer user_current, Integer user_to_add) {
		String raw_query = " insert into address(user_id, user_id_has) select ?,? from dual "
				+ " where not exists "
				+ " ( select * from address where "
				+ " case "
				+ " when user_id = ? then  user_id_has = ? "
				+ " when user_id = ? then user_id_has = ? " + " end) ;";
		try {
			PreparedStatement stmt = conn.prepareStatement(raw_query,
					Statement.RETURN_GENERATED_KEYS);
			stmt.setInt(1, user_current);
			stmt.setInt(2, user_to_add);
			stmt.setInt(3, user_current);
			stmt.setInt(4, user_to_add);
			stmt.setInt(6, user_current);
			stmt.setInt(5, user_to_add);
			stmt.executeUpdate();
			// System.out.println("created local user " + user_current + " " +
			// user_to_add );
			return true;

		} catch (SQLException e) {
			System.out.println(e);
			e.printStackTrace();
			return false;
		}
	}

	public ArrayList<User> searchGlobalUsers(String phrase, Integer id) {
		String raw_query = "select user_id, username , email, status, admin_flag from users where"
				+ " username like ? ";
		// +
		// " and (? or user_id, user_id or ?) not in (select * from block_list);";
		try {
			PreparedStatement stmt = conn.prepareStatement(raw_query);
			stmt.setString(1, "%" + phrase + "%");
			// stmt.setInt(2, id);
			// stmt.setInt(3, id);
			ResultSet rs = stmt.executeQuery();
			ArrayList<User> userlist = new ArrayList<User>();
			while (rs.next()) {
				int user_id = rs.getInt("user_id");
				String username = rs.getString("username");
				String email = rs.getString("email");
				int status = rs.getInt("status");
				int admin_flag = rs.getInt("admin_flag");
				if (id != user_id) {
					userlist.add(new User(user_id, username, email, status,
							admin_flag));
				}
			}
			//System.out.println("fetching users from world");
			return userlist;
		} catch (SQLException e) {
			System.out.println(e);
			e.printStackTrace();
			return null;
		}
	}
	
	@Override
	public ArrayList<UserActivity> fetchUserActivity(Integer user_id){
		if(user_id == -1){
                        //System.out.println("its in if " + user_id);
			String raw_query = "select user_id, time_in, time_out from user_activity; ";
			try {
				PreparedStatement stmt = conn.prepareStatement(raw_query);
				
				ResultSet rs = stmt.executeQuery();
				ArrayList<UserActivity> activitylist = new ArrayList<UserActivity>();
				while (rs.next()) {
					int id = rs.getInt("user_id");
					Timestamp in = rs.getTimestamp("time_in");
					Timestamp out = rs.getTimestamp("time_out");
					activitylist.add(new UserActivity(id, in, out));
                                        //System.out.println(id +" "+ in + " " +out );
				}
				return activitylist;
			} catch (SQLException e) {
				System.out.println(e);
				e.printStackTrace();
				return null;
			}
		}
		else{
                    //System.out.println("its in else " + user_id);
			String raw_query = " select user_id, time_in, time_out from user_activity "
					+ " where user_id = ? ; ";
			try {
				PreparedStatement stmt = conn.prepareStatement(raw_query);
				stmt.setInt(1, user_id);
				ResultSet rs = stmt.executeQuery();
				ArrayList<UserActivity> activitylist = new ArrayList<UserActivity>();
				while (rs.next()) {
					int id = rs.getInt("user_id");
					Timestamp in = rs.getTimestamp("time_in");
					Timestamp out = rs.getTimestamp("time_out");
					activitylist.add(new UserActivity(id, in, out));
                                        //System.out.println(id +" "+ in + " " +out );
				}
				return activitylist;
			} catch (SQLException e) {
				System.out.println(e);
				e.printStackTrace();
				return null;
			}			
		}
			
	}

	public ArrayList<User> searchLocalUsers(Integer user_id, String phrase) {
		String raw_query = "select U.user_id as id, U.username, U.email, U.status, U.admin_flag from users U, address A "
				+ " where "
				+ " case "
				+ " when A.user_id = ? then U.user_id = A.user_id_has "
				+ " when A.user_id_has = ? then U.user_id = A.user_id"
				+ " end " + " and " + "  U.username like ? ";

		// +
		// " and (? or A.user_id, A.user_id or ?) not in (select * from block_list);";
		try {
			PreparedStatement stmt = conn.prepareStatement(raw_query);
			stmt.setInt(1, user_id);
			stmt.setInt(2, user_id);
			stmt.setString(3, "%" + phrase + "%");
			// stmt.setInt(3, user_id);
			// stmt.setInt(4, user_id);
			ResultSet rs = stmt.executeQuery();
			ArrayList<User> userlist = new ArrayList<User>();
			while (rs.next()) {
				int id = rs.getInt("id");
				String username = rs.getString("username");
				String email = rs.getString("email");
				int status = rs.getInt("status");
				int admin_flag = rs.getInt("admin_flag");
				userlist.add(new User(id, username, email, status, admin_flag));
				//System.out.println("user id - " + id + "has " + username);
			}
			return userlist;
		} catch (SQLException e) {
			System.out.println(e);
			e.printStackTrace();
			return null;
		}
	}

	public Boolean blockUserFromLocalBook(Integer user_id_by, Integer user_id_to) {
		String raw_query = " insert ignore into block_list (user_id_by, user_id_to, time) select ?,?,null "
				+ " from dual "
				+ " where not exists "
				+ " (select * from block_list where user_id_by = ? "
				+ " and user_id_to = ?) ; ";
		try {
			PreparedStatement stmt = conn.prepareStatement(raw_query);
			stmt.setInt(1, user_id_by);
			stmt.setInt(2, user_id_to);
			stmt.setInt(3, user_id_by);
			stmt.setInt(4, user_id_to);
			stmt.executeUpdate();
			return true;
		} catch (SQLException e) {
			System.out.println(e);
			e.printStackTrace();
			return false;
		}
	}

	public ArrayList<User> fetchBlockedUsers(Integer user_id) {
		String raw_query = " select B.user_id_to, U.username, U.email, U.status, U.admin_flag "
				+ " from block_list B, Users U "
				+ " where B.user_id_by = ? and " + " U.user_id = B.user_id_to ";
		try {
			PreparedStatement stmt = conn.prepareStatement(raw_query);
			stmt.setInt(1, user_id);
			ResultSet rs = stmt.executeQuery();
			ArrayList<User> userlist = new ArrayList<User>();
			while (rs.next()) {
				int id = rs.getInt("user_id_to");
				String username = rs.getString("username");
				String email = rs.getString("email");
				int status = rs.getInt("status");
				int admin_flag = rs.getInt("admin_flag");
				userlist.add(new User(id, username, email, status, admin_flag));
			}
			return userlist;
		} catch (SQLException e) {
			System.out.println(e);
			e.printStackTrace();
			return null;
		}
	}
	
	
	public boolean markLogOut(Integer user_id, Timestamp login, Timestamp logout ) {
		String raw_query = "insert into user_activity values(?, ?, ?);";
		int flag =0;
		Connection fconn = conn;
		if(fconn==null){
			fconn = this.getConnection();
			flag =1;
		}
		try {
			PreparedStatement stmt = fconn.prepareStatement(raw_query,
					Statement.RETURN_GENERATED_KEYS);
			stmt.setInt(1, user_id);
			stmt.setTimestamp(2, login);
			stmt.setTimestamp(3, logout);
			stmt.executeUpdate();
			if(flag==1){
				fconn.close();
			}
			return true;
		} catch (SQLException e) {
			System.out.println(e);
			e.printStackTrace();
			if(flag==1){
				try {
					fconn.close();
				} catch (SQLException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
			return false;
		}
	}


	public Integer createConversation(Integer user_one, Integer user_two,
			Integer chat_type, String g_name) {
		int conv_id;
		String raw_query = "insert into conversations"
				+ " (conv_id,user_one, user_two, chat_type,time_of_update,time_user_one, time_user_two, status)"
				+ " select NULL,?,?,?,NULL, NULL, NULL, 0 from DUAL  "
				+ " where not  " + "exists ( "
				+ " select * from conversations C " + " where " + " case "
				+ " when " + " C.chat_type = 1 " + " then "
				+ " ( C.user_one= ? and C.user_two=? ) " + " or "
				+ " ( C.user_one= ? and C.user_two=? ) " + " end " + " ); ";
		try {
			PreparedStatement stmt = conn.prepareStatement(raw_query,
					Statement.RETURN_GENERATED_KEYS);
			stmt.setInt(1, user_one);
			stmt.setInt(2, user_two);
			stmt.setInt(3, chat_type);
			stmt.setInt(4, user_one);
			stmt.setInt(5, user_two);
			stmt.setInt(6, user_two);
			stmt.setInt(7, user_one);
			// stmt.setInt(8, user_two);
			// stmt.setInt(9, user_one);
			// stmt.setInt(10, user_two);
			// stmt.setInt(11, user_one);
			stmt.executeUpdate();
			ResultSet rs = stmt.getGeneratedKeys();
			if (rs.next()) {
				conv_id = rs.getInt(1);
				if (chat_type == 0) {

					raw_query = "insert ignore into "
							+ "group_users(conv_id,g_name,user_id,status,time_user) values(?,?,?,0,null)";
					try {
						stmt = conn.prepareStatement(raw_query);
						stmt.setInt(1, conv_id);
						stmt.setInt(3, user_one);
						stmt.setString(2, g_name);
						stmt.executeUpdate();
					} catch (SQLException e) {
						System.out.println(e);
						e.printStackTrace();
						return null;
					}
				}
				return conv_id;

			}
			return 0;
		} catch (SQLException e) {
			System.out.println(e);
			e.printStackTrace();
			return -1;
		}
	}

	public Boolean insertUserToGroup(Integer conv_id, Integer user_id) {
		String raw_query = " insert ignore into group_users(conv_id,g_name,user_id,status,time_user) "
				+ " select ?,G.g_name,?,0,null from group_users G "
				+ "  where G.conv_id =? group by G.g_name; ";
		try {
			PreparedStatement stmt = conn.prepareStatement(raw_query);
			stmt.setInt(1, conv_id);
			stmt.setInt(2, user_id);
			stmt.setInt(3, conv_id);
			stmt.executeUpdate();
			return true;
		} catch (SQLException e) {
			System.out.println(e);
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * Inserts text message into text_msg table
	 * 
	 * @param text
	 *            text to be inserted
	 * @return text_msg_id if successful; 0 otherwise
	 */
	public Integer insertText(String text, Connection fconn) {
		System.out.println("text is " + text);
		String raw_query = "insert into text_msg values (null, ?)";
		
		try {
			if(fconn == null){
				System.out.println("oops ");
			}
			else{
				System.out.println("fine ");
			}
			PreparedStatement stmt = fconn.prepareStatement(raw_query,
					Statement.RETURN_GENERATED_KEYS);
			System.out.println("reached her ");
			stmt.setString(1, text);
			stmt.executeUpdate();
			ResultSet rs = stmt.getGeneratedKeys();
			if (rs.next()) {
				int text_id = rs.getInt(1);
				System.out.println("reached her too with text id  " + text_id );
				return text_id;
			}
			return 0;
		} catch (SQLException e) {
			System.out.println(e);
			return 0;
		}
	}

	/**
	 * 
	 * @param conv_id
	 * @param user_id_s
	 * @param user_id_r
	 * @param text
	 * @param user_s_ip
	 * @param user_r_ip
	 * @return
	 */
	public int sendText(int conv_id, int user_id_s, int user_id_r, String text,
			String user_s_ip, String user_r_ip, Connection fconn) {
		if (fconn == null) {
			try {
				fconn = dataSource.getConnection();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		System.out.println("the conversation id of the text is ");
		System.out.println(conv_id);
		int text_id = this.insertText(text,fconn);
		
		Timestamp time = null;
		int conv_msg_id = 0;
		String raw_query = "insert into conv_msg values(null,?,?,?,?,0,'text',null,?,?)";
		try {
			PreparedStatement stmt = fconn.prepareStatement(raw_query,
					Statement.RETURN_GENERATED_KEYS);
			stmt.setInt(1, conv_id);
			stmt.setInt(2, user_id_s);
			stmt.setInt(3, user_id_r);
			stmt.setInt(4, text_id);
			stmt.setString(5, user_s_ip);
			stmt.setString(6, user_r_ip);

			stmt.executeUpdate();
			ResultSet rs = stmt.getGeneratedKeys();
			if (rs.next()) {
				conv_msg_id = rs.getInt(1);
			}

		} catch (SQLException e) {
			System.out.println(e);
			try {
				fconn.close();
			} catch (SQLException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			return 0;

		}
		raw_query = "select time from conv_msg where conv_msg_id = ?";
		try {
			PreparedStatement stmt = fconn.prepareStatement(raw_query);
			stmt.setInt(1, conv_msg_id);

			ResultSet rs = stmt.executeQuery();

			if (rs.next()) {
				time = rs.getTimestamp("time");
			}

		} catch (SQLException e) {
			System.out.println(e);
			try {
				fconn.close();
			} catch (SQLException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			return 0;
		}
		raw_query = "update conversations set time_of_update = ? where conv_id = ?";
		try {
			PreparedStatement stmt = fconn.prepareStatement(raw_query);
			stmt.setTimestamp(1, time);
			stmt.setInt(2, conv_msg_id);

			stmt.executeUpdate();
			try {
				fconn.close();
			} catch (SQLException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			return 1;

		} catch (SQLException e) {
			System.out.println(e);
			try {
				fconn.close();
			} catch (SQLException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			return 0;
		}
	}

	/**
	 * 
	 * @param conv_id
	 * @param user_id_s
	 * @param user_id_r
	 * @param file_id
	 * @param user_s_ip
	 * @param user_r_ip
	 * @return file_id assigned to the file with filename as input
	 */
	public Integer sendFile(int conv_id, int user_id_s, int user_id_r,
			Integer file_id, String user_s_ip, String user_r_ip,
			Connection fconn) {
		if (fconn == null) {
			try {
				fconn = dataSource.getConnection();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		int conv_msg_id = 0;
		Timestamp time = null;
		String raw_query = "insert into conv_msg values(null,?,?,?,null,?,'file',null,?,?)";
		try {
			PreparedStatement stmt = fconn.prepareStatement(raw_query,
					Statement.RETURN_GENERATED_KEYS);
			stmt.setInt(1, conv_id);
			stmt.setInt(2, user_id_s);
			stmt.setInt(3, user_id_r);
			stmt.setInt(4, file_id);
			stmt.setString(5, user_s_ip);
			stmt.setString(6, user_r_ip);

			stmt.executeUpdate();
			ResultSet rs = stmt.getGeneratedKeys();
			if (rs.next()) {
				conv_msg_id = rs.getInt(1);
			}

		} catch (SQLException e) {
			System.out.println(e);
			e.printStackTrace();
			try {
				fconn.close();
			} catch (SQLException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			return 0;
		}
		raw_query = "select time from conv_msg where conv_msg_id = ?";
		try {
			PreparedStatement stmt = fconn.prepareStatement(raw_query);
			stmt.setInt(1, conv_msg_id);

			ResultSet rs = stmt.executeQuery();

			if (rs.next()) {
				time = rs.getTimestamp("time");
			}

		} catch (SQLException e) {
			System.out.println(e);
			try {
				fconn.close();
			} catch (SQLException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			return 0;
		}
		raw_query = "update conversations set time_of_update = ? where conv_id = ?";
		try {
			PreparedStatement stmt = fconn.prepareStatement(raw_query);
			stmt.setTimestamp(1, time);
			stmt.setInt(2, conv_msg_id);

			stmt.executeUpdate();
			try {
				fconn.close();
			} catch (SQLException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			return file_id;

		} catch (SQLException e) {
			System.out.println(e);
			try {
				fconn.close();
			} catch (SQLException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			return 0;
		}
	}

	public ArrayList<Conversation> fetchConversationsForUser(Integer user_id) {
		String raw_query = " ( "
				+ "select C.conv_id, C.time_of_update, U.username as convname "
				+ "from conversations C , users U " + "where "
				+ "( C.user_one = ? or C.user_two =? )" + "  and " + " case "
				+ " when C.user_one = ? then U.user_id = C.user_two "
				+ " when C.user_two = ? then U.user_id = C.user_one " + " end "
				+ " and C.chat_type = 1 " + ") " + "union " + "( "
				+ " select C.conv_id, C.time_of_update , G.g_name as convname "
				+ " from conversations C, group_users G " + " where "
				+ " G.user_id = ? " + " and " + " C.chat_type = 0 " + " and "
				+ " G.conv_id = C.conv_id " + " ) "
				+ " order by time_of_update ; ";
		try {
			PreparedStatement stmt = conn.prepareStatement(raw_query);
			stmt.setInt(1, user_id);
			stmt.setInt(2, user_id);
			stmt.setInt(3, user_id);
			stmt.setInt(4, user_id);
			stmt.setInt(5, user_id);

			ResultSet rs = stmt.executeQuery();
			ArrayList<Conversation> convlist = new ArrayList<Conversation>();
			while (rs.next()) {
				int x = rs.getInt("conv_id");
				Timestamp time = rs.getTimestamp("time_of_update");
				String convname = rs.getString("convname");
				convlist.add(new Conversation(x, convname, time));
			}
			return convlist;
		} catch (SQLException e) {
			System.out.println(e);
			return null;
		}
	}

	@Override
	public Boolean setUserStatusAvailable(Integer userid) {

		return setStatusHelper(STATUS_AVAILABLE, userid);
	}

	@Override
	public Boolean setUserStatusBusy(Integer userid) {
		return setStatusHelper(STATUS_BUSY, userid);
	}

	@Override
	public Boolean setUserStatusOffline(Integer userid) {
		return setStatusHelper(STATUS_OFFLINE, userid);
	}

	@Override
	public Boolean setUserStatusIdle(Integer userid) {
		return setStatusHelper(STATUS_IDLE, userid);
	}

	private Boolean setStatusHelper(Integer status, Integer userid) {
		String raw_query = "update users set status = " + status
				+ " where user_id = ?";
		conn = getConnection();
		try {
			PreparedStatement stmt = conn.prepareStatement(raw_query);
			stmt.setInt(1, userid);
			stmt.executeUpdate();
			return true;
		} catch (SQLException e) {
			System.out.println(e);
			return false;
		}
	}

	public Boolean unblockUserFromLocalBook(Integer user_id_by,
			Integer user_id_to) {
		String raw_query = "delete from block_list where user_id_by = ?"
				+ " and  user_id_to = ? ; ";
		try {
			PreparedStatement stmt = conn.prepareStatement(raw_query);
			stmt.setInt(1, user_id_by);
			stmt.setInt(2, user_id_to);
			stmt.executeUpdate();
			return true;
		} catch (SQLException e) {
			System.out.println(e);
			e.printStackTrace();
			return false;
		}
	}

	@Override
	public ArrayList<TextMessage> fetchTextConversationHistory(
			Integer conversationId, Integer user_id) {

		String raw_query = ""
				+ " select R.conv_id, R.conv_msg_id, R.time, "
				+ " T.text_msg, "
				+ " U.username, U.user_id, "
				+ " R.user_id_r as rec, R.user_s_ip "
				+ " from"
				+ " conv_msg R , users U , text_msg T, conversations C "
				+ " where "
				+ " C.conv_id = ? and "
				+ " R.conv_id = ? "
				+ " and R.msg_type = 'text' "
				+ " and R.text_id = T.text_id "
				+ " and R.user_id_s = U.user_id "
				+ " and "
				+ " case "
				+ " when exists (select time from block_list where user_id_by = ? and user_id_to = R.user_id_s) "
				+ " then R.time < (select time from block_list where user_id_by = ? and user_id_to = R.user_id_s) "
				+ " else R.time > '1970-01-01' " + " end"
				+ " order by R.conv_msg_id asc ";
		try {
			PreparedStatement stmt = conn.prepareStatement(raw_query);
			stmt.setInt(3, user_id);
			stmt.setInt(4, user_id);
			stmt.setInt(1, conversationId);
			stmt.setInt(2, conversationId);
			// stmt.setInt(4, user_id);
			// stmt.setInt(5, user_id);

			ResultSet rs = stmt.executeQuery();
			ArrayList<TextMessage> convlist = new ArrayList<TextMessage>();
			while (rs.next()) {
				int conversationid = rs.getInt("conv_id");
				int x = rs.getInt("conv_msg_id");
				Timestamp time = rs.getTimestamp("time");
				String text = rs.getString("text_msg");
				String username = rs.getString("username");
				int senderid = rs.getInt("user_id");
				int rec = rs.getInt("rec");
                                String user_s_ip = rs.getString("user_s_ip");
				convlist.add(new TextMessage(senderid, username, text, time,
						rec, conversationid, user_s_ip));
			}
			return convlist;
		} catch (SQLException e) {
			System.out.println(e);
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public ArrayList<FileMessage> fetchFileConversationHistory(
			Integer conversationId, Integer user_id) {
		String raw_query = " select R.conv_id, R.conv_msg_id, R.time, "
				+ " F.filename, "
				+ " F.file_id, U.username, U.user_id, "
				+ " R.user_id_r as rec, R.user_s_ip from conversations C, "
				+ " conv_msg R , users U , files F  "
				+ " where "
				+ " C.conv_id = ? and "
				+ " R.conv_id = ?"
				+ " and R.msg_type = 'file' "
				+ " and R.file_id = F.file_id "
				+ " and R.user_id_s = U.user_id "
				+ " and "
				+ " case "
				+ " when exists (select time from block_list where user_id_by = ? and user_id_to = R.user_id_s) "
				+ " then R.time < (select time from block_list where user_id_by = ? and user_id_to = R.user_id_s) "
				+ " else R.time > '1970-01-01' " + " end"
				+ " order by R.conv_msg_id asc ";
		try {
			PreparedStatement stmt = conn.prepareStatement(raw_query);
			stmt.setInt(4, user_id);
			stmt.setInt(3, user_id);
			stmt.setInt(2, conversationId);
			stmt.setInt(1, conversationId);

			ResultSet rs = stmt.executeQuery();
			ArrayList<FileMessage> convlist = new ArrayList<FileMessage>();
			while (rs.next()) {
				int conversationid = rs.getInt("conv_id");
				int x = rs.getInt("conv_msg_id");
				Timestamp time = rs.getTimestamp("time");
				String filename = rs.getString("filename");
				int file_id = rs.getInt("file_id");
				String username = rs.getString("username");
				int senderid = rs.getInt("user_id");
				int rec = rs.getInt("rec");
                                String user_s_ip = rs.getString("user_s_ip");
				convlist.add(new FileMessage(filename, username, file_id,
						senderid, time, rec, conversationid,user_s_ip));
			}
			return convlist;
		} catch (SQLException e) {
			System.out.println(e);
			e.printStackTrace();
			return null;
		}
	}

	public String getFilename(int file_id, Connection fconn) {
		String filename = null;
		if (fconn == null) {
			try {
				fconn = dataSource.getConnection();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		String raw_query = "Select filename from files where file_id = ?";
		try {
			PreparedStatement stmt = fconn.prepareStatement(raw_query,
					Statement.RETURN_GENERATED_KEYS);
			stmt.setInt(1, file_id);
			ResultSet rs = stmt.executeQuery();
			while (rs.next()) {
				filename = rs.getString("filename");
			}
			return filename;
		} catch (SQLException e) {
			System.out.println(e);
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public Boolean deleteUserFromLocalBook(Integer user_current,
			Integer user_to_add) {
		System.out.println("1 is " + user_current + " 2 is " + user_to_add);
		String raw_query = " delete from address where ( user_id = ? and user_id_has = ? ) "
				+ " or ( user_id = ? and user_id_has = ? ) ; ";
		try {
			PreparedStatement stmt = conn.prepareStatement(raw_query,
					Statement.RETURN_GENERATED_KEYS);
			stmt.setInt(1, user_current);
			stmt.setInt(2, user_to_add);
			stmt.setInt(4, user_current);
			stmt.setInt(3, user_to_add);
			stmt.executeUpdate();
			return true;

		} catch (SQLException e) {
			System.out.println(e);
			e.printStackTrace();
			return false;
		}
	}

	@Override
	public Integer insertFile(String filename, Boolean check) {

		if(check == false){
			String raw_query = "insert into files values (null, ?)";
			try {
				PreparedStatement stmt = conn.prepareStatement(raw_query,
						Statement.RETURN_GENERATED_KEYS);
				stmt.setString(1, getName(filename));
				stmt.executeUpdate();
				ResultSet rs = stmt.getGeneratedKeys();
				if (rs.next()) {
					return rs.getInt(1);
				}
				return 0;
			} catch (SQLException e) {
				System.out.println(e);
				e.printStackTrace();
				return -1;
			}
		}
		else{
			String raw_query = "insert into file_repo values (null, ?)";
			try {
				PreparedStatement stmt = conn.prepareStatement(raw_query,
						Statement.RETURN_GENERATED_KEYS);
				stmt.setString(1, getName(filename));
				stmt.executeUpdate();
				ResultSet rs = stmt.getGeneratedKeys();
				if (rs.next()) {
					return rs.getInt(1);
				}
				return 0;
			} catch (SQLException e) {
				System.out.println(e);
				e.printStackTrace();
				return -1;
			}
		}
	}

	private static String getName(String fileName) {
		if (fileName.lastIndexOf("\\") != -1 && fileName.lastIndexOf("\\") != 0)
			return fileName.substring(fileName.lastIndexOf("\\") + 1);
		else if (fileName.lastIndexOf("/") != -1
				&& fileName.lastIndexOf("/") != 0)
			return fileName.substring(fileName.lastIndexOf("/") + 1);
		else
			return "";
	}

	// Admin Functions
	@Override
	public Integer getTotalMessagesInConversation(Integer conversation_id) {
		String raw_query = "select count(*) from conv_msg where conv_id = ?";
		try {
			PreparedStatement stmt = conn.prepareStatement(raw_query,
					Statement.RETURN_GENERATED_KEYS);
			stmt.setInt(1, conversation_id);
			ResultSet rs = stmt.executeQuery();
			if (rs.next()) {
				return rs.getInt(1);
			}
			return 0;
		} catch (SQLException e) {
			System.out.println(e);
			e.printStackTrace();
			return -1;
		}
	}

	@Override
	public Integer getConversationIdBetweenUsers(Integer user_one, Integer user_two) {
		String raw_query = "select conv_id from conversations where (user_one = ? and user_two = ?) or (user_one = ? and user_two = ?)";
		try {
			PreparedStatement stmt = conn.prepareStatement(raw_query,
					Statement.RETURN_GENERATED_KEYS);
			stmt.setInt(1, user_one);
			stmt.setInt(2, user_two);
			stmt.setInt(1, user_one);
			stmt.setInt(4, user_two);
			ResultSet rs = stmt.executeQuery();
			if (rs.next()) {
				return rs.getInt(1);
			}
			return 0;
		} catch (SQLException e) {
			System.out.println(e);
			e.printStackTrace();
			return -1;
		}
	}

	@Override
	public ArrayList<FileRepo> fetchFileRepo(){
		String raw_query = "Select file_id , filename from file_repo ;";
		try {
			PreparedStatement stmt = conn.prepareStatement(raw_query);
			ResultSet rs = stmt.executeQuery();
			ArrayList<FileRepo> filelist = new ArrayList<FileRepo>(); 
			while (rs.next()) {
				int id = rs.getInt("file_id");
				String name = rs.getString("filename");
				filelist.add(new FileRepo(id, name));
			}
			return filelist;
		} catch (SQLException e) {
			System.out.println(e);
			e.printStackTrace();
			return null;
		}
	}
	
	public Integer messagesSentOnASpecificDay(String day) {
		String raw_query = "select count(*) from conv_msg where time like ?";
		day += "%";
		try {
			conn = getConnection();
			PreparedStatement stmt = conn.prepareStatement(raw_query,
					Statement.RETURN_GENERATED_KEYS);
			stmt.setString(1, day);
			ResultSet rs = stmt.executeQuery();
			if (rs.next()) {
				return rs.getInt(1);
			}
			return 0;
		} catch (SQLException e) {
			System.out.println(e);
			e.printStackTrace();
			return -1;
		}
	}


	@Override
	public Hashtable<String, Integer> messagesPerDay(String dayStart,
			String dayEnd) {
		Hashtable<String, Integer> resultHT = new Hashtable<String, Integer>();
		int dayDifference = calculateTimeDifferenceInDays(dayStart, dayEnd);
		int count = 0;
		while (count < dayDifference) {
			int messageCount = messagesSentOnASpecificDay(dayStart);
			resultHT.put(dayStart, messageCount);
			dayStart = getNextDay(dayStart);
			count++;
		}
		return resultHT;
		//OLD
		/*if (dayDifference == 0) { // 1 day
			int messagesSentOnStartDay = messagesSentOnASpecificDay(dayStart);
			resultHT.put(dayStart, messagesSentOnStartDay);
		} else if (dayDifference < TWO_MONTHS) { // In days
			int count = 0;
			while (count < dayDifference) {
				int messageCount = messagesSentOnASpecificDay(dayStart);
				resultHT.put(dayStart, messageCount);
				dayStart = getNextDay(dayStart);
				count++;
			}
		} else if (dayDifference < TEN_MONTHS) { // In weeks
			int count = 0;
			while (count < dayDifference) {
				int messageCount = messagesSentOnASpecificDay(dayStart);
				resultHT.put(dayStart, messageCount);
				dayStart = getNextWeek(dayStart);
				count += ONE_WEEK;
			}
		} else if (dayDifference < FIVE_YEARS) { // In months
			int count = 0;
			while (count < dayDifference) {
				int messageCount = messagesSentOnASpecificDay(dayStart);
				resultHT.put(dayStart, messageCount);
				dayStart = getNextMonth(dayStart);
				count += ONE_MONTH;
			}
		} else { // In years
			int count = 0;
			while (count < dayDifference) {
				int messageCount = messagesSentOnASpecificDay(dayStart);
				resultHT.put(dayStart, messageCount);
				dayStart = getNextYear(dayStart);
				count += DAYS_IN_ONE_YEAR;
			}
		}*/
	}

	/**
	 * Helper of messages per day. Calculates time difference between two dates.
	 * 
	 * @param timeStart
	 * @param timeEnd
	 * @return day count as integer
	 */
	private int calculateTimeDifferenceInDays(String timeStart, String timeEnd) {
		int result = 0;
		int yearStart = Integer.parseInt(timeStart.substring(YEAR_INDEX_START,
				YEAR_INDEX_END));
		int yearEnd = Integer.parseInt(timeStart.substring(YEAR_INDEX_START,
				YEAR_INDEX_END));
		result += (yearEnd - yearStart) * 365;

		int monthStart = Integer.parseInt(timeStart.substring(
				MONTH_INDEX_START, MONTH_INDEX_END));
		int monthEnd = Integer.parseInt(timeStart.substring(MONTH_INDEX_START,
				MONTH_INDEX_END));
		result += (monthEnd - monthStart) * 30;

		int dayStart = Integer.parseInt(timeStart.substring(DAY_INDEX_START,
				DAY_INDEX_END));
		int dayEnd = Integer.parseInt(timeStart.substring(DAY_INDEX_START,
				DAY_INDEX_END));
		result += dayEnd - dayStart;

		return result;
	}

	/**
	 * Helper of messages per day.
	 * 
	 * @param currentDate
	 * @return next day as string of format "YYYY-MM-DD"
	 */
	private String getNextDay(String currentDate) {
		String nextDay = "";
		int currentYear = Integer.parseInt(currentDate.substring(
				YEAR_INDEX_START, YEAR_INDEX_END));
		int currentMonth = Integer.parseInt(currentDate.substring(
				MONTH_INDEX_START, MONTH_INDEX_END));
		int currentDay = Integer.parseInt(currentDate.substring(
				DAY_INDEX_START, DAY_INDEX_END));
		currentDay++;
		if (currentDay > ONE_MONTH) {
			currentDay = currentDay - ONE_MONTH; // returns 1
			currentMonth++;
		}
		if (currentMonth > ONE_YEAR) {
			currentMonth = currentMonth - ONE_YEAR; // returns 1
			currentYear++;
		}
		String strDay = formatDate(currentDay);
		String strMonth = formatDate(currentMonth);
		String strYear = formatDate(currentYear);
		nextDay = strYear + "-" + strMonth + "-" + strDay;
		return nextDay;
	}
//  OLD / UNUSED	
//	
//	/**
//	 * Recursively calls getNextDay: method to get the next week's date
//	 * 
//	 * @param currentDate
//	 * @return next week as string of format "YYYY-MM-DD"
//	 */
//	private String getNextWeek(String currentDate) {
//		for (int i = 0; i < ONE_WEEK; i++) {
//			currentDate = getNextDay(currentDate);
//		}
//		return currentDate;
//	}
//
//	/**
//	 * Uses similar approach to getNextDay: method to get the next month's date
//	 * 
//	 * @param currentDate
//	 * @return next month as string of format "YYYY-MM-DD"
//	 */
//	private String getNextMonth(String currentDate) {
//		String nextMonth = "";
//		int currentYear = Integer.parseInt(currentDate.substring(
//				YEAR_INDEX_START, YEAR_INDEX_END));
//		int currentMonth = Integer.parseInt(currentDate.substring(
//				MONTH_INDEX_START, MONTH_INDEX_END));
//		String strDay = currentDate.substring(DAY_INDEX_START, DAY_INDEX_END);
//		currentMonth++;
//
//		if (currentMonth > ONE_YEAR) {
//			currentMonth = currentMonth - ONE_YEAR; // returns 1
//			currentYear++;
//		}
//
//		String strMonth = formatDate(currentMonth);
//		String strYear = formatDate(currentYear);
//		nextMonth = strYear + "-" + strMonth + "-" + strDay;
//		return nextMonth;
//	}
//
//	/**
//	 * Uses similar approach to getNextDay: and getNextMonth: methods to get the
//	 * next year's date
//	 * 
//	 * @param currentDate
//	 * @return next year as string of format "YYYY-MM
//	 */
//	private String getNextYear(String currentDate) {
//		String nextYear = "";
//		int currentYear = Integer.parseInt(currentDate.substring(
//				YEAR_INDEX_START, YEAR_INDEX_END));
//		String strMonth = currentDate.substring(MONTH_INDEX_START,
//				MONTH_INDEX_END);
//		String strDay = currentDate.substring(DAY_INDEX_START, DAY_INDEX_END);
//		currentYear++;
//
//		String strYear = formatDate(currentYear);
//		nextYear = strYear + "-" + strMonth + "-" + strDay;
//		return nextYear;
//	}
	

	@Override
	public Integer getTotalMessagesSentByUser(Integer user_id) {
		String raw_query = "select count(*) from conv_msg where user_id_s = ?";
		try {
			PreparedStatement stmt = conn.prepareStatement(raw_query,
					Statement.RETURN_GENERATED_KEYS);
			stmt.setInt(1, user_id);
			ResultSet rs = stmt.executeQuery();
			if (rs.next()) {
				return rs.getInt(1);
			}
			return 0;
		} catch (SQLException e) {
			System.out.println(e);
			e.printStackTrace();
			return -1;
		}
	}

	/**
	 * Formats date according to database
	 * 
	 * @param date
	 * @return date as String of format "XX", if month or day ; "YYYY" , if
	 *         year.
	 */
	public String formatDate(int date) {
		if (date < 10) {
			return "0" + date;
		}
		return "" + date;
	}

	private static final int STATUS_AVAILABLE = 0;
	private static final int STATUS_BUSY = 1;
	private static final int STATUS_IDLE = 2;
	private static final int STATUS_OFFLINE = 3;

	private static final int ONE_MONTH = 31; // Days
	private static final int ONE_YEAR = 12; // Months
	
	private static final int YEAR_INDEX_START = 0;
	private static final int YEAR_INDEX_END = 4;
	private static final int MONTH_INDEX_START = 5;
	private static final int MONTH_INDEX_END = 7;
	private static final int DAY_INDEX_START = 8;
	private static final int DAY_INDEX_END = 10;

}
