package uk.ac.lancs.relate;

import java.util.*;

/**
 ** MessageQueue is used for asynchronous message passing
 ** between several threads running in parallel but cooperating,
 ** such as the Server and the Threads handling the connections
 ** to various Clients.
 **/
public class MessageQueue {
    Vector queue = null;					// The queue itself
    
    /**
     ** Default constructor
     **/
    public MessageQueue() {
        queue = new Vector();
    }
    
    /**
     ** Add element to the end of the list
     **/
    public synchronized void addMessage(Object o) {
        queue.addElement(o);
        notify();
    }
    
    /**
     ** Get element
     **/
    public synchronized Object getMessage() {
        Object result = null;
        
        if (queue.size() > 0) {
            try {
                if (queue.size() > 0)
                    result = queue.firstElement();
                queue.removeElementAt(0);
            } catch (Exception e) {
                result = null;
            }
        }
        return result;
    }
    
    /**
     **  Get all messages
     **/
    public synchronized Object[] getAllMessages() {
        Object[] result = null;
        
        if (queue.size() > 0) {
            result = queue.toArray();
            queue.clear();
        }
        return result;
    }
    
    /**
     ** Check whether queue is empty
     **/
    public synchronized boolean isEmpty() {
        return queue.isEmpty();
    }
    
    /**
     ** Wait for incoming message
     **/
    public synchronized void waitForMessage(int millis) {
        try {
            wait(millis);
        } catch (InterruptedException e) {
        }
    }
    
}