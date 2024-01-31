package org.fog.test.perfeval;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.Actuator;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacementEdgewards;
import org.fog.placement.ModulePlacementMapping;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;


public class SmartApartment {
	static List<FogDevice> fogDevices = new ArrayList<FogDevice>();
	static List<Sensor> sensors = new ArrayList<Sensor>();
	static List<Actuator> actuators = new ArrayList<Actuator>();
	static int Apartment = 1;
	static int Room = 1;	
	private static boolean CLOUD = true;
	private static boolean FOG = false; 
	
	public static void main(String[] args) {

		Log.printLine("Starting Smart Apartment...");

		try {
			Log.disable();
			int num_user = 1; // number of cloud users
			Calendar calendar = Calendar.getInstance();
			boolean trace_flag = false; // mean trace events
			CloudSim.init(num_user, calendar, trace_flag);
			String appId = "SA"; // identifier of the application			
			FogBroker broker = new FogBroker("broker");
			
			Application application = createApplication(appId, broker.getId());
			application.setUserId(broker.getId());
			
			createFogDevices(broker.getId(), appId);
			
			Controller controller = null;
			
			ModuleMapping moduleMapping = ModuleMapping.createModuleMapping(); // initializing a module mapping
			for(FogDevice device : fogDevices){
				if(device.getName().startsWith("m")){ 					
					moduleMapping.addModuleToDevice("temperature_detector", device.getName());
					moduleMapping.addModuleToDevice("humidity_detector", device.getName());
					moduleMapping.addModuleToDevice("light_detector", device.getName());
					moduleMapping.addModuleToDevice("motion_detector", device.getName());
				}
			}
			moduleMapping.addModuleToDevice("user_interface", "cloud"); // fixing instances of User Interface module in the Cloud
			if(CLOUD){// if the mode of deployment is cloud-based
				moduleMapping.addModuleToDevice("smart_apartment", "cloud"); //placing all instances of Smart apartment module in the Cloud
 
			}else if (FOG) {
				for(FogDevice device : fogDevices){
						if(device.getName().startsWith("d")){ // names of all Smart device start with 'd' 
							moduleMapping.addModuleToDevice("smart_apartment", device.getName()); // placing all instances of Smart apartment module in the Cloud
						}
					}								
			}else {
				for(FogDevice device : fogDevices){
					if(device.getName().startsWith("m")){ // names of all Smart device start with 'm' 
						moduleMapping.addModuleToDevice("smart_apartment", device.getName()); 
					}
				}				
				
			}
			
			controller = new Controller("master-controller", fogDevices, sensors, actuators);
			
			controller.submitApplication(application, 
					(CLOUD)?(new ModulePlacementMapping(fogDevices, application, moduleMapping))
							:(new ModulePlacementEdgewards(fogDevices, sensors, actuators, application, moduleMapping)));
			
			TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
			
			CloudSim.startSimulation();

			CloudSim.stopSimulation();

			Log.printLine("System Implementation finished!");
		} catch (Exception e) {
			e.printStackTrace();
			Log.printLine("Unwanted errors happen");
		}
	}
	
	
	private static void createFogDevices(int userId, String appId) {
		FogDevice cloud = createFogDevice("cloud", 44800, 40000, 10000, 10000, 0, 0.01, 16*103, 16*83.25);
		cloud.setParentId(-1);
		fogDevices.add(cloud);
		FogDevice proxy = createFogDevice("proxy-server", 6000, 16000, 10000, 10000, 1, 0.0, 100, 80);
		proxy.setParentId(cloud.getId());
		proxy.setUplinkLatency(100); //latency of connection between proxy server and cloud is 100 ms
		fogDevices.add(proxy);
		for(int i=0;i<Apartment;i++){
			addApartment(i+"", userId, appId, proxy.getId());
		}
	}

	private static FogDevice addApartment(String id, int userId, String appId, int parentId){
		FogDevice Gateway = createFogDevice("d-"+id, 2800, 4000, 10000, 10000, 2, 0.0, 107.339, 83.4333);
		fogDevices.add(Gateway);
		Gateway.setUplinkLatency(30); // latency of connection between Gateway and proxy server is 2 ms
		for(int i=0;i<Room;i++){
			String mobileId = id+"-"+i;
			FogDevice EdgeDevice = addRoom(mobileId, userId, appId, Gateway.getId()); //adding a smart system to the physical topology. 
			EdgeDevice.setUplinkLatency(2); // latency of connection between  edge device and gateway is 2 ms
			fogDevices.add(EdgeDevice);
		}
		Gateway.setParentId(parentId);
		return Gateway;
	}
	
	private static FogDevice addRoom(String id, int userId, String appId, int parentId){
		FogDevice EdgeDevice = createFogDevice("m-"+id, 1600, 2000, 10000, 10000, 3, 0, 107.339, 83.4333);
		EdgeDevice.setParentId(parentId);
		
		Sensor TempSensor = new Sensor("T_Sensor"+id, "TemperatureSensor", userId, appId, new DeterministicDistribution(5)); // inter-transmission time of (sensor) follows a deterministic distribution
		sensors.add(TempSensor);
		TempSensor.setGatewayDeviceId(EdgeDevice.getId());
		TempSensor.setLatency(1.0); 
		
		Sensor HumidSensor = new Sensor("H_Sensor"+id, "HumiditySensor", userId, appId, new DeterministicDistribution(5)); // inter-transmission time of (sensor) follows a deterministic distribution
		sensors.add(HumidSensor);
		HumidSensor.setGatewayDeviceId(EdgeDevice.getId());
		HumidSensor.setLatency(1.0);
		
		Sensor LightSensor = new Sensor("L_Sensor"+id, "LightSensor", userId, appId, new DeterministicDistribution(5)); // inter-transmission time of (sensor) follows a deterministic distribution
		sensors.add(LightSensor);
		LightSensor.setGatewayDeviceId(EdgeDevice.getId());
		LightSensor.setLatency(1.0);
		
		Sensor MotionSensor = new Sensor("M_Sensor"+id, "MotionSensor", userId, appId, new DeterministicDistribution(5)); // inter-transmission time of (sensor) follows a deterministic distribution
		sensors.add(MotionSensor);
		MotionSensor.setGatewayDeviceId(EdgeDevice.getId());
		MotionSensor.setLatency(1.0);
		
		Actuator Aircooler = new Actuator("AC"+id, userId, appId, "AcRegulator");
		actuators.add(Aircooler);
		Aircooler.setGatewayDeviceId(EdgeDevice.getId());
		Aircooler.setLatency(1.0);

		Actuator LightControl = new Actuator("LightSwitch"+id, userId, appId, "LightControl");
		actuators.add(LightControl);
		LightControl.setGatewayDeviceId(EdgeDevice.getId());
		LightControl.setLatency(1.0);// latency of connection LightControl and the parent Smart addRoom(samrt_apartment) is 1 ms
		
		return EdgeDevice;
	}
	

	private static FogDevice createFogDevice(String nodeName, long mips,
			int ram, long upBw, long downBw, int level, double ratePerMips, double busyPower, double idlePower) {
		
		List<Pe> peList = new ArrayList<Pe>();

		// 3. Create PEs and add these into a list.
		peList.add(new Pe(0, new PeProvisionerOverbooking(mips))); // need to store Pe id and MIPS Rating

		int hostId = FogUtils.generateEntityId();
		long storage = 1000000; // host storage
		int bw = 10000;

		PowerHost host = new PowerHost(
				hostId,
				new RamProvisionerSimple(ram),
				new BwProvisionerOverbooking(bw),
				storage,
				peList,
				new StreamOperatorScheduler(peList),
				new FogLinearPowerModel(busyPower, idlePower)
			);

		List<Host> hostList = new ArrayList<Host>();
		hostList.add(host);

		String arch = "x86"; // system architecture
		String os = "Linux"; // operating system
		String vmm = "Xen";
		double time_zone = 10.0; // time zone this resource located
		double cost = 3.0; // the cost of using processing in this resource
		double costPerMem = 0.05; // the cost of using memory in this resource
		double costPerStorage = 0.001; // the cost of using storage in this
										// resource
		double costPerBw = 0.0; // the cost of using bw in this resource
		LinkedList<Storage> storageList = new LinkedList<Storage>(); 
													

		FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
				arch, os, vmm, host, time_zone, cost, costPerMem,
				costPerStorage, costPerBw);

		FogDevice fogdevice = null;
		try {
			fogdevice = new FogDevice(nodeName, characteristics, 
					new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, 0, ratePerMips);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		fogdevice.setLevel(level);
		return fogdevice;
	}

	@SuppressWarnings({"serial" })
	private static Application createApplication(String appId, int userId){
		
		Application application = Application.createApplication(appId, userId);	
		application.addAppModule("temperature_detector", 10);
		application.addAppModule("humidity_detector", 10);
		application.addAppModule("light_detector", 10);
		application.addAppModule("motion_detector", 10);
		application.addAppModule("smart_apartment", 10);
		application.addAppModule("user_interface", 10);
		
		//edges
		application.addAppEdge("TemperatureSensor", "temperature_detector", 1000, 20000, "TEMP_SENSE", Tuple.UP, AppEdge.SENSOR); // adding edge from (sensor) to temperature_detector module carrying tuples of type TEMP_SENSE
		application.addAppEdge("temperature_detector", "smart_apartment", 2000, 2000, "TEMPERATURE", Tuple.UP, AppEdge.MODULE); // adding edge from temperature_detector to smart_apartment module carrying tuples of type TEMPERATURE
		application.addAppEdge("HumiditySensor", "humidity_detector", 500, 2000, "HUM_SENSE", Tuple.UP, AppEdge.SENSOR); 
		application.addAppEdge("humidity_detector", "smart_apartment", 1000, 100, "HUMIDITY", Tuple.UP, AppEdge.MODULE);
		application.addAppEdge("LightSensor", "light_detector", 500, 2000, "LIGHT_SENSE", Tuple.UP, AppEdge.SENSOR); 
		application.addAppEdge("light_detector", "smart_apartment", 1000, 100, "LIGHT_STATE", Tuple.UP, AppEdge.MODULE);
		application.addAppEdge("MotionSensor", "motion_detector", 500, 2000, "MOTION_SENSE", Tuple.UP, AppEdge.SENSOR); 
		application.addAppEdge("motion_detector", "smart_apartment", 1000, 100, "MOVEMENT", Tuple.UP, AppEdge.MODULE);

		application.addAppEdge("smart_apartment", "AcRegulator", 100, 28, 100, "UP_DOWN", Tuple.DOWN, AppEdge.ACTUATOR); 
		application.addAppEdge("smart_apartment", "LightControl", 100, 28, 100, "ON_OFF", Tuple.DOWN, AppEdge.ACTUATOR);
		application.addAppEdge("smart_apartment", "user_interface", 100, 28, 100, "DATA_SHEET", Tuple.UP, AppEdge.MODULE);

		//module input-output
		application.addTupleMapping("temperature_detector", "TEMP_SENSE", "TEMPERATURE", new FractionalSelectivity(1.0)); // 1.0 tuples of type TEMPERATURE are emitted by temperature_detector module per incoming tuple of type TEMP_SENSE
		application.addTupleMapping("humidity_detector", "HUM_SENSE", "HUMIDITY", new FractionalSelectivity(1.0)); 
		application.addTupleMapping("light_detector", "LIGHT_SENSE", "LIGHT_STATE", new FractionalSelectivity(1.0)); 
		application.addTupleMapping("motion_detector", "MOTION_SENSE", "MOVEMENT", new FractionalSelectivity(0.8));
		
		application.addTupleMapping("smart_apartment", "TEMPERATURE", "UP_DOWN", new FractionalSelectivity(0.8));
		application.addTupleMapping("smart_apartment", "LIGHT_STATE", "ON_OFF", new FractionalSelectivity(0.8));
		
		final AppLoop loop1 = new AppLoop(new ArrayList<String>(){{add("TEMP_SENSE");add("temperature_detector");add("smart_apartment");}});
		final AppLoop loop2 = new AppLoop(new ArrayList<String>(){{add("HUM_SENSE");add("humidity_detector");add("smart_apartment");}});
		final AppLoop loop3 = new AppLoop(new ArrayList<String>(){{add("LIGHT_SENSE");add("light_detector");add("smart_apartment");}});
		final AppLoop loop4 = new AppLoop(new ArrayList<String>(){{add("MOTION_SENSE");add("motion_detector");add("smart_apartment");}});


		final AppLoop loop5 = new AppLoop(new ArrayList<String>(){{add("smart_apartment");add("AcRegulator");}});
		final AppLoop loop6 = new AppLoop(new ArrayList<String>(){{add("smart_apartment");add("LightControl");}});

		List<AppLoop> loops = new ArrayList<AppLoop>(){{add(loop1);add(loop2);add(loop3);add(loop4);add(loop5);add(loop6);}};
		
		application.setLoops(loops);
		return application;
	}
}
