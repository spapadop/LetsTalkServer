package se.bth.swatkats.letstalk.filerepo;
import java.io.File;  
import java.io.FileInputStream;  
import java.io.ObjectInputStream;  
import java.io.ObjectOutputStream;  
import java.net.ServerSocket;
import java.net.Socket; 

import javax.crypto.SealedObject;

import se.bth.swatkats.letstalk.Constants;
import se.bth.swatkats.letstalk.connection.encryption.CryptModule;
import se.bth.swatkats.letstalk.connection.packet.Packet;
import se.bth.swatkats.letstalk.connection.packet.internal.OpenConnectionMessage;
/**
 * This class is responsible for reacting on file Repository download request
 * on the server side
 * 
 * @author Jyoti
 *
 */

public class download extends Thread{ 
	
    private static final int BUFFER_SIZE =1024; 
    
    private ObjectOutputStream oos;
    
    private ObjectInputStream ois;
    
    private CryptModule crypto;
    
    private SealedObject message;
    
    private long FILE_SIZE,sent=0;
    
    private String path; 
    /*
     * Open a port to receive file repository download requests
     */
    public void run() {  
        try {  
            ServerSocket serverSocket = new ServerSocket(Constants.REPOSITORYDOWNLOADPORT);  
            while (true) {  
                this.sent=0;
                Socket s = serverSocket.accept(); 
                this.sent=0;
                oos = new ObjectOutputStream(s.getOutputStream());  
                ois = new ObjectInputStream(s.getInputStream());  
                initConnection();
                filedownload(s);  
            }  
        } catch (Exception e) {  
            e.printStackTrace();  
        }  
    }  
    private void initConnection() {
		Packet message = null;
		try {
			message = (Packet) ois.readObject();
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (message instanceof OpenConnectionMessage) {
			OpenConnectionMessage m = (OpenConnectionMessage) message;
			crypto = new CryptModule();
			try {
				OpenConnectionMessage back = new OpenConnectionMessage(
						crypto.generateKeyPairFromClientKey(m.getKey()));
				oos.writeObject(back);
			} catch (Exception e) {
				System.err.print("Key exchange failed.");
				e.printStackTrace();
			}

		} else {
			System.err.print("Need to initialize Connection first.");
		}
	}
    /*
     * Read from file stream and write to object output stream
     */
    private void filedownload(Socket socket) throws Exception {  
         
    	message = (SealedObject) ois.readObject();
        path= Constants.REPOPATH +(String) crypto.decrypt(message);
        File file = new File(path); 
        this.FILE_SIZE=file.length();
        oos.writeObject(crypto.encrypt(FILE_SIZE));
        FileInputStream fis = new FileInputStream(file); 
        byte [] buffer = new byte[BUFFER_SIZE];  
        while (sent < FILE_SIZE) {
        fis.read(buffer);
        oos.writeObject(crypto.encrypt(buffer)); 
        this.sent=Math.min(sent+1024,FILE_SIZE);
        }
        /*
         * close file input stream,object input stream and object output stream
         */
        fis.close();
        oos.close();  
        ois.close();     
    } 
}
