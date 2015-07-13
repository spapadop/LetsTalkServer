package se.bth.swatkats.letstalk.server.mail;

/*
Author : Akanksha Gupta
Sends an email containing chat if other user is offline. 
*/


import java.util.Properties;
import javax.mail.*;
import javax.mail.internet.*;

public class SendMail {
	
	private String to;
	private String sender;
	private String text;
   
	public SendMail(String sendername, String receivermailid, String ChatText) {
		this.to = receivermailid;
		this.sender = sendername;
		this.text = ChatText;
	}

	public void mailToOfflineUser()
	{
     // to = "guptaaka95@gmail.com";  //email id of other user who is offline
      
      Properties props = new Properties();
      props.put("mail.smtp.host", "smtp.gmail.com"); //The SMTP server to connect to.
      props.put("mail.smtp.auth", "true");  //Attempt to authenticate the user.
      props.put("mail.smtp.port", "587");   //The SMTP port to connect to.
   // props.put("mail.smtp.ssl.enable", "false");  
      props.put("mail.smtp.starttls.enable", "true"); //enables the use of the STARTTLS command to switch the connection to 
                                                      //a TLS-protected connection before issuing any login commands
  //  props.put("mail.smtp.ssl.trust", "smtp.gmail.com");
       
     Session session = Session.getInstance(props, 
    		 new javax.mail.Authenticator() {
         protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication("noreply.letstalk", "passwordforswatkats");
         }
     });
     
     //MIME is an extension of the original Internet e-mail protocol
     //which helps in using the protocol to exchange different kinds of data files.
      try {
         Message message = new MimeMessage(session);
         message.setFrom(new InternetAddress("noreply.letstalk@gmail.com"));    // Set From field.
         message.setRecipients(Message.RecipientType.TO,InternetAddress.parse(to));    // Set To field.
         message.setSubject("Messages on LetsTalk"); // Set Subject field.
        // message.setContent("Messages from "+sender);     
         message.setText(sender+": "+text);    //from database
         Transport.send(message);           // Send message
         System.out.println("Sent Successfully");
         } catch (MessagingException e) {
            throw new RuntimeException(e);
           }
   }
}