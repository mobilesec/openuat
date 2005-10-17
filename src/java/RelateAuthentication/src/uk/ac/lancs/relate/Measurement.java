package uk.ac.lancs.relate;

import java.io.Serializable;
import java.util.*;

//
//  Measurement.java
//  RelateMeetingSupport
//
//  Created by Christian Kray on 28/01/2004.
//  Copyright (c) 2004 __MyCompanyName__. All rights reserved.
//

public class Measurement implements Cloneable, Serializable {
	
    /** ID of the object to which this measurement is relative */
    protected int relatum;
	/** ID of object that this measurement stems from */
	protected int id;
	/** distance in millimeters */
	protected double distance;
	/** pre-computed angle (in degrees between 0 and 360) */
	protected double angle;
	/** confidence value for distance */
	protected double confidenceDistance;
	/** confidence value for angle */
	protected double confidenceAngle;
	/** timeStamp */
	protected long timeStamp;
	/** relate time */
	protected int relateTime;
	/** number of transducers */
	protected int transducers;
	/** in motion */
	protected Boolean moving = null;
	/** Sensor info if local measurement */
	protected SensorReading sensorReading = null;
	/** Is this measurement obtained by IP ? */
	protected Boolean ipShared = null;
	
	/** Empty constructor */
	public Measurement() {
	    relatum = -1;
		id = -1;
		distance = -1;
		angle = -1;
		confidenceDistance = -1.0;
		confidenceAngle = -1.0;
		timeStamp = -1;
		relateTime = -1;
		transducers = -1;
		moving = null;
		sensorReading = null;
		ipShared = null;
	}
	
	/** Fully qualified constructor */
	public Measurement(int r, int i, double d, double a, double cd,
			double ca, long t, int rt, int tr) {
		relatum = r;
	    id = i;
		distance = d;
		angle = a;
		confidenceDistance = cd;
		confidenceAngle = ca;
		timeStamp = t;
		relateTime = rt;
		transducers = tr;
		moving = null;
		sensorReading = null;
		ipShared = null;
	}
	
	/** Fully qualified constructor */
	public Measurement(int r, int i, double d, double a, double cd,
			double ca, long t, int rt, int tr, boolean inMotion) {
		relatum = r;
	    id = i;
		distance = d;
		angle = a;
		confidenceDistance = cd;
		confidenceAngle = ca;
		timeStamp = t;
		relateTime = rt;
		transducers = tr;
		moving = new Boolean(inMotion);
		sensorReading = null;
		ipShared = null;
	}
	
	/** Fully qualified constructor */
	public Measurement(int r, int i, double d, double a, double cd,
			double ca, long t, int rt, int tr, Boolean inMotion) {
		relatum = r;
	    id = i;
		distance = d;
		angle = a;
		confidenceDistance = cd;
		confidenceAngle = ca;
		timeStamp = t;
		relateTime = rt;
		transducers = tr;
		moving = inMotion;
		sensorReading = null;
		ipShared = null;
	}

	/** Fully qualified constructor */
	public Measurement(int r, int i, double d, double a, double cd,
			double ca, long t, int rt, int tr, 
			boolean inMotion, SensorReading sensorReading) {
		relatum = r;
	    id = i;
		distance = d;
		angle = a;
		confidenceDistance = cd;
		confidenceAngle = ca;
		timeStamp = t;
		relateTime = rt;
		transducers = tr;
		moving = new Boolean(inMotion);
		this.sensorReading = sensorReading;
		ipShared = null;
	}
	
	/** Fully qualified constructor */
	public Measurement(int r, int i, double d, double a, double cd,
			double ca, long t, int rt, int tr, 
			Boolean inMotion,SensorReading sensorReading) {
		relatum = r;
	    id = i;
		distance = d;
		angle = a;
		confidenceDistance = cd;
		confidenceAngle = ca;
		timeStamp = t;
		relateTime = rt;
		transducers = tr;
		moving = inMotion;
		this.sensorReading = sensorReading;
		ipShared = null;
	}
	
	
	/** Fully qualified constructor (with number of receiving 
		transducers as parameter instead of confidence value) */
	public Measurement(int r, int i, double d, double a, int transducers, long t, int rt) {
	    relatum = r;
		id = i;
		distance = d;
		angle = a;
		//confidenceDistance = (transducers > 3 ? 1.0 : transducers/3.0);
		timeStamp = t;
		//confidenceAngle = (transducers > 3 ? 1.0 : transducers/3.0);
		relateTime = rt;
		this.transducers = transducers;
		switch (transducers) {
		case 1:
			confidenceDistance = 65;
			confidenceAngle = 35;
			break;
		case 2:
			confidenceDistance = 35;
			confidenceAngle = 15;
			break;
		case 3:
			confidenceDistance = 25;
			confidenceAngle = 15;
			break;
		default:
			confidenceDistance = -1.0;
			confidenceAngle = -1.0;
			break;
		}
		moving = null;
		sensorReading = null;
		ipShared = null;
	}
	
	/** Fully qualified constructor (with number of receiving 
	transducers as parameter instead of confidence value) */
	public Measurement(int r, int i, double d, double a, int transducers, long t, int rt,
			boolean inMotion) {
		relatum = r;
		id = i;
		distance = d;
		angle = a;
		//confidenceDistance = (transducers > 3 ? 1.0 : transducers/3.0);
		timeStamp = t;
		//confidenceAngle = (transducers > 3 ? 1.0 : transducers/3.0);
		relateTime = rt;
		this.transducers = transducers;
		switch (transducers) {
		case 1:
			confidenceDistance = 65;
			confidenceAngle = 35;
			break;
		case 2:
			confidenceDistance = 35;
			confidenceAngle = 15;
			break;
		case 3:
			confidenceDistance = 25;
			confidenceAngle = 15;
			break;
		default:
			confidenceDistance = -1.0;
			confidenceAngle = -1.0;
			break;
		}
		moving = new Boolean(inMotion);
		sensorReading = null;
		ipShared = null;
	}

	/** Fully qualified constructor (with number of receiving 
	transducers as parameter instead of confidence value) */
	public Measurement(int r, int i, double d, double a, int transducers, long t, int rt,
			boolean inMotion, SensorReading sensorReading) {
		relatum = r;
		id = i;
		distance = d;
		angle = a;
		//confidenceDistance = (transducers > 3 ? 1.0 : transducers/3.0);
		timeStamp = t;
		//confidenceAngle = (transducers > 3 ? 1.0 : transducers/3.0);
		relateTime = rt;
		this.transducers = transducers;
		switch (transducers) {
		case 1:
			confidenceDistance = 65;
			confidenceAngle = 35;
			break;
		case 2:
			confidenceDistance = 35;
			confidenceAngle = 15;
			break;
		case 3:
			confidenceDistance = 25;
			confidenceAngle = 15;
			break;
		default:
			confidenceDistance = -1.0;
			confidenceAngle = -1.0;
			break;
		}
		moving = new Boolean(inMotion);
		this.sensorReading = sensorReading;
		ipShared = null;
	}
	
	/*New constructors with ipShared field*/

	/** Fully qualified constructor */
	public Measurement(int r, int i, double d, double a, double cd,
			double ca, long t, int rt, int tr, 
			Boolean inMotion,Boolean ipShared) {
		relatum = r;
	    id = i;
		distance = d;
		angle = a;
		confidenceDistance = cd;
		confidenceAngle = ca;
		timeStamp = t;
		relateTime = rt;
		transducers = tr;
		moving = inMotion;
		this.ipShared = ipShared;
	}
	

	/** Fully qualified constructor */
	public Measurement(int r, int i, double d, double a, double cd,
			double ca, long t, int rt, int tr, 
			Boolean inMotion,SensorReading sensorReading,Boolean ipShared) {
		relatum = r;
	    id = i;
		distance = d;
		angle = a;
		confidenceDistance = cd;
		confidenceAngle = ca;
		timeStamp = t;
		relateTime = rt;
		transducers = tr;
		moving = inMotion;
		this.sensorReading = sensorReading;
		this.ipShared = ipShared;
	}
	
	
	/** Fully qualified constructor (with number of receiving 
		transducers as parameter instead of confidence value) */
	public Measurement(int r, int i, double d, double a, int transducers, 
			long t, int rt, Boolean inMotion, Boolean ipShared) {
	    relatum = r;
		id = i;
		distance = d;
		angle = a;
		//confidenceDistance = (transducers > 3 ? 1.0 : transducers/3.0);
		timeStamp = t;
		//confidenceAngle = (transducers > 3 ? 1.0 : transducers/3.0);
		relateTime = rt;
		this.transducers = transducers;
		switch (transducers) {
		case 1:
			confidenceDistance = 65;
			confidenceAngle = 35;
			break;
		case 2:
			confidenceDistance = 35;
			confidenceAngle = 15;
			break;
		case 3:
			confidenceDistance = 25;
			confidenceAngle = 15;
			break;
		default:
			confidenceDistance = -1.0;
			confidenceAngle = -1.0;
			break;
		}
		moving = inMotion;
		sensorReading = null;
		this.ipShared = ipShared;
	}

	/** Fully qualified constructor (with number of receiving 
		transducers as parameter instead of confidence value) */
	public Measurement(int r, int i, double d, double a, int transducers, 
			long t, int rt, Boolean inMotion, SensorReading sensorReading, Boolean ipShared) {
	    relatum = r;
		id = i;
		distance = d;
		angle = a;
		//confidenceDistance = (transducers > 3 ? 1.0 : transducers/3.0);
		timeStamp = t;
		//confidenceAngle = (transducers > 3 ? 1.0 : transducers/3.0);
		relateTime = rt;
		this.transducers = transducers;
		switch (transducers) {
		case 1:
			confidenceDistance = 65;
			confidenceAngle = 35;
			break;
		case 2:
			confidenceDistance = 35;
			confidenceAngle = 15;
			break;
		case 3:
			confidenceDistance = 25;
			confidenceAngle = 15;
			break;
		default:
			confidenceDistance = -1.0;
			confidenceAngle = -1.0;
			break;
		}
		moving = inMotion;
		this.sensorReading = sensorReading;
		this.ipShared = ipShared;
	}
	
	/** Compare the measurement to another measurement 
	 ** @param  m the other measurement to compare
	 ** @return true if both measurements contain the same data
	 */
	public boolean sameAs(Measurement m) {
	    boolean result = false;
	    if (m != null) {
	        result = (relatum == m.getRelatum()) &&
	        		 (id == m.getId()) &&
	        		 (distance == m.getDistance()) &&
	        		 (angle == m.getAngle()) &&
	        		 (confidenceDistance == m.getConfidenceDistance()) &&
	        		 (confidenceAngle == m.getConfidenceAngle()) &&
	        		 (timeStamp == m.getTimeStamp()) &&
	        		 (relateTime == m.getRelateTime());
	    }
	    return result;
	}

    /**
     * @return Returns ipShared flag.
     */
    public Boolean getIpShared() {
        return ipShared;
    }

	/** Accessor */
	public void setIpShared(boolean ipShared) {
		this.ipShared = new Boolean(ipShared);
	}

	/** Accessor */
	public void setIpShared(Boolean ipShared) {
		this.ipShared = ipShared;
	}
	
    /**
     * @return Returns a SensorReading object.
     */
    public SensorReading getSensorReading() {
        return sensorReading;
    }

	/** Accessor */
	public void setSensorReading(SensorReading sensorReading) {
		this.sensorReading = sensorReading;
	}
	
	/** Checks whether this instance contains a valid measurement
	 ** (i.e. its distance value is below 4000 mm).
	 ** @return true if measurement is valid
	 */
	public boolean isValid() {
	    return ((distance < 4000) && (transducers != 0));
	}
	
    /**
     * @return Returns the relateTime.
     */
    public int getRelateTime() {
        return relateTime;
    }
    /**
     * @param relateTime The relateTime to set.
     */
    public void setRelateTime(int relateTime) {
        this.relateTime = relateTime;
    }
    /**
     * @return Returns the id.
     */
    public int getId() {
        return id;
    }
    /**
     * @param id The id to set.
     */
    public void setId(int id) {
        this.id = id;
    }
    /**
     * @return Returns the relatum.
     */
    public int getRelatum() {
        return relatum;
    }
    /**
     * @param relatum The relatum to set.
     */
    public void setRelatum(int relatum) {
        this.relatum = relatum;
    }
	/** Accessor */
	public double getDistance() {
		return distance;
	}
	/** Accessor */
	public double getAngle() {
		return angle;
	}
	/** Accessor */
	public double getConfidenceDistance() {
		return confidenceDistance;
	}
	/** Accessor */
	public double getConfidenceAngle() {
		return confidenceAngle;
	}
	/** Accessor */
	public long getTimeStamp() {
		return timeStamp;
	}
	
	/** Accessor */
	public void setDistance(double t) {
		distance = t;
	}
	/** Accessor */
	public void setAngle(double a) {
		angle = a;
	}
	/** Accessor */
	public void setConfidenceDistance(double m) {
		confidenceDistance = m;
	}
	/** Accessor */
	public void setConfidenceAngle(double m) {
		confidenceAngle = m;
	}
	/** Accessor */
	public void setTimeStamp(long t) {
		timeStamp = t;
	}
    /**
     * @return Returns the transducers.
     */
    public int getTransducers() {
        return transducers;
    }
    /**
     * @param transducers The transducers to set.
     */
    public void setTransducers(int transducers) {
        this.transducers = transducers;
    }

	/**
	 * @return Returns the moving.
	 */
	public Boolean getMoving() {
		return moving;
	}
	/**
	 * @param moving The moving to set.
	 */
	public void setMoving(boolean moving) {
		this.moving = new Boolean(moving);
	}
	public void setMoving(Boolean moving) {
		this.moving = moving;
	}
	
	/** Check whether this measurement refers to the same
	 ** two devices as another one.	
	 ** @param m another measurement
	 ** @return true if argument refers to same two devices */
	public boolean sameDevicesAs(Measurement m) {
		boolean result = false;
		if (m != null) {
			result = (relatum == m.getRelatum()) &&
					 (id == m.getId());
		}
		return result;
	}
	
	/** cloning */
	public Object clone() {
		return new Measurement(getRelatum(), getId(), getDistance(), getAngle(),
							   getConfidenceDistance(), getConfidenceAngle(),
							   getTimeStamp(), getRelateTime(), getTransducers(),
							   getMoving(),getSensorReading(),getIpShared());
	}
	
	/** pretty print */
	public String toString() {
	    StringBuffer result = new StringBuffer(250);
	    result.append("[measurement: rx=");
	    result.append(getRelatum());
	    result.append(" tx=");
	    result.append(getId());
	    result.append(" d="); 
	    result.append(getDistance());
	    result.append(" a=");
	    result.append(getAngle()); 
	    result.append(" rt=");
	    result.append(getRelateTime());
	    result.append(" tr=");
	    result.append(getTransducers());
	    result.append(" t=");
	    result.append(getTimeStamp());
	    result.append(" cd="); 
	    result.append(shorten(getConfidenceDistance()));
	    result.append(" ca="); 
	    result.append(shorten(getConfidenceAngle()));
	    if (getMoving() != null) {
	    	result.append(" move="); 
	    	result.append(getMoving().booleanValue());
	    } else
	    	result.append(" move=undefined");
	    if (getSensorReading() != null) {
	    	result.append(" "+getSensorReading().toString());
	    } else
	    	result.append(" sr=N/A");
	    if (getIpShared() != null) {
	    	result.append(" ip="+(getIpShared().booleanValue()?1:0)) ;
	    } else
	    	result.append(" ip=0");
	    result.append(" ]");
	    return result.toString();
	}
	
	public static String shorten(double d) {
	    String result = "" + d;
	    int index = result.indexOf('.');
	    if (index > -1) {
	        if (index < (result.length()-2))
	            result = result.substring(0, index + 2);
	    }
	    return result;
	}
	
	/** parsing from String */
    public static Measurement fromString(String what) {
    	Measurement result = null;
    	StringTokenizer tokenizer = null;
    	int i;
    	String token;
    	
    	if (what != null) {
    		tokenizer = new StringTokenizer(what);
    		try {
    			result = new Measurement();
    			if ("[measurement:".equals(tokenizer.nextToken())) {
    				/* relatum */
    				token = tokenizer.nextToken();
    				token = token.substring(3);
    				result.setRelatum(Integer.parseInt(token));
    				/* target */
    				token = tokenizer.nextToken();
    				token = token.substring(3);
    				result.setId(Integer.parseInt(token));
    				/* distance */
    				token = tokenizer.nextToken();
    				token = token.substring(2);
    				result.setDistance(Double.parseDouble(token));
    				/* angle */
    				token = tokenizer.nextToken();
    				token = token.substring(2);
    				result.setAngle(Double.parseDouble(token));
    				/* relate time */
    				token = tokenizer.nextToken();
    				token = token.substring(3);
    				result.setRelateTime(Integer.parseInt(token));
    				/* number transducers */
    				token = tokenizer.nextToken();
    				token = token.substring(3);
    				result.setTransducers(Integer.parseInt(token));
    				/* timeStamp */
    				token = tokenizer.nextToken();
    				token = token.substring(2);
    				result.setTimeStamp(Long.parseLong(token));
    				/* confidence distance */
    				token = tokenizer.nextToken();
    				token = token.substring(3);
    				result.setConfidenceDistance(Double.parseDouble(token));
    				/* confidence angle */
    				token = tokenizer.nextToken();
    				token = token.substring(3);
    				result.setConfidenceAngle(Double.parseDouble(token));
    				/* ball switch data */
    				try {
    				    if (tokenizer.hasMoreTokens()) {
    				        token = tokenizer.nextToken();
    				        token = token.substring(5);
    				        if ("undefined".equalsIgnoreCase(token)) {
    				            result.setMoving(null);
    				        } else
    				            result.setMoving(Boolean.valueOf(token));
    				    }
    				} catch (Exception e) {
    				}
    				/* Sensor reading */
    				try {
    				    if (tokenizer.hasMoreTokens()) {
    				        token = tokenizer.nextToken("[]");
    				        token = tokenizer.nextToken("[]");
    				        token = "["+token+" ]" ;
    				        result.setSensorReading(SensorReading.fromString(token)) ;
    				    }
    				} catch (Exception e) {
    				}
    				/* ipShared */
    				try {
    				    if (tokenizer.hasMoreTokens()) {
    				        token = tokenizer.nextToken(" ]");
    				        token = token.substring(3);
    				        result.setIpShared(Boolean.valueOf((token.equals("0"))?"false":"true"));
    				    }
    				} catch (Exception e) {
    				}
    			}
    		} catch (Exception e) {
    			result = null;
    		}
    	}
    	return result;
    }
	
	/** testing */
	public static void main(String[] args) {
		SensorReading sensorReading = new SensorReading(1140,2459,1139, 211,207,176, true,false, true);
	    /*Measurement m = new Measurement(0, 3, 600.0, 180.0, 1.0, 0.0, 100000000, 255, 2,new Boolean(false),sensorReading,new Boolean(true));*/
	    Measurement m = new Measurement(0, 3, 600.0, 180.0, 1.0, 0.0, 100000000, 255, 2);
		/*Measurement m = new Measurement(0, 3, 600.0, 180.0, 1.0, 0.0, 100000000, 255, 2,false);*/
	    System.out.println(m);
	    Measurement m1 = Measurement.fromString(m.toString());
	    Measurement m2 = Measurement.fromString("[measurement: rx=0 tx=3 d=600.0 a=180.0 rt=255 tr=2 t=100000000 cd=1.0 ca=0.0 ]") ;
	    System.out.println(m1);
	}
}
