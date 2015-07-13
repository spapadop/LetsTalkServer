package se.bth.swatkats.letstalk.server.mailwithfile;

/*
Author : Akanksha Gupta
Sends an email containing chat with files if other user is offline. 
*/

import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

public class SendMailWithFile {
			
	private String to;
	private String sender;
	private String filepath;
	private String filename;
		   
	public SendMailWithFile(String sendername, String receivermailid, String path, String name) {
		this.sender = sendername;
		this.to = receivermailid;
		this.filepath = path;
		this.filename = name;
	}

	public void mailFileToOfflineUser()
	{
		  // to = "guptaaka95@gmail.com";  //email id of other user who is offline

	      
	      Properties props = new Properties();
	      props.put("mail.smtp.host", "smtp.gmail.com"); //The SMTP server to connect to.
	      props.put("mail.smtp.auth", "true");  ////Attempt to authenticate the user.
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
	         message.setSubject("Messages on LetsTalk");     // Set Subject field.

	         // Create the message part
	         BodyPart messageBodyPart = new MimeBodyPart();

	         // Now set the actual message
	         messageBodyPart.setText(sender+": ");

	          // Create a multipart message
	          Multipart multipart = new MimeMultipart();

	          // Set text message part
	          multipart.addBodyPart(messageBodyPart);

	          // Part two is attachment
	          messageBodyPart = new MimeBodyPart();
	          DataSource source = new FileDataSource(filepath);
	          DataHandler data = new DataHandler(source);
	          messageBodyPart.setDataHandler(data);
	          messageBodyPart.setFileName(filename);
	          multipart.addBodyPart(messageBodyPart);

	          // Send the complete message parts
	          message.setContent(multipart);

	          // Send message
	          Transport.send(message);

	          System.out.println("Sent message successfully....");
	   
	       } catch (MessagingException e) {
	          throw new RuntimeException(e);
	       }
	   }
}
