/*
 * Created on 28-Jan-2005
 */
package uk.ac.lancs.relate;

import java.io.Serializable;
import java.util.StringTokenizer;

/**
 * @author Henoc AGBOTA
 */
public class SensorReading implements Cloneable, Serializable  {
	
	/** Number of transducers used by a relate dongle to get measurements */
    public final static int SENSORS_NUMBER = 3;
	/** Time Of flight value for sensor uS1 */
	protected int tofUs1;
	/** Time Of flight value for sensor uS2 */
	protected int tofUs2;
	/** Time Of flight value for sensor uS4 */
	protected int tofUs4;
	/** Signal Strength value for sensor uS1 */
	protected int ssUs1;
	/** Time Of flight value for sensor uS2 */
	protected int ssUs2;
	/** Time Of flight value for sensor uS4 */
	protected int ssUs4;
	/** "Valid signal" flag for sensor uS1 */
	protected boolean validUs1;
	/** "Valid signal" flag for sensor uS2 */
	protected boolean validUs2;
	/** "Valid signal" flag for sensor uS4 */
	protected boolean validUs4;

	public SensorReading() {
		super();
		this.tofUs1 = -1;
		this.tofUs2 = -1;
		this.tofUs4 = -1;
		this.ssUs1 = -1;
		this.ssUs2 = -1;
		this.ssUs4 = -1;
		this.validUs1 = false ;
		this.validUs2 = false ;
		this.validUs4 = false ;
	}
	/** 
	 * Default constructor
	 * @param tofUs1         TOF value for sensor uS1
	 * @param tofUs2         TOF value for sensor uS2
	 * @param tofUs4         TOF value for sensor uS4
	 * @param ssUs1          SS value for sensor uS1
	 * @param ssUs2          SS value for sensor uS2
	 * @param ssUs4          SS value for sensor uS4
	 * @param validUs1       "Valid signal" flag for sensor uS1
	 * @param validUs2       "Valid signal" flag for sensor uS2
	 * @param validUs4       "Valid signal" flag for sensor uS4
	 */
	public SensorReading(int tofUs1,int tofUs2,int tofUs4,int ssUs1,
			int ssUs2,int ssUs4,boolean validUs1,boolean validUs2,boolean validUs4) {
		super();
		this.tofUs1 = tofUs1;
		this.tofUs2 = tofUs2;
		this.tofUs4 = tofUs4;
		this.ssUs1 = ssUs1;
		this.ssUs2 = ssUs2;
		this.ssUs4 = ssUs4;
		this.validUs1 = validUs1;
		this.validUs2 = validUs2;
		this.validUs4 = validUs4;
	}

	/**
	 * @param sensorId the id of the corresponding uS sensor.
	 * @return the Time Of Flight value for the corresponding sensor
	 * or -1 if sensorId is invalid.
	 */
	public int getTOF(int sensorId) {
		int result = -1 ;
		if (sensorId == 1)
			result = this.tofUs1 ;
		if (sensorId == 2)
			result = this.tofUs2 ;
		if (sensorId == 4)
			result = this.tofUs4 ;
		return result ;
	}

	/**
	 * @param sensorId the id of the corresponding uS sensor.
	 * @return the Signal Strength value for the corresponding sensor
	 * or -1 if sensorId is invalid.
	 */
	public int getSS(int sensorId) {
		int result = -1 ;
		if (sensorId == 1)
			result = this.ssUs1 ;
		if (sensorId == 2)
			result = this.ssUs2 ;
		if (sensorId == 4)
			result = this.ssUs4 ;
		return result ;
	}

	/**
	 * @param sensorId the id of the corresponding uS sensor.
	 * @return true if the measurement taken by the corresponding sensor 
	 * is valid or or false otherwies or if sensorId is invalid.
	 */
	public boolean getValidityFlag(int sensorId) {
		boolean result = false ;
		if (sensorId == 1)
			result = this.validUs1 ;
		if (sensorId == 2)
			result = this.validUs2 ;
		if (sensorId == 4)
			result = this.validUs4 ;
		return result ;
	}

	/**
	 * implement clone interface
	 * @author Henoc AGBOTA
	 */
	public Object clone() {
		return new SensorReading(this.getTOF(1),this.getTOF(2),this.getTOF(4),
				this.getSS(1),this.getSS(2),this.getSS(4),this.getValidityFlag(1),
				this.getValidityFlag(2),this.getValidityFlag(4)) ;
	}

	/**
	 * set new TOF value
	 * @param sensorId The id of the corresponding sensor.
	 * @param tofUS new TOF for the corresponding sensor.
	 */
	public void setTOF(int sensorId, int tofUs) {
		if (sensorId == 1)
			this.tofUs1 = tofUs ;
		if (sensorId == 2)
			this.tofUs2 = tofUs ;
		if (sensorId == 4)
			this.tofUs4 = tofUs ;
	}

	/**
	 * set new SS value
	 * @param sensorId The id of the corresponding sensor.
	 * @param ssUs new SS for the corresponding sensor.
	 */
	public void setSS(int sensorId, int ssUs) {
		if (sensorId == 1)
			this.ssUs1 = ssUs ;
		if (sensorId == 2)
			this.ssUs2 = ssUs ;
		if (sensorId == 4)
			this.ssUs4 = ssUs ;
	}

	/**
	 * set new validity flag
	 * @param sensorId The id of the corresponding sensor.
	 * @param validUs new validity flag for the corresponding sensor.
	 */
	public void setValidityFlag(int sensorId, boolean validUs) {
		if (sensorId == 1)
			this.validUs1 = validUs ;
		if (sensorId == 2)
			this.validUs2 = validUs ;
		if (sensorId == 4)
			this.validUs4 = validUs ;
	}
	
	/**
	 * equality check: if two SensorReading objects have the same TOF value,
	 * SS value and valiity flag for all three sensors uS1, uS2, uS4
	 * @author Henoc AGBOTA
	 * @return true if two SensorReading are equal
	 */
	public boolean equals(SensorReading sensorReading) {
		boolean result = false;
		int i ;
		if (sensorReading != null) {
			result = true ;
			for(i = 0; i < SENSORS_NUMBER; i++){
				result = result && (getTOF(i+1) == sensorReading.getTOF(i+1)) ;
				result = result && (getSS(i+1) == 	sensorReading.getSS(i+1)) ;
				result = result && (getValidityFlag(i+1) == 
					sensorReading.getValidityFlag(i+1)) ;
				if (!result)
					break ;
			}
		}
		return result;
	}

	/** pretty print */
	public String toString() {
	    StringBuffer result = new StringBuffer(100);
	    result.append("[sr: tof=( ");
	    result.append(getTOF(1)+",");
	    result.append(getTOF(2)+",");
	    result.append(getTOF(4)+" )");
	    result.append(" ss=( ");
	    result.append(getSS(1)+",");
	    result.append(getSS(2)+",");
	    result.append(getSS(4)+" )");
	    result.append(" v=( "); 
	    result.append(getValidityFlag(1)+",") ;
	    result.append(getValidityFlag(2)+",") ;
	    result.append(getValidityFlag(4)+" )") ;
	    result.append(" ]");
	    return result.toString();
	}

	/** parsing from String */
	public static SensorReading fromString(String what) {
		/*System.out.println("[SensorReading] Warning: to be re-done") ;*/
		SensorReading result = null;
		StringTokenizer tokenizer = null;
		int i;
		String token;
		
		if (what != null) {
			tokenizer = new StringTokenizer(what);
			try {
				result = new SensorReading() ;
				if ("[sr:".equals(tokenizer.nextToken())) {
					/* Sensor uS1 */
					/* TOF */
					token = tokenizer.nextToken();
					token = tokenizer.nextToken(" ,");
					result.setTOF(1,Integer.parseInt(token));
					token = tokenizer.nextToken(",");
					result.setTOF(2,Integer.parseInt(token));
					token = tokenizer.nextToken(", ");
					result.setTOF(4,Integer.parseInt(token));
					/* SS */
					token = tokenizer.nextToken("(");
					token = tokenizer.nextToken(" ");
					token = tokenizer.nextToken(" ,");
					result.setSS(1,Integer.parseInt(token));
					token = tokenizer.nextToken(",");
					result.setSS(2,Integer.parseInt(token));
					token = tokenizer.nextToken(", ");
					result.setSS(4,Integer.parseInt(token));
					/* Validity*/
					token = tokenizer.nextToken("(");
					token = tokenizer.nextToken(" ");
					token = tokenizer.nextToken(" ,");
					result.setValidityFlag(1,token.equals("true"));
					token = tokenizer.nextToken(",");
					result.setValidityFlag(2,token.equals("true"));
					token = tokenizer.nextToken(", ");
					result.setValidityFlag(4,token.equals("true"));
				}
			} catch (Exception e) {
				result = null;
				e.printStackTrace() ;
			}
		}
		return result;
	}
	
	/** testing */
	public static void main(String[] args) {
		SensorReading sensorReading = new SensorReading(1140,2459,1139, 211,207,176, true,false, true);
	    System.out.println(sensorReading);
	    SensorReading sensorReading1 = SensorReading.fromString(sensorReading.toString());
	    System.out.println(sensorReading1);
	}
}