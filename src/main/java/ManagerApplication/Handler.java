package ManagerApplication;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import PSO.Swarm;
import PSO.Vector;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

public class Handler implements HttpHandler {
	
	private static ArrayList<Worker> workerCse;
	private static CopyOnWriteArrayList<Worker> qualifiedWorker;
	private static ArrayList<Service> receivedService;
	private static ArrayList<Container> cnt;
	
	private static final Logger LOGGER = LogManager.getLogger(Handler.class);
	
	//For test ONLY
	private static int workloadTest = 100;
	private static int nTimes = 1;
	private static int nService = 2;
	
	public Handler(ArrayList<Worker> workerCse, CopyOnWriteArrayList<Worker> qualifiedWorker,
					ArrayList<Service> receivedService, ArrayList<Container> cnt){
		this.workerCse = workerCse;
		this.qualifiedWorker = qualifiedWorker;
		this.receivedService = receivedService;
		this.cnt = cnt;
	}
	
	
	public void handle(HttpExchange httpExchange) {
		//System.out.println("Event Recieved!");

		try {
			InputStream in = httpExchange.getRequestBody();

			String requestBody = "";
			int i;
			char c;
			while ((i = in.read()) != -1) {
				c = (char) i;
				requestBody = (String) (requestBody + c);
			}

			//System.out.println(requestBody);

			Headers inHeader = httpExchange.getRequestHeaders();
			String headerTimeStamp = inHeader.getFirst("X-M2M-OT");
			JSONObject json = new JSONObject(requestBody);

			String responseBudy = "";
			byte[] out = responseBudy.getBytes("UTF-8");
			httpExchange.sendResponseHeaders(200, out.length);
			OutputStream os = httpExchange.getResponseBody();
			os.write(out);
			os.close();

			if (json.getJSONObject("m2m:sgn").has("m2m:vrq")) {
				if (json.getJSONObject("m2m:sgn").getBoolean("m2m:vrq")) {
					//LOGGER.info("Confirm subscription");
				}
			} else {
				JSONObject rep = json.getJSONObject("m2m:sgn")
						.getJSONObject("m2m:nev").getJSONObject("m2m:rep").getJSONObject("m2m:cin");
				String pi = rep.getString("pi"); // = /worker-id/cnt-....
				String fromWorkerId = pi.split("/")[1]; // worker ID
				for (Container ctner : cnt) {
					if (pi.equals(ctner.getContainerID())) {
						JSONArray content = new JSONArray(rep.getString("con"));
						
						/*
						 * MONITOR worker node
						 */
						if (ctner.getContainerName().equals("MONITOR")) {

							System.out.println("Receive resource info from: "+ fromWorkerId);
							Worker worker = getWorkerById(fromWorkerId);
							worker.setCurrentWorkload(content.getJSONObject(3).getInt("CWORKLOAD"));
							//used to track time to transmiss from worker to manager
							//Timestamp ts1 = Timestamp.valueOf(content.getJSONObject(4).getString("TS"));
							//Date now = (Calendar.getInstance()).getTime();
							//long transT = (new Timestamp(now.getTime()).getTime() - ts1.getTime());
							//LOGGER.info("Transmission time from {} is {}", worker.getCseName(), transT);
							
							if (worker.getState()) { // get CPU in use		
								if(!qualifiedWorker.contains(worker)){
									qualifiedWorker.add(worker); 
								}
							}
							else{ // if not qualified, remove from list
								Boolean rmWorker = qualifiedWorker.remove(worker);
								if(rmWorker) {
									//no case to remove worker yet
								}
							}
						}

						/*	Incomming service
						 * 
						 */
						if (ctner.getContainerName().equals("SERVICE")) {
							System.out.println("New service to worker " + fromWorkerId 
												+ " service index: " + content.getJSONObject(5).getInt("NSERVICE"));
							
							Worker originalWorker = getWorkerById(fromWorkerId);
							Date now = (Calendar.getInstance()).getTime();
							int workLoad = content.getJSONObject(4).getInt("WORKLOAD");
							Service serv = new Service(content.getJSONObject(0).getString("SERVICE"),
														content.getJSONObject(1).getString("SERVICEID"), 
														originalWorker, 
														content.getJSONObject(2).getInt("NOAWORKER"),
														workLoad,
														(new Timestamp(now.getTime())).toString(),
														content.getJSONObject(5).getInt("NSERVICE"));
							receivedService.add(serv);
							
							System.out.println(" Now having service: ");
							for(Service service : receivedService){
								//System.out.println(service.getIndexService() + " " + service.getServiceId());
							}
									
							/*
							 * Run pso for current service, then send zip file message 
							 * 
							 * PSO.optimalRatio --> return a Map: PC:p1, pi1: p2, ...
							 * 
							 */
							Vector currentWorkload = getAllCurrentWorkload(); // get currentWorkload of all worker
							System.out.println("All nodes current workload: " + currentWorkload.toStringOutput());
							Swarm swarm = new Swarm(20, 1000, workerCse.size() + 1,   //particle, epoch, nodes = worker + 1 Man
									workLoad, currentWorkload);			// workLoad + currentWorkLoad
							Map<String, String> ratio = swarm.run(content.getJSONObject(1).getString("SERVICEID"));
							System.out.println(ratio.toString());
							
							serv.setRatio(ratio); //update workload ratio for this new service
							String[] ratioImages = new String[ratio.size()];// except service original worker
							
							int k = 0; //Create ratio Map{ key = Node; value = workload Ratio }
							for(String key : ratio.keySet()){
								ratioImages[k] = ratio.get(key);
								k++;
							}
							
							//wrap ratio Map into command for original worker to zip files 
							int zip = Command.zipData(originalWorker.getCseId(), 
														originalWorker.getCseName(),
														ConfigVar.COMMANDID, 
														ConfigVar.ZIP, ratioImages,   //ratioImages new String[]{"1-"+workLoad}
														serv.getServiceId());
							
						}

						
						/*	Receive service result 
						 * There are 2 Sub services: Zip Data + Detect images
						 * If Received service result is ZIP => meaning Zipping data is done from original worker
						 * => process to send command to other workers to pull zipped data and start detecting
						 * 
						 * If Received service result is DETECT => meaning 1 worker has done its detecting job
						 * => count if the number of worker report done DETECT service = number of worker got assigned
						 * for that service ID => Whole service is done 
						 */
						
						if (ctner.getContainerName().equals("RESULT")) {
							synchronized(this){
								
							String serviceId = content.getJSONObject(0).getString("SERVICEID");
							String service = content.getJSONObject(1).getString("SERVICE");
							
							//Result of zipping data 
							if(service.equals("ZIP")){
								if(content.getJSONObject(3).getInt("ZIPSTATE") == 1){ //This means Zipping data is done
									Service serv = getServiceById(serviceId);
									
									/*
									 * Send deploy command to workers
									*/
									Map<String, String> ratio = serv.getRatio();
									Iterator<Worker> iterator = qualifiedWorker.iterator();
									while(iterator.hasNext()){
										Worker worker = iterator.next();
										Command.deployContainer(
												worker.getCseId(), // command destination 
												worker.getCseName(), 
												ConfigVar.DEPLOY, 
												ConfigVar.COMMANDID,
												serv, 
												ratio.get(worker.getCseId())); 
										ConfigVar.COMMANDID++;
										serv.increaseAssignedWorker();
									}
									
									/* TEST DEPLOY ONLY ON 1 WORKER
									 * 
									Worker worker = getWorkerById(serv.getOriginalWorker().getCseId());
									Command.deployContainer(// command destination
											worker.getCseId(), 
											worker.getCseName(), 
											2, CommonVar.COMMANDID,
											serv, 
											"1-"+serv.getWorkload()); //recalculate number of images ratio.get(worker.getCseId())
									CommonVar.COMMANDID++;
									serv.increaseAssignedWorker();
									 */
									
									/* 
									 * Deploy on Manager
									 */
									Worker originalWorker = serv.getOriginalWorker();
									PullData.usingDiscovery(serv.getServiceId(), 
											originalWorker.getCseId()+"/"+originalWorker.getCseName(),  
											ratio.get("MANAGER"));  
									
									ZipUtils.UnzipData(ratio.get("MANAGER"),  
											serv.getServiceId());
									
									DockerController.deployService(ConfigVar.MANCSEPOA, 
											originalWorker.getCseId(), 
											originalWorker.getCseName(), 
											serv.getServiceId(), 
											"DetectImage", 
											ConfigVar.COMMANDID, 
											ratio.get("MANAGER"));  //"1-"+serv.getWorkload(), if only deploy on Man
									ConfigVar.COMMANDID++;
									serv.increaseAssignedWorker();
								}
								
							}
							
							if(service.equals("DETECT")){
								
									
									Service resultServ = getServiceById(serviceId);
									resultServ.decreaseAssignedWorker();
									System.out.println(resultServ.getServiceId());
									System.out.println("Service " + resultServ.getIndexService() + " CON SO WORKER: " 
											+ resultServ.getNumberOfAssignedWorker() + "\n");
									
									
									if (resultServ.getNumberOfAssignedWorker() == 0) {
										resultServ.updateServiceStatus(true);
									} else{
										System.out.println("Waiting for other workers for service "+ resultServ.getIndexService());
										//LOGGER.info("Waiting for other workers for service {}", resultServ.getServiceId());
									}
									
									
									if (getServiceById(serviceId).getServiceStatus()){
										System.out.println("FULL SERVICE DONE FOR SERVICE NUMER: " + resultServ.getIndexService() + "\n");
										Date now = (Calendar.getInstance()).getTime();
										Timestamp tsNow = new Timestamp(now.getTime());
										long fullService = tsNow.getTime() - Timestamp.valueOf(resultServ.getTime()).getTime();
										//LOGGER.info("Toltal time full service with ID: {}", fullService, resultServ.getServiceId());
										
										
										
										//to get result//////////////////////////////////
										String filename= "/home/namnguyen/Desktop/MyResult.txt";
										FileWriter fw = null; //the true will append the new data
										try
										{
											fw = new FileWriter(filename,true);
											fw.write("service number: "+ resultServ.getIndexService() + " serviceId: " + resultServ.getServiceId() 
													+ " workload: " + resultServ.getWorkload()
													+ " Tprocess: " + fullService  +"\n");
													//+ " Ratio: " + resultServ.getRatio().toString() +"\n");//appends the string to the file
											
										}
										catch(IOException ioe)
										{
											System.err.println("IOException: " + ioe.getMessage());
										}
										finally{
											if(fw != null){
												fw.close();
											}
										}
										//////////////////////////////////////////////////
										
										//indexService++;
										receivedService.remove(resultServ);
										receivedService.trimToSize(); // decrease size
										
										// FOR TEST 
										/*
										 */
										Thread.sleep(8000);
										JSONObject obj = new JSONObject();
										JSONObject resource = new JSONObject();
										obj.put("rn", "testService_"+ ConfigVar.SDF.format(Calendar.getInstance().getTime()));
										obj.put("cnf", "application/text");
										List<JSONObject> content_test = new ArrayList<JSONObject>();
										UUID serviceId_test = UUID.randomUUID(); //generate random unique serviceId 
										content_test.add((new JSONObject()).put("SERVICE", "DetectImage")); //0
										content_test.add((new JSONObject()).put("SERVICEID", serviceId_test.toString())); //1
										content_test.add((new JSONObject()).put("NOAWORKER", 0)); // 2 - Number of Assigned Workers
										content_test.add((new JSONObject()).put("DTSOURCE", "192.168.0.21")); // 3
										content_test.add((new JSONObject()).put("WORKLOAD", workloadTest)); //4 number of images
										content_test.add((new JSONObject()).put("NSERVICE", nService)); 
										obj.put("con", content_test.toString());
										resource.put("m2m:cin", obj);
										RestHttpClient.post(ConfigVar.ORIGINATOR, "http://192.168.0.21:8181"+"/~/"+"worker-1-id"+"/"+"worker-1"
												+"/"+"SERVICE" , resource.toString(), 4);
										//LOGGER.info("Sent service with ID: {}", serviceId.toString());
										
										//FOR TEST 
										nService++;
										nTimes++;
										if(nTimes == 20){
											nTimes = 1;
											workloadTest += 100;
										}
										
									}
								
							}
							
						}
					}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	/**
	 * 
	 * @param workerId: get worker with given Id
	 * @return	worker
	 */
	private static Worker getWorkerById(String workerId) {
		for (Worker worker : workerCse) {
			if ((worker.getCseId()).equals(workerId))
				return worker;
		}
		System.out.println("No worker found!");
		return null;
	}
	
	/**
	 * 
	 * @param serviceId: serviceID get from message
	 * @return service with given ID
	 */
	private static Service getServiceById(String serviceId) {
		Iterator<Service> iterator = receivedService.iterator();
		while(iterator.hasNext()){
			Service service = iterator.next();
			if(serviceId.equals(service.getServiceId()))
				return service;
		}
		System.out.println("No service found!");
		return null;
	}
	
	/**
	 * 
	 * @return current Workload of all qualified worker
	 */
	private static Vector getAllCurrentWorkload(){
		double[] cWorkload = new double[qualifiedWorker.size()+1]; // workers + manager
		int index = 0;
		cWorkload[index] = getNodeCurrentWorkload(); //manager 
		for(Worker worker : qualifiedWorker){
			index++;
			cWorkload[index] = worker.getCurrentWorkload(); //workers
		}
		Vector currentWorkload = new Vector(cWorkload);
		return currentWorkload;
	}
	
	private static int getNodeCurrentWorkload(){
		int currentWorkload = 0;
		File folder = new File(ConfigVar.DATADIR);
		File[] listOfFile = folder.listFiles();
		if(listOfFile.length == 0){
			System.out.println("Empty");
		}
		for(File file : listOfFile){
			if(file.isDirectory()){
				System.out.println(file.getAbsolutePath());
				folder = new File(file.getAbsolutePath());
				currentWorkload += folder.listFiles().length;
			}
		}
		System.out.println(currentWorkload);
		return currentWorkload;
	}
	
	
}
