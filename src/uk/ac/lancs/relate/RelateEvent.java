package uk.ac.lancs.relate;

/**
 *  RelateEvent encapsulates model related events within
 *  the Relate system such as the detection of a new device
 *  or a new model that was computed.
 *  @author Chris Kray
 */
public class RelateEvent implements Cloneable {
	
	/** event types */
	public final static int UNDEFINED = 0;
	public final static int NEW_MEASUREMENT = 10;
	public final static int DEVICE_ADDED = 20;
	public final static int DEVICE_REMOVED = 30;
	public final static int DEVICE_MOVED = 40;
	public final static int NEW_MODEL = 100;
	public final static int DEVICE_INFO = 1000;
	public final static int CALIBRATION_INFO = 500;
	public final static int IP_SHARED = 800;
	public final static int ERROR_CODE = 600;
	public final static int DN_STATE = 700;
	
	public final static int[] TYPES = {
		UNDEFINED, NEW_MEASUREMENT, DEVICE_ADDED, DEVICE_REMOVED,
		DEVICE_MOVED, NEW_MODEL, DEVICE_INFO, CALIBRATION_INFO, 
		IP_SHARED, ERROR_CODE, DN_STATE
	};
		
	public final static String[] TYPE_NAMES = {
		"UNDEFINED", "NEW_MEASUREMENT", "DEVICE_ADDED", "DEVICE_REMOVED",
		"DEVICE_MOVED", "NEW_MODEL", "DEVICE_INFO", "CALIBRATION_INFO",
		"IP_SHARED", "ERROR_CODE", "DN_STATE"
	};
	
	/** type of the event */
	protected int type;
	/** argument of the event (the devce that this event relates to) */
	protected Device device;
	/** measurement related to this event */
	protected Measurement measurement;
	/** time stamp of the event */
	protected long timeStamp;
	/** (incomplete) model */
//	protected Model model;
	/** Calibration object */
	protected Calibration calibration;
	/** Error code as an Integer */
	protected Integer errorCode;
	/** DongleNetworkState object */
	protected DongleNetworkState dnState;
	
	/** Default (empty) constructor */
	public RelateEvent() {
		type = UNDEFINED;
		device = null;
		measurement = null;
		timeStamp = -1;
//		model = null;
		calibration = null ;
		errorCode = null ;
		dnState = null ;
	}
	
	/** Fully qualified constructor 
		@param t  type of the event
		@param d  device this event relates to (null if N/A)
		@param m  the measurement associated with this event; null if N/A
		@param ts the timeStamp of the event (system time)
		@param mo the model this event relates to; null if N/A
	 **/
	public RelateEvent(int t, Device d, Measurement m, long ts
/*, 
			Model mo*/) {
		type = t;
		device = d;
		measurement = m;
		timeStamp = ts;
//		model = mo;
		calibration = null ;
	}

	/** Fully qualified constructor 
		@param t  type of the event
		@param d  device this event relates to (null if N/A)
		@param m  the measurement associated with this event; null if N/A
		@param ts the timeStamp of the event (system time)
		@param mo the model this event relates to; null if N/A
		@param c the Calibration object associated with this event; null if N/A
	 **/
	public RelateEvent(int t, Device d, Measurement m, long ts, 
/*			Model mo, */Calibration c) {
		type = t;
		device = d;
		measurement = m;
		timeStamp = ts;
//		model = mo;
		calibration = c ;
	}

	/** Fully qualified constructor 
		@param t  type of the event
		@param d  device this event relates to (null if N/A)
		@param m  the measurement associated with this event; null if N/A
		@param ts the timeStamp of the event (system time)
		@param mo the model this event relates to; null if N/A
		@param errorCode firmware error code as an Integer; null if N/A
	 **/
	public RelateEvent(int t, Device d, Measurement m, long ts, 
/*			Model mo, */Integer errorCode) {
		type = t;
		device = d;
		measurement = m;
		timeStamp = ts;
//		model = mo;
		this.errorCode = errorCode ;
	}

	/** Fully qualified constructor 
		@param t  type of the event
		@param d  device this event relates to (null if N/A)
		@param m  the measurement associated with this event; null if N/A
		@param ts the timeStamp of the event (system time)
		@param mo the model this event relates to; null if N/A
		@param c the Calibration object associated with this event; null if N/A
		@param errorCode firmware error code as an Integer; null if N/A
	 **/
	public RelateEvent(int t, Device d, Measurement m, long ts, 
/*			Model mo, */Calibration c, Integer errorCode) {
		type = t;
		device = d;
		measurement = m;
		timeStamp = ts;
//		model = mo;
		calibration = c ;
		this.errorCode = errorCode ;
	}

	/** Fully qualified constructor 
		@param t  type of the event
		@param d  device this event relates to (null if N/A)
		@param m  the measurement associated with this event; null if N/A
		@param ts the timeStamp of the event (system time)
		@param mo the model this event relates to; null if N/A
		@param c the Calibration object associated with this event; null if N/A
		@param dnState DongleNetworkState object; null if N/A
	 **/
	public RelateEvent(int t, Device d, Measurement m, long ts, 
/*			Model mo, */Calibration c, DongleNetworkState dnState) {
		type = t;
		device = d;
		measurement = m;
		timeStamp = ts;
//		model = mo;
		calibration = c ;
		this.dnState = dnState ;
	}

	/** Fully qualified constructor 
		@param t  type of the event
		@param d  device this event relates to (null if N/A)
		@param m  the measurement associated with this event; null if N/A
		@param ts the timeStamp of the event (system time)
		@param mo the model this event relates to; null if N/A
		@param dnState DongleNetworkState object; null if N/A
	 **/
	public RelateEvent(int t, Device d, Measurement m, long ts, 
/*			Model mo, */DongleNetworkState dnState) {
		type = t;
		device = d;
		measurement = m;
		timeStamp = ts;
//		model = mo;
		this.dnState = dnState ;
	}

	/** Fully qualified constructor 
		@param t  type of the event
		@param d  device this event relates to (null if N/A)
		@param m  the measurement associated with this event; null if N/A
		@param ts the timeStamp of the event (system time)
		@param mo the model this event relates to; null if N/A
		@param c the Calibration object associated with this event; null if N/A
		@param errorCode firmware error code as an Integer; null if N/A
		@param dnState DongleNetworkState object; null if N/A
	 **/
	public RelateEvent(int t, Device d, Measurement m, long ts, 
/*			Model mo, */Calibration c, Integer errorCode, 
			DongleNetworkState dnState) {
		type = t;
		device = d;
		measurement = m;
		timeStamp = ts;
//		model = mo;
		calibration = c ;
		this.errorCode = errorCode ;
		this.dnState = dnState ;
	}

	/**
	 * @return Returns the device.
	 */
	public Device getDevice() {
		return device;
	}
	/**
	 * @param device The device to set.
	 */
	public void setDevice(Device device) {
		this.device = device;
	}
	/**
	 * @return Returns the measurement.
	 */
	public Measurement getMeasurement() {
		return measurement;
	}
	/**
	 * @param measurement The measurement to set.
	 */
	public void setMeasurement(Measurement measurement) {
		this.measurement = measurement;
	}
	/**
	 * @return Returns the model.
	 */
/*	public Model getModel() {
		return model;
	}*/
	/**
	 * @param model The model to set.
	 */
/*	public void setModel(Model model) {
		this.model = model;
	}*/
	/**
	 * @return Returns the timeStamp.
	 */
	public long getTimeStamp() {
		return timeStamp;
	}
	/**
	 * @param timeStamp The timeStamp to set.
	 */
	public void setTimeStamp(long timeStamp) {
		this.timeStamp = timeStamp;
	}
	/**
	 * @return Returns the type.
	 */
	public int getType() {
		return type;
	}
	/**
	 * @param type The type to set.
	 */
	public void setType(int type) {
		this.type = type;
	}

	/**
	 * @return Returns the dnState.
	 */
	public DongleNetworkState getDongleNetworkState() {
		return dnState;
	}
	
	/**
	 * @param c The dnState to set.
	 */
	public void setDongleNetworkState(DongleNetworkState dnState) {
		this.dnState = dnState;
	}
	

	/**
	 * @return Returns the errorCode.
	 */
	public Integer getErrorCode() {
		return errorCode;
	}
	
	/**
	 * @param c The errorCode to set.
	 */
	public void setErrorCode(Integer errorCode) {
		this.errorCode = errorCode;
	}
	
	/**
	 * @return Returns the Calibration.
	 */
	public Calibration getCalibration() {
		return calibration;
	}
	
	/**
	 * @param c The Calibration to set.
	 */
	public void setCalibration(Calibration c) {
		this.calibration = c;
	}
	
	/** pretty print */
	public String toString() {
		int i;
		String result = null;
		
		for (i=0; i<TYPES.length; i++) {
			if (type == TYPES[i])
				result = "[RelateEvent: " + TYPE_NAMES[i] + ", ";
		}
		if (result != null) {
			if (getDevice() != null)
				result += "device=" + getDevice() + " ";
			if (getMeasurement() != null)
				result += "measurement=" + getMeasurement() + " ";
/*			if (getModel() != null)
				result += "model=" + getModel() + " ";*/
			if (getTimeStamp() > -1)
				result += "timeStamp=" + getTimeStamp() + " ";
			if (getCalibration() != null)
				result += "calibration=" + getCalibration() + " ";
			if (getErrorCode() != null)
				result += "errorCode=" + getErrorCode() + " ";
			if (getDongleNetworkState() != null)
				result += "dongle network state=" + getDongleNetworkState();
			return (result  + "]");
		} else
			return "[INVALID EVENT]";
	}
	
	/** validity check: currently non-functional */
	public boolean isValid() {
		boolean result = true;
		return result;
	}
		
	/**
	 ** cloning implementation
	 **/
	public Object clone() {
		return new RelateEvent(getType(), 
				(getDevice() == null ? null : 
					(Device) getDevice().clone()),
				(getMeasurement() == null ? null :
					(Measurement) getMeasurement().clone()),
				getTimeStamp(),
/*				(getModel() == null ? null :
					(Model) getModel().clone()),*/
					(getCalibration() == null ? null :
						(Calibration) getCalibration().clone()),
						(errorCode == null ? null :
								getErrorCode()),
							(getDongleNetworkState() == null ? null :
								(DongleNetworkState) getDongleNetworkState().clone())
								);
	}
	
	/**
	 ** Test method
	 **/
	public static void main(String[] args) {
	}
}
