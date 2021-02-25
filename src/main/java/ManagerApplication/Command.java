package ManagerApplication;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class Command {
	private Command(){}
	
	//private static SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss");
	private static Calendar cal;
	
	private static final Logger LOGGER = LogManager.getLogger(Command.class);

	
	public static int monitorResource(String csePoa, String cseId, String cseName, 
													int commandId, int commandCode){
		cal = Calendar.getInstance();
		String monitorURI = ConfigVar.MANCSEPOA+"/~/"+cseId+"/"+cseName+"/COMMAND";
		
		JSONObject obj = new JSONObject();
		JSONObject command = new JSONObject();
		command.put("COMMANDID", commandId);
		command.put("COMMANDCODE", commandCode);
		List<JSONObject> content = new ArrayList<JSONObject>();
		content.add((new JSONObject()).put("COMMAND", command));
		obj.put("rn", "command_"+ commandId +"_"+ ConfigVar.SDF.format(cal.getTime()));
		obj.put("cnf", "application/text");
		obj.put("con", content.toString());
		JSONObject resource = new JSONObject();
		resource.put("m2m:cin", obj);
		HttpResponse response = new HttpResponse();
		response = RestHttpClient.post(ConfigVar.ORIGINATOR, monitorURI, resource.toString(), 4);
		//LOGGER.info("Sent Monitor command to {}", cseId);
		return response.getStatusCode();
	}
	
	
	
	public static int zipData(String cseId, String cseName,
											int commandId, int commandCode, 
											String[] rangeImages, String serviceId){
		
		//cal = Calendar.getInstance();
		String workerURI = ConfigVar.MANCSEPOA+"/~/"+cseId+"/"+cseName+"/COMMAND";
		
		JSONObject obj = new JSONObject();
		JSONObject command = new JSONObject();
		command.put("COMMANDID", commandId);
		command.put("COMMANDCODE", commandCode);
		List<String> zipRatio = new ArrayList<String>();
		for(String str : rangeImages){
			zipRatio.add(str);
		}
		command.put("ZIP", zipRatio.toString());
		command.put("SERVICEID", serviceId);
		List<JSONObject> content = new ArrayList<JSONObject>();
		content.add((new JSONObject()).put("COMMAND", command));
		obj.put("rn", "command_"+ commandId +"_"+ serviceId);
		obj.put("cnf", "application/text");
		obj.put("con", content.toString());
		JSONObject resource = new JSONObject();
		resource.put("m2m:cin", obj);
		HttpResponse response = new HttpResponse();
		response = RestHttpClient.post(ConfigVar.ORIGINATOR, workerURI, resource.toString(), 4);
		LOGGER.info("Sent zipping data command to {}", cseId);
		return response.getStatusCode();
	}
	
	
	

	public static int deployContainer(String cseId, String cseName, 
										int commandCode, int commandId, Service service,
										String ratioImages){
		// add target POA, ID, NAme
		String workerURI = ConfigVar.MANCSEPOA+"/~/"+cseId+"/"+cseName+"/COMMAND";
		JSONObject obj = new JSONObject();
		int startImage = Integer.valueOf(ratioImages.split("-")[0]);
		int endImage = Integer.valueOf(ratioImages.split("-")[1]);
				
		JSONObject command = new JSONObject();
		command.put("COMMANDID",commandId);
		command.put("COMMANDCODE",commandCode);
		command.put("SERVICE", service.getServiceName());
		command.put("SERVICEID", service.getServiceId());
		command.put("STARTIMAGE", startImage);
		command.put("ENDIMAGE", endImage);
		command.put("DTSOURCE", service.getOriginalWorker().getCseId()+"/"+service.getOriginalWorker().getCseName());
		Worker originalWorker = service.getOriginalWorker(); // command result destination
		command.put("TARGETPOA", originalWorker.getCsePoa());   
		command.put("TARGETID", originalWorker.getCseId());
		command.put("TARGETNAME", originalWorker.getCseName());
		//add Delta Time and COMMAND to content
		List<JSONObject> content = new ArrayList<JSONObject>();
		content.add((new JSONObject()).put("COMMAND", command )); //index = 0
		
		cal = Calendar.getInstance();
		//content.add((new JSONObject()).put(""))
		obj.put("rn", "command_deploy_"+ commandId +"_"+ ConfigVar.SDF.format(cal.getTime()));
		obj.put("cnf", "application/text");
		obj.put("con", content.toString());
		JSONObject resource = new JSONObject();
		resource.put("m2m:cin", obj);
		HttpResponse response = new HttpResponse();
		response = RestHttpClient.post(ConfigVar.ORIGINATOR, workerURI, resource.toString(), 4);
		LOGGER.info("Sent Deploy command to {}", cseId );
		return response.getStatusCode();
	}
	
}
