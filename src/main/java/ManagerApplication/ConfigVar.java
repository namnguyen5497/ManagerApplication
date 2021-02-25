package ManagerApplication;

import java.text.SimpleDateFormat;

public class ConfigVar {
	private ConfigVar(){}
	
	public static final String ORIGINATOR = "admin:admin";
	public static final String CSEPROTOCOL = "http";

	public static final String MANCSEIP = "192.168.0.103"; 
	public static final int MANCSEPORT = 8080;
	public static final String MANCSEID = "in-cse";
	public static final String MANCSENAME = "in-name";

	public static final String AENAME = "ManagerAE";
	public static final String AEPROTOCOL = "http";
	public static final String AEIP = "192.168.0.103"; 
	public static final int AEPORT = 1600;
	public static final String AESUB = "ManagerSub";

	public static final String MANCSEPOA = CSEPROTOCOL + "://" + MANCSEIP + ":"+ MANCSEPORT;
	public static final String APPPOA = AEPROTOCOL+"://"+AEIP+":"+AEPORT;
	public static final String NU = "/"+MANCSEID+"/"+MANCSENAME+"/"+AENAME;
	
	public static int COMMANDID = 0; // for command indexing 
	public static final int DEPLOY = 2;
	public static final int ZIP = 3;
	
	public static final int WORKERS = 3; //range( 1 -> n );
	
	public static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss"); //date format
	
	public static final String DATADIR = "/home/namnguyen/data/cut_image"; // data storage
	
}
