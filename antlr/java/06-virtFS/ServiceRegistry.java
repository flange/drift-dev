import java.util.*;

public class ServiceRegistry {

  public Map<String, ServiceWrapper> db = new HashMap<String, ServiceWrapper>();

  public boolean isValid(ServiceInfo si) {

    if (!db.containsKey(si.binary))
      return false;

    ServiceWrapper dbs = db.get(si.binary);

    if ((dbs.nsIn == si.nsIn) && (dbs.nsOut == si.nsOut))
      return true;

    //System.out.println("lookup(): invalid (" + si.nsIn + "/" + si.nsOut + " vs " + dbs.nsIn + "/" + dbs.nsOut + ")");
    return false;
  }


}
