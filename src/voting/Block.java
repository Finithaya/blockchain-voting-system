package voting;

import java.security.MessageDigest;
import java.util.Date;

public class Block {
    public String hash;
    public String previousHash;
    public String voterId;
    public String candidate;
    public long timeStamp;

   
    public Block(String voterId, String candidate, String previousHash) {
        this.voterId = voterId;
        this.candidate = candidate;
        this.previousHash = previousHash;
        this.timeStamp = new Date().getTime();
        this.hash = calculateHash();
    }

   
    public Block(String voterId, String candidate, String previousHash, long timeStamp, String hash) {
        this.voterId = voterId;
        this.candidate = candidate;
        this.previousHash = previousHash;
        this.timeStamp = timeStamp;
        this.hash = hash; 
    }

    public String calculateHash() {
        String data = previousHash + timeStamp + voterId + candidate;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data.getBytes("UTF-8"));
            StringBuffer hexString = new StringBuffer();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if(hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }
}