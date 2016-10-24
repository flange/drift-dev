import java.util.*;
import java.io.*;

import java.security.MessageDigest;

public class CmdInfo implements Serializable {

  public String sessionId = null;

  public String name = null;
  public ArrayList<ServiceInfo> services = new ArrayList<ServiceInfo>();

  public boolean isAsync = false;
  public boolean isQuery = false;

  public void print() {
    System.out.println("CmdInfo:");
    System.out.println("  session:     " + sessionId);
    System.out.println("  name:        " + name);
    System.out.print("  services: ");

    for (ServiceInfo si : services)
      System.out.print(si.complete + " ");

    System.out.println();
    System.out.println("  isAsync:     " + isAsync);
    System.out.println("  isQuery:     " + isQuery);
    System.out.println();
  }

  public byte[] toByteArray() {

    ByteArrayOutputStream bos = new ByteArrayOutputStream();

    try {
      ObjectOutputStream out = new ObjectOutputStream(bos) ;
      out.writeObject(this);
      out.close();
    } catch (IOException e) {
      System.out.println(e);
    }

    return bos.toByteArray();
  }

  public void fromByteArray(byte[] body) {

    CmdInfo cmdInfo = new CmdInfo();

    try{
      ByteArrayInputStream bis = new ByteArrayInputStream(body);
      ObjectInputStream ois = new ObjectInputStream(bis);

      cmdInfo = (CmdInfo) ois.readObject();
    } catch (Exception e) {
      System.out.println(e);
    }

    sessionId = cmdInfo.sessionId;
    name      = cmdInfo.name;
    services  = cmdInfo.services;
    isAsync   = cmdInfo.isAsync;
    isQuery   = cmdInfo.isQuery;

    return;
  }

  public String byteArrayToHexString(byte[] b) {
    String result = "";
    for (int i=0; i < b.length; i++) {
      result +=
            Integer.toString( ( b[i] & 0xff ) + 0x100, 16).substring( 1 );
    }
    return result;
  }

  public String servicesHash() {

    ByteArrayOutputStream bos = new ByteArrayOutputStream();

    try {
      ObjectOutputStream out = new ObjectOutputStream(bos) ;
      out.writeObject(services);
      out.close();

      MessageDigest md = MessageDigest.getInstance("SHA-256");
      md.update(bos.toByteArray());

      return byteArrayToHexString(md.digest());

    } catch (Exception e) {
      System.out.println(e);
    }

    return "";
  }

  public String servicesString() {

    StringJoiner sj = new StringJoiner("| ");

    for (ServiceInfo si : services)
      sj.add(si.complete);

    return sj.toString();
  }


}
