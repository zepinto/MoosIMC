package pt.lsts.moosimc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import MOOS.MOOSCommClient;
import MOOS.MOOSMsg;
import MOOS.comms.MessageType;
import pt.lsts.imc.DesiredHeading;
import pt.lsts.imc.DesiredSpeed;
import pt.lsts.imc.PlanControl;
import pt.lsts.imc.PlanControl.OP;
import pt.lsts.imc.PlanControl.TYPE;
import pt.lsts.imc.PlanControlState.STATE;
import pt.lsts.imc.adapter.VehicleAdapter;
import pt.lsts.imc.def.SpeedUnits;
import pt.lsts.imc.net.Consume;
import pt.lsts.neptus.messages.listener.Periodic;

public class MoosIMC extends VehicleAdapter {
	
	// MOOS comms API
	private MOOSCommClient moosClient;
	
	// MOOS' incoming data
	private ConcurrentHashMap<String, Double> ivpState = new ConcurrentHashMap<>();
	private boolean deployed = false, returning = false;
	
	/**
	 * Class constructor. Connects with MOOS and subscribes to IvP data.
	 */
	public MoosIMC(String vehicle, int imcId, String moosHostname, int moosPort) {
		super(vehicle, imcId);
		
		moosClient = new MOOSCommClient(moosHostname, moosPort);
		moosClient.setEnable(true);
		
		moosClient.register("DESIRED_SPEED", 1.0);
        moosClient.register("DESIRED_DEPTH", 1.0);
        moosClient.register("DESIRED_HEADING", 1.0);
        moosClient.register("NAV_LAT", 1.0);
        moosClient.register("NAV_LONG", 1.0);
        moosClient.register("NAV_ROLL", 1.0);
        moosClient.register("NAV_PITCH", 1.0);
        moosClient.register("NAV_HEADING", 1.0);
        moosClient.register("NAV_SPEED", 1.0);
        moosClient.register("NAV_DEPTH", 1.0);
        moosClient.register("PARK", 1.0);
        moosClient.register("DEPLOY", 0.0);
        moosClient.register("RETURN", 0.0);
        
        moosClient.setMessageHandler((ArrayList<MOOSMsg> messages) -> {
            return onNewMail(messages);
        });
	}
	
	@Consume
	public void on(PlanControl pc) {
		if (pc.getType() == TYPE.REQUEST) {
			if (pc.getOp() == OP.START) {
				moosClient.notify(new MOOSMsg(MessageType.Notify, "RETURN", "false"));
				moosClient.notify(new MOOSMsg(MessageType.Notify, "DEPLOY", "true"));
			}
			else if (pc.getOp() == OP.STOP) {
				moosClient.notify(new MOOSMsg(MessageType.Notify, "RETURN", "true"));
				moosClient.notify(new MOOSMsg(MessageType.Notify, "DEPLOY", "false"));
			}
		}
	}
	
	// Send IvP's Desired Speed
	@Periodic(1000)
	public void sendDesiredSpeed() {
		
		DesiredSpeed speed = new DesiredSpeed();
		speed.setSpeedUnits(SpeedUnits.METERS_PS);
		synchronized (ivpState) {
			if (!ivpState.containsKey("DESIRED_SPEED"))
				return;
			speed.setValue(ivpState.get("DESIRED_SPEED"));	
		}
		dispatch(speed);
	}
	
	// Send IvP's desired heading
	@Periodic(1000)
	public void sendDesiredHeading() {
		DesiredHeading msg = new DesiredHeading();
		synchronized (ivpState) {
			if (!ivpState.containsKey("DESIRED_HEADING"))
				return;
			msg.setValue(Math.toRadians(ivpState.get("DESIRED_HEADING")));	
		}
		dispatch(msg);
	}
	
	// Update plan control state according to DEPLOY / RETURN MOOS variables
	@Periodic(1000)
	public void sendPlanControlState() {
		synchronized (ivpState) {
			planControl.setState(STATE.READY);
			if (deployed) {
				planControl.setState(STATE.EXECUTING);
				planControl.setPlanId("moos plan");
			}
			if (returning) {
				planControl.setState(STATE.BLOCKED);
				planControl.setPlanId("moos return");
			}
		}
	}
	
	// Update the vehicle's state based on last received data
	@Periodic(500)
	public void sendState() {
		synchronized (ivpState) {
			try {
				setPosition(ivpState.get("NAV_LAT"), ivpState.get("NAV_LONG"), 0, ivpState.get("NAV_DEPTH"));
				setEuler(0, ivpState.get("NAV_PITCH"), ivpState.get("NAV_HEADING"));
			}
			catch (Exception e) {
				System.err.println("Not connected to Moos.");
			}
		}
	}
	
	// Incoming data from MOOS
	private boolean onNewMail(ArrayList<MOOSMsg> messages) {
        messages.stream().forEachOrdered(msg -> {
        	synchronized (ivpState) {
        		if (msg.isDouble())
        			ivpState.put(msg.getKey(), msg.getDoubleData());
        		else if (msg.getKey().equals("DEPLOY"))
        			deployed = msg.getStringData().equals("true");
        		else if (msg.getKey().equals("RETURN"))
        			returning = msg.getStringData().equals("true");
			}        	
        });
        return true;
    }
		
	// Create an instance of MoosIMC
	public static void main(String[] args) throws IOException {
		
		if (args.length != 4) {
			System.err.println("Usage: java -jar MoosAdapter.jar <vehicle> <imc_id> <moos_hostname> <moos_port>");
			System.exit(1);
		}
		int imcId = 0;
		String imcIdStr = args[1].toLowerCase().trim();
		if (imcIdStr.startsWith("0x"))
			imcId = Integer.valueOf(imcIdStr.substring(2), 16);
		else
			imcId = Integer.valueOf(imcIdStr);
		
		new MoosIMC(args[0], imcId, args[2], Integer.valueOf(args[3]));
	}  
}
