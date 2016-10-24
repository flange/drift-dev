import java.security.MessageDigest;

public class Hash {

  public static String byteArrayToHexString(byte[] b) {
    String result = "";
    for (int i=0; i < b.length; i++) {
      result +=
            Integer.toString( ( b[i] & 0xff ) + 0x100, 16).substring( 1 );
    }
    return result;
  }

  public static String hashString(String line) throws Exception {

    MessageDigest md = MessageDigest.getInstance("SHA-512");
    md.update(line.getBytes());

    return byteArrayToHexString(md.digest());
  }








  public static void main(String[] args) throws Exception {

    String s = "abc123";

    System.out.println(hashString(s));

  }
}
