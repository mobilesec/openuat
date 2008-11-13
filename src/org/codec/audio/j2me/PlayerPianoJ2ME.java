/* Copyright Iulia Ion
 * File created 2008-08-01
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.codec.audio.j2me;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.microedition.media.control.ToneControl;

import org.codec.audio.common.CodecBitArray;

public class PlayerPianoJ2ME {
    // These are some MIDI constants from the spec.  They aren't defined
    // for us in javax.sound.midi.
    public static final int DAMPER_PEDAL = 64;
    public static final int DAMPER_ON = 127;
    public static final int DAMPER_OFF = 0;
    public static final int END_OF_TRACK = 47;
    
    
    static final String[  ] Chords = {
    	"A ", "Ab ", 
    	"B ", "Bb ",
    	"C ", "Cb ",
    	"D ", "Db ",
    	"E ", "Eb ",
    	"F ", "Fb ",
    	"G ", "Gb ",
    	"> ", ". "
    };
    
    public static String MakeInput(byte[] in){
    	
    	CodecBitArray bits = new CodecBitArray(in);
    	String out= new String();
    	int octave=0;
    	    
    	boolean up = true;
    	for(int i=0;i<20;i+=2){
    		if(i!=18){
    			if(bits.ValueFour(i+1)>=bits.ValueFour(i+3)){
    				if(octave<5){
    					out=out+"+ ";
    					octave++;
    				}
    				else{
    					octave=0;
    					out=out+"-----";
    				}
    			
    			}
    			
    			else{
    				if(octave>-5){
    					out=out+"- ";
    					octave--;	
    				}
    				else{
    					octave=0;
    					out=out+"++++";
    				}
    			}
    		}	
    		
    		out= out +Chords[bits.ValueFour(i)];
    		/*if ((i+1)%5==0){
    			if(up)
    				out=out+"++ ";
    			else
    				out=out+"-- ";
    			up=!up;
    		}*/
    			
    		
    	}
    	
    	out="/2 "+out;
    	System.out.println("OUT: "+out);
    	return out;
    }

    public static byte [] PlayerPiano(String score) throws IOException
{
    int instrument = 0;
    byte tempo =  60;

    char[  ] notes = score.toCharArray( );
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(new byte[]{
    		//(Sequence.PPQ, 16);
    		ToneControl.VERSION, 1,   // version 1
    		ToneControl.TEMPO, tempo});
    // 16 ticks per quarter note. 
   
    int n = 0; // current character in notes[  ] array
    int t = 0; // time in ticks for the composition

    // These values persist and apply to all notes 'till changed
    int notelength = 16; // default to quarter notes
    int velocity = 64;   // default to middle volume
    
    
    int basekey = ToneControl.C4;    // 60 is middle C. Adjusted up and down by octave
    boolean sustain = false;   // is the sustain pedal depressed?
    int numnotes = 0;    // How many notes in current chord?

    while(n < notes.length) {
        char c = notes[n++];

        if (c == '+') basekey += 12;        // increase octave
        else if (c == '-') basekey -= 12;   // decrease octave
        else if (c == '>') velocity += 16;  // increase volume;
        else if (c == '<') velocity -= 16;  // decrease volume;
        else if (c == '/') {
            char d = notes[n++];
            if (d == '2') notelength = 32;  // half note
            else if (d == '4') notelength = 16;  // quarter note
            else if (d == '8') notelength = 8;   // eighth note
            else if (d == '3' && notes[n++] == '2') notelength = 2;
            else if (d == '6' && notes[n++] == '4') notelength = 1;
            else if (d == '1') {
                if (n < notes.length && notes[n] == '6')
                    notelength = 4;    // 1/16th note
                else notelength = 64;  // whole note
            }
        }
        else if (c == 's') { //???
//            sustain = !sustain;
//            // Change the sustain setting for channel 0
//            ShortMessage m = new ShortMessage( );
//            m.setMessage(ShortMessage.CONTROL_CHANGE, 0,
//                         DAMPER_PEDAL, sustain?DAMPER_ON:DAMPER_OFF);
//            track.add(new MidiEvent(m, t));
        }
        else if (c >= 'A' && c <= 'G') {
            int key = basekey + offsets[c - 'A'];
            if (n < notes.length) {
                if (notes[n] == 'b') { // flat
                    key--; 
                    n++;
                }
                else if (notes[n] == '#') { // sharp
                    key++;
                    n++;
                }
            }

            addNote(t, notelength, key, velocity, out);
            numnotes++;
        }
        else if (c == ' ') {
            // Spaces separate groups of notes played at the same time.
            // But we ignore them unless they follow a note or notes.
            if (numnotes > 0) {
                t += notelength;
                numnotes = 0;
            }
        }
        else if (c == '.') { 
            // Rests are like spaces in that they force any previous
            // note to be output (since they are never part of chords)
            if (numnotes > 0) {
                t += notelength;
                numnotes = 0;
            }
            // Now add additional rest time
            t += notelength;
        }
    }
    return out.toByteArray();

}

	

static final int[  ] offsets = {  // add these amounts to the base value
    // A   B  C  D  E  F  G
      -4, -2, 0, 1, 3, 5, 7  
};

/*
 * This method parses the specified char[  ] of notes into a Track.
 * The musical notation is the following:
 * A-G:   A named note; Add b for flat and # for sharp.
 * +:     Move up one octave. Persists.
 * -:     Move down one octave.  Persists.
 * /1:    Notes are whole notes.  Persists 'till changed
 * /2:    Half notes
 * /4:    Quarter notes
 * /n:    N can also be 8, 16, 32, 64.
 * s:     Toggle sustain pedal on or off (initially off)
 * 
 * >:     Louder.  Persists
 * <:     Softer.  Persists
 * .:     Rest. Length depends on current length setting
 * Space: Play the previous note or notes; notes not separated by spaces
 *        are played at the same time
 */
//public static void addTrack(byte [] s, int instrument, int tempo,
//                            char[  ] notes)
//    throws InvalidMidiDataException
//{
////    Track track = s.createTrack( );  // Begin with a new track
//
//    // Set the instrument on channel 0
////    ShortMessage sm = new ShortMessage( );
////    sm.setMessage(ShortMessage.PROGRAM_CHANGE, 0, instrument, 0);
////    track.add(new MidiEvent(sm, 0));
//
//    int n = 0; // current character in notes[  ] array
//    int t = 0; // time in ticks for the composition
//
//    // These values persist and apply to all notes 'till changed
//    int notelength = 16; // default to quarter notes
//    int velocity = 64;   // default to middle volume
//    int basekey = 60;    // 60 is middle C. Adjusted up and down by octave
//    boolean sustain = false;   // is the sustain pedal depressed?
//    int numnotes = 0;    // How many notes in current chord?
//
//    while(n < notes.length) {
//        char c = notes[n++];
//
//        if (c == '+') basekey += 12;        // increase octave
//        else if (c == '-') basekey -= 12;   // decrease octave
//        else if (c == '>') velocity += 16;  // increase volume;
//        else if (c == '<') velocity -= 16;  // decrease volume;
//        else if (c == '/') {
//            char d = notes[n++];
//            if (d == '2') notelength = 32;  // half note
//            else if (d == '4') notelength = 16;  // quarter note
//            else if (d == '8') notelength = 8;   // eighth note
//            else if (d == '3' && notes[n++] == '2') notelength = 2;
//            else if (d == '6' && notes[n++] == '4') notelength = 1;
//            else if (d == '1') {
//                if (n < notes.length && notes[n] == '6')
//                    notelength = 4;    // 1/16th note
//                else notelength = 64;  // whole note
//            }
//        }
//        else if (c == 's') {
//            sustain = !sustain;
//            // Change the sustain setting for channel 0
//            ShortMessage m = new ShortMessage( );
//            m.setMessage(ShortMessage.CONTROL_CHANGE, 0,
//                         DAMPER_PEDAL, sustain?DAMPER_ON:DAMPER_OFF);
//            track.add(new MidiEvent(m, t));
//        }
//        else if (c >= 'A' && c <= 'G') {
//            int key = basekey + offsets[c - 'A'];
//            if (n < notes.length) {
//                if (notes[n] == 'b') { // flat
//                    key--; 
//                    n++;
//                }
//                else if (notes[n] == '#') { // sharp
//                    key++;
//                    n++;
//                }
//            }
//
//            addNote(track, t, notelength, key, velocity);
//            numnotes++;
//        }
//        else if (c == ' ') {
//            // Spaces separate groups of notes played at the same time.
//            // But we ignore them unless they follow a note or notes.
//            if (numnotes > 0) {
//                t += notelength;
//                numnotes = 0;
//            }
//        }
//        else if (c == '.') { 
//            // Rests are like spaces in that they force any previous
//            // note to be output (since they are never part of chords)
//            if (numnotes > 0) {
//                t += notelength;
//                numnotes = 0;
//            }
//            // Now add additional rest time
//            t += notelength;
//        }
//    }
//}
private static int ticksSoFar = 0;

//A convenience method to add a note to the track on channel 0
public static void addNote(int startTick, int tickLength, int key, int velocity, ByteArrayOutputStream out) throws IOException
{
	//	System.out.println(startTick + " : "+ tickLength + " : " + key + " : "+ velocity);
	//	System.out.println("Ticks so far: "+ticksSoFar);
	if(ticksSoFar < startTick){
		out.write(new byte []{ToneControl.SILENCE,  (byte)(startTick-ticksSoFar)});
		//System.out.println(velocity);
		//		System.out.println("added silence");
	}
	out.write(new byte []{ToneControl.SET_VOLUME,  (byte) velocity, (byte)key,  (byte) tickLength});
	ticksSoFar = (startTick + tickLength);
	//	System.out.println("tickLength: "+ tickLength);
	//	System.out.println("ticksSoFar: "+ticksSoFar);
}
}