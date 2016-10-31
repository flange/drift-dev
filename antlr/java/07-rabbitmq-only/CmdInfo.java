import java.util.*;
import java.io.*;

import java.security.MessageDigest;

public class CmdInfo implements Serializable {

  public String sessionId = null;

  public String targetName = null;
  public String targetNamespace = null;

  public ArrayList<ServiceInfo> services = new ArrayList<ServiceInfo>();

  public boolean isAsync = false;
  public boolean isQuery = false;
  public boolean isLs    = false;
  public boolean isCd    = false;
  public boolean isRm    = false;
  public boolean resultIsNamespace = false;

  public int upCnt = 0;

  public void print() {
    System.out.println("CmdInfo:");
    System.out.println("  session:         " + sessionId);
    System.out.println("  targetName:      " + targetName);
    System.out.println("  targetNamespace: " + targetNamespace);
    System.out.println("  services: ");

    for (ServiceInfo si : services)
      si.print();

    System.out.println();
    System.out.println("  isAsync:     " + isAsync);
    System.out.println("  isQuery:     " + isQuery);
    System.out.println("  isLs:        " + isLs);
    System.out.println("  isCd:        " + isCd);
    System.out.println("  isRm:        " + isRm);
    System.out.println("    upCnt      " + upCnt);
    System.out.println("  NamespaceResult:        " + resultIsNamespace);

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

    sessionId       = cmdInfo.sessionId;
    targetName      = cmdInfo.targetName;
    targetNamespace = cmdInfo.targetNamespace;
    services        = cmdInfo.services;
    isAsync         = cmdInfo.isAsync;
    isQuery         = cmdInfo.isQuery;
    isLs            = cmdInfo.isLs;
    isCd            = cmdInfo.isCd;
    isRm            = cmdInfo.isRm;
    resultIsNamespace = cmdInfo.resultIsNamespace;
    upCnt           = cmdInfo.upCnt;

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

    StringJoiner sj = new StringJoiner(" | ");

    for (ServiceInfo si : services)
      sj.add(si.complete);

    return sj.toString();
  }


}
