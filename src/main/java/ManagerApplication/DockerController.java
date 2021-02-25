package ManagerApplication;

import java.io.BufferedReader;
import java.io.InputStreamReader;


public class DockerController {
	private static final DockerController docker = new DockerController();
	
	private DockerController(){}
	
	public static DockerController getInstance(){
		return docker;
	}
		

	//official
	public static void deployService(String csePoa, String cseId, String cseName, 
										String serviceId, String service, int commandIdNumber,
										String ratioImages, long... time) throws Exception {
		int startImage = Integer.valueOf(ratioImages.split("-")[0]);
		int endImage = Integer.valueOf(ratioImages.split("-")[1]);
		String command = service.split("Image")[0];

		if (command.isEmpty()) {
			throw new Exception("Empty Command!");
		}
		if (service == "") {
			throw new Exception("Null Service!");
		}
		String timeCommand = "";
		for (long t : time) {
			timeCommand += t + " ";
		}
		String commandDeploy = "docker exec " + service
				+ " python3 /opt/generateCommand.py " + command + " " + csePoa
				+ " " + cseId + " " + cseName + " " +  " " + commandIdNumber + " " 
				+ serviceId + " " + startImage + " " + endImage + " "  
				+ timeCommand;
		try {
			Process proc = Runtime.getRuntime().exec(commandDeploy);

			Boolean successful = proc.waitFor() == 0 && proc.exitValue() == 0;
			System.out.println("Deploy container for service "+ serviceId +" :" + successful);

			BufferedReader stdInput = new BufferedReader(new InputStreamReader(
					proc.getInputStream()));

			String line = null;
			System.out.println("**************");
			while ((line = stdInput.readLine()) != null) {
				System.out.println(line);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	
	}
}

