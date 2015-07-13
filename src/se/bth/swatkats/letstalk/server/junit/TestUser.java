package se.bth.swatkats.letstalk.server.junit;

public class TestUser {
	int id,status,adminFlag;
	String userName,pwd,email;
	
	public TestUser(int id, String userName, String pwd, String email, int adminFlag, int status) {
		this.id = id;
		this.userName = userName;
		this.pwd = pwd;
		this.email = email;
		this.adminFlag = adminFlag;
		this.status = status;
	}
}
