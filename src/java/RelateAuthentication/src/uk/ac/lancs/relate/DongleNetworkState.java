/*
 * Created on 03-Apr-2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package uk.ac.lancs.relate;

import java.util.*;
/**
 * @author Henoc AGBOTA
 *
 */
public class DongleNetworkState {
	protected int relateTime ;
	protected int numberOfEntries ;
	protected Vector dicoveryListIds = null ;
	protected Vector dicoveryListTimes = null ;
	protected int awareconSyncRate ;
	
	public DongleNetworkState(int relateTime, int numberOfEntries,
			Vector dicoveryListIds, Vector dicoveryListTimes,
			int awareconSyncRate) {
		
		this.relateTime = relateTime ;
		this.numberOfEntries = numberOfEntries ;
		this.dicoveryListIds = dicoveryListIds ;
		this.dicoveryListTimes = dicoveryListTimes ;
		this.awareconSyncRate = awareconSyncRate ;
	}
	
	public int getRelateTime(){
		return relateTime ;
	}
	
	public void setRelateTime(int relateTime) {
		this.relateTime = relateTime ;
	}
	
	public int getNumberOfEntries(){
		return numberOfEntries ;
	}
	
	public void setNumberOfEntries(int numberOfEntries) {
		this.numberOfEntries = numberOfEntries ;
	}
	
	public Vector getDicoveryListIds(){
		return dicoveryListIds ;
	}
	
	public void setDicoveryListIds(Vector dicoveryListIds) {
		this.dicoveryListIds = dicoveryListIds ;
	}
	
	public Vector getDicoveryListTimes(){
		return dicoveryListTimes ;
	}
	
	public void setDicoveryListTimes(Vector dicoveryListTimes) {
		this.dicoveryListTimes = dicoveryListTimes ;
	}
	
	
	public int getAwareconSyncRate(){
		return awareconSyncRate ;
	}
	
	public void setAwareconSyncRate(int awareconSyncRate) {
		this.awareconSyncRate = awareconSyncRate ;
	}
	
	
	public boolean equals(DongleNetworkState dnState) {
		boolean result = false;
		if (dnState != null) {
			result = true ;
			result = result && 
			(getRelateTime() == 
				dnState.getRelateTime()) ;
			result = result && 
			(getNumberOfEntries() == 
				dnState.getNumberOfEntries()) ;
			result = result && 
			(getDicoveryListIds() == 
				dnState.getDicoveryListIds()) ;
			result = result && 
			(getDicoveryListTimes() == 
				dnState.getDicoveryListTimes()) ;
			result = result && 
			(getAwareconSyncRate() == 
				dnState.getAwareconSyncRate()) ;
		}
		return result;
	}
	
	/** pretty print */
	public String toString() {
		StringBuffer result = new StringBuffer(100+5*numberOfEntries*2);
		result.append("[DNS: rt=");
		result.append(getRelateTime());
		result.append(" n=");
		result.append(getNumberOfEntries());
		result.append(" aws="); 
		result.append(getAwareconSyncRate());
		result.append(" ids=");
		result.append(printVector(dicoveryListIds));
		result.append(" tms=");
		result.append(printVector(dicoveryListTimes));
		result.append(" ]");
		return result.toString();
	}
	
	public String printVector(Vector v){
		String result = "" ;
		int i ;
		if (v != null) {
			result = "(" ;
            for (i=0; i<v.size(); i++) {
            	result+= ((i == v.size()-1) ?
            			(""+(Integer)v.elementAt(i)) :
            				((Integer)v.elementAt(i)).intValue()+", "
							);
            }
		}		
		result += ")" ;	
		return result ;
	}
	
	/** string parsing (A faire..)*/
	public static DongleNetworkState fromString(String str) {
		return null ;
	}
	
	
	/**
	 * implement clone interface
	 * @author Henoc AGBOTA
	 */
	public Object clone() {
		return new DongleNetworkState(this.getRelateTime(), this.getNumberOfEntries(),
				this.getDicoveryListIds(), this.getDicoveryListTimes(),
				this.getAwareconSyncRate()) ;
	}
	
	
	public static void main(String[] args) {
	}
}
