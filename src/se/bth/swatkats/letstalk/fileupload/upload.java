package se.bth.swatkats.letstalk.fileupload;

import java.io.File; 
import java.io.FileOutputStream;  
import java.io.ObjectInputStream;  
import java.io.ObjectOutputStream;  
import java.net.Socket;  
import java.net.ServerSocket; 

import javax.crypto.SealedObject;

import se.bth.swatkats.letstalk.Constants;
import se.bth.swatkats.letstalk.connection.encryption.CryptModule;
import se.bth.swatkats.letstalk.connection.packet.Packet;
import se.bth.swatkats.letstalk.connection.packet.internal.OpenConnectionMessage;
  
/**
 * This class is responsible for reacting on file Message upload request
 * on the server side
 * 
 * @author Jyoti
 *
 */
public class upload extends Thread {  
	
    private static final int BUFFER_SIZE = 1024; 
    
    private ObjectOutputStream oos;
    
    private ObjectInputStream ois;
    
    private CryptModule crypto;
    
    private SealedObject message;
    
    private long FILE_SIZE,remain;
    /*
     * Open a port to receive file upload requests
     */
    public void run() {  
        try {  
            ServerSocket serverSocket = new ServerSocket(Constants.UPLOADPORT);  
            while (true) {
                Socket s = serverSocket.accept(); 
                oos = new ObjectOutputStream(s.getOutputStream());  
                ois = new ObjectInputStream(s.getInputStream());  
                initConnection();
                saveFile(s);
                s.close();
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
     * Read from object input stream and write to file stream
     */  
    private void saveFile(Socket socket) throws Exception {  
        FileOutputStream fos = null;  
        byte [] buffer = new byte[BUFFER_SIZE];  
  
        // Read file name. 
        message = (SealedObject) ois.readObject();
        int id=(int) crypto.decrypt(message);
        message = (SealedObject) ois.readObject();
        FILE_SIZE=(long) crypto.decrypt(message);
        message = (SealedObject) ois.readObject();
        Object file = crypto.decrypt(message);
        remain=FILE_SIZE;
  
        if (file instanceof String) {
        	//File targetDirectory = new File("/home/files/");//wherever the server wants files to be saved
        	File f=new File (Constants.FILEPATH+String.valueOf(id)+"."+file.toString());
        	try{
        		f.createNewFile();
        	}
        	catch(Exception e){
        		e.printStackTrace();
        		}
            fos = new FileOutputStream(f);
        	//fos = new FileOutputStream(new File (String.valueOf(id)+"."+file.toString()));
        } else {  
            throwException("File name not received");  
        }  
  
        // Read file to the end.  
          int size=0;
  
          while (remain > 0) {
        	
            try{
            	size=Math.min(1024,(int)remain);
            	message = (SealedObject) ois.readObject();
                buffer=(byte[]) crypto.decrypt(message);
            	remain=Math.max(0,remain-1024);
                }catch (Exception e) {
                  e.printStackTrace();
                }  
            fos.write(buffer, 0, size);
        } 
        System.out.println("File transfer success");  
        /*
         * close file output stream,object input stream and object output stream
         */ 
        fos.close();  
        ois.close();  
        oos.close();  
    }  
  
    public static void throwException(String message) throws Exception {  
        throw new Exception(message);  
    }  
} 

