/*
 * Created on 28-Jan-2005
 */
package uk.ac.lancs.relate;

/**
 * @author Henoc AGBOTA
 */
public class Calibration implements Cloneable {
	
	/** Number of transducers used by a relate dongle to get measurements */
    public final static int SENSORS_NUMBER = 3;
	/** Average background level for sensor uS1 */
	protected int ablUs1;
	/** Average background level for sensor uS2 */
	protected int ablUs2;
	/** Average background level for sensor uS4 */
	protected int ablUs4;
	/** Maximal background level for sensor uS1 */
	protected int mblUs1;
	/** Maximal background level for sensor uS1 */
	protected int mblUs2;
	/** Maximal background level for sensor uS1 */
	protected int mblUs4;
	/** Threshold level for sensor uS1 */
	protected int thlUs1;
	/** Threshold level for sensor uS1 */
	protected int thlUs2;
	/** Threshold level for sensor uS1 */
	protected int thlUs4;

	/** 
	 * Default constructor
	 * @param ablUs1          Average background level for sensor uS1
	 * @param ablUs2          Average background level for sensor uS2
	 * @param ablUs4          Average background level for sensor uS4
	 * @param mblUs1          Maximal background level for sensor uS1
	 * @param mblUs2          Maximal background level for sensor uS2
	 * @param mblUs4          Maximal background level for sensor uS4
	 * @param thlUs1          Threshold level for sensor uS1
	 * @param thlUs2          Threshold level for sensor uS2
	 * @param thlUs4          Threshold level for sensor uS4
	 */
	public Calibration(int ablUs1,int ablUs2,int ablUs4,int mblUs1,
			int mblUs2,int mblUs4,int thlUs1,int thlUs2,int thlUs4) {
		super();
		this.ablUs1 = ablUs1;
		this.ablUs2 = ablUs2;
		this.ablUs4 = ablUs4;
		this.mblUs1 = mblUs1;
		this.mblUs2 = mblUs2;
		this.mblUs4 = mblUs4;
		this.thlUs1 = thlUs1;
		this.thlUs2 = thlUs2;
		this.thlUs4 = thlUs4;
	}

	/**
	 * @param sensorId the id of the corresponding uS sensor.
	 * @return the average background level for the corresponding 
	 * or -1 if sensorId is invalid.
	 */
	public int getAverageBackgroundLevel(int sensorId) {
		int result = -1 ;
		if (sensorId == 1)
			result = this.ablUs1 ;
		if (sensorId == 2)
			result = this.ablUs2 ;
		if (sensorId == 4)
			result = this.ablUs4 ;
		return result ;
	}

	/**
	 * @param sensorId the id of the corresponding uS sensor.
	 * @return the maximal background level for the corresponding 
	 * or -1 if sensorId is invalid.
	 */
	public int getMaximalBackgroundLevel(int sensorId) {
		int result = -1 ;
		if (sensorId == 1)
			result = this.mblUs1 ;
		if (sensorId == 2)
			result = this.mblUs2 ;
		if (sensorId == 4)
			result = this.mblUs4 ;
		return result ;
	}

	/**
	 * @param sensorId the id of the corresponding uS sensor.
	 * @return the threshold level for the corresponding 
	 * or -1 if sensorId is invalid.
	 */
	public int getThresholdLevel(int sensorId) {
		int result = -1 ;
		if (sensorId == 1)
			result = this.thlUs1 ;
		if (sensorId == 2)
			result = this.thlUs2 ;
		if (sensorId == 4)
			result = this.thlUs4 ;
		return result ;
	}

	/**
	 * implement clone interface
	 * @author Henoc AGBOTA
	 */
	public Object clone() {
		return new Calibration(this.getAverageBackgroundLevel(1),
				this.getAverageBackgroundLevel(2),this.getAverageBackgroundLevel(4),
				this.getMaximalBackgroundLevel(1),this.getMaximalBackgroundLevel(2),
				this.getMaximalBackgroundLevel(4),this.getThresholdLevel(1),
				this.getThresholdLevel(2),this.getThresholdLevel(4)) ;
	}

	/**
	 * set new average background level
	 * @param sensorId The id of the corresponding sensor.
	 * @param abluS new average background level for the corresponding sensor.
	 */
	public void setAverageBackgroundLevel(int sensorId, int abluS) {
		if (sensorId == 1)
			this.ablUs1 = abluS ;
		if (sensorId == 2)
			this.ablUs2 = abluS ;
		if (sensorId == 4)
			this.ablUs4 = abluS ;
	}

	/**
	 * set new maximal background level
	 * @param sensorId The id of the corresponding sensor.
	 * @param mbluS new maximal background level for the corresponding sensor.
	 */
	public void setMaximalBackgroundLevel(int sensorId, int mbluS) {
		if (sensorId == 1)
			this.mblUs1 = mbluS ;
		if (sensorId == 2)
			this.mblUs2 = mbluS ;
		if (sensorId == 4)
			this.mblUs4 = mbluS ;
	}

	/**
	 * set new threshold level
	 * @param sensorId The id of the corresponding sensor.
	 * @param thluS new threshold level for the corresponding sensor.
	 */
	public void setThresholdLevel(int sensorId, int thluS) {
		if (sensorId == 1)
			this.thlUs1 = thluS ;
		if (sensorId == 2)
			this.thlUs2 = thluS ;
		if (sensorId == 4)
			this.thlUs4 = thluS ;
	}
	
	/**
	 * equality check: if two Calibration objects have the same average
	 * background level, maximal background level and threshold level for 
	 * all three sensors uS1, uS2, uS4
	 * @author Henoc AGBOTA
	 * @return true if two Calibrations are equal
	 */
	public boolean equals(Calibration calibration) {
		boolean result = false;
		int i ;
		if (calibration != null) {
			result = true ;
			for(i = 0; i < SENSORS_NUMBER; i++){
				result = result && 
				(getAverageBackgroundLevel(i+1) == 
					calibration.getAverageBackgroundLevel(i+1)) ;
				result = result && 
				(getMaximalBackgroundLevel(i+1) == 
					calibration.getMaximalBackgroundLevel(i+1)) ;
				result = result && 
				(getThresholdLevel(i+1) == 
					calibration.getThresholdLevel(i+1)) ;
				if (!result)
					break ;
			}
		}
		return result;
	}

	/** pretty print */
	public String toString() {
	    StringBuffer result = new StringBuffer(100);
	    result.append("[Calibration: abl=(");
	    result.append(getAverageBackgroundLevel(1)+",");
	    result.append(getAverageBackgroundLevel(2)+",");
	    result.append(getAverageBackgroundLevel(4)+")");
	    result.append(" mbl=(");
	    result.append(getMaximalBackgroundLevel(1)+",");
	    result.append(getMaximalBackgroundLevel(2)+",");
	    result.append(getMaximalBackgroundLevel(4)+")");
	    result.append(" thl=("); 
	    result.append(getThresholdLevel(1)+",");
	    result.append(getThresholdLevel(2)+",");
	    result.append(getThresholdLevel(4)+")");
	    result.append(" ]");
	    return result.toString();
	}
	
	/** string parsing */
	public static Calibration fromString(String str) {
		System.out.println("[Calibration] Warning: to be re-done") ;
	    Calibration result = null;
	    int mblUs1 = -1,
	    	thlUs1 = -1,
	    	ablUs1 = -1,
	    	mblUs2 = -1,
	    	thlUs2 = -1,
	    	ablUs2 = -1,
	    	mblUs4 = -1,
	    	thlUs4 = -1,
	    	ablUs4 = -1,
	    	index, index2;
	    if (str != null) {
	        index = str.indexOf("ablUs1=");
	        if (index > 0) {
	            index2 = str.indexOf("mblUs1=", index);
	            if (index2 > 0) {
	                try {
	                    ablUs1 = Integer.parseInt(str.substring(index+7, index2-1));
	                } catch (NumberFormatException e) {}
	            }
	        }
	        index = str.indexOf("mblUs1=");
	        if (index > 0) {
	            index2 = str.indexOf("thlUs1=", index);
	            if (index2 > 0) {
	                try {
	                    mblUs1 = Integer.parseInt(str.substring(index+7, index2-1));
	                } catch (NumberFormatException e) {}
	            }
	        }
	        index = str.indexOf("thlUs1=");
	        if (index > 0) {
	            index2 = str.indexOf("ablUs2=", index);
	            if (index2 > 0) {
	                try {
	                    thlUs1 = Integer.parseInt(str.substring(index+7, index2-1));
	                } catch (NumberFormatException e) {}
	            }
	        }
	        index = str.indexOf("ablUs2=");
	        if (index > 0) {
	            index2 = str.indexOf("mblUs2=", index);
	            if (index2 > 0) {
	                try {
	                    ablUs2 = Integer.parseInt(str.substring(index+7, index2-1));
	                } catch (NumberFormatException e) {}
	            }
	        }
	        index = str.indexOf("mblUs2=");
	        if (index > 0) {
	            index2 = str.indexOf("thlUs2=", index);
	            if (index2 > 0) {
	                try {
	                    mblUs2 = Integer.parseInt(str.substring(index+7, index2-1));
	                } catch (NumberFormatException e) {}
	            }
	        }
	        index = str.indexOf("thlUs2=");
	        if (index > 0) {
	            index2 = str.indexOf("ablUs4=", index);
	            if (index2 > 0) {
	                try {
	                    thlUs2 = Integer.parseInt(str.substring(index+7, index2-1));
	                } catch (NumberFormatException e) {}
	            }
	        }
	        index = str.indexOf("ablUs4=");
	        if (index > 0) {
	            index2 = str.indexOf("mblUs4=", index);
	            if (index2 > 0) {
	                try {
	                    ablUs4 = Integer.parseInt(str.substring(index+7, index2-1));
	                } catch (NumberFormatException e) {}
	            }
	        }
	        index = str.indexOf("mblUs4=");
	        if (index > 0) {
	            index2 = str.indexOf("thlUs4=", index);
	            if (index2 > 0) {
	                try {
	                    mblUs4 = Integer.parseInt(str.substring(index+7, index2-1));
	                } catch (NumberFormatException e) {}
	            }
	        }
	        index = str.indexOf("thlUs4=");
	        if (index > 0) {
	            index2 = str.indexOf("]", index);
	            if (index2 > 0) {
	                try {
	                    thlUs4 = Integer.parseInt(str.substring(index+7, index2-1));
	                } catch (NumberFormatException e) {}
	            }
	        }
	        if ((mblUs1 != -1) && (ablUs1 != -1) && (thlUs1 != -1) &&
	            (mblUs2 != -1) && (ablUs2 != -1) && (thlUs2 != -1) &&
	            (mblUs4 != -1) && (ablUs4 != -1) && (thlUs4 != -1)) {
	            result = new Calibration(ablUs1, ablUs2, ablUs4, mblUs1, mblUs2, mblUs4,
	                    				 thlUs1, thlUs2, thlUs4);
	        }
	    }
	    return result;
	}
	
	public static void main(String[] args) {
	    String str = "[calibration: ablUs1=1 mblUs1=11 thlUs1=111 ablUs2=2 mblUs2=22 thlUs2=222 ablUs4=4 mblUs4=44 thlUs4=444 ]";
	    Calibration c;
	    System.out.println(Calibration.fromString(str));
	    c = new Calibration(1,2,3,4,5,6,7,8,9);
	    System.out.println(c);
	    System.out.println(Calibration.fromString(c.toString()));
	}
}