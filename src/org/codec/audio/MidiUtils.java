package org.codec.audio;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;

import org.codec.audio.common.CodecBitArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;



public class MidiUtils {

	private static final int	VELOCITY = 64;
	
    public static void PerformMidiFile(File inputFile){
    	try {
            Sequence sequence = MidiSystem.getSequence(inputFile);
        
            Sequencer sequencer = MidiSystem.getSequencer();
            sequencer.open();
            sequencer.setSequence(sequence);
            sequencer.setTempoInBPM(60);
        
            sequencer.start();
        } catch (MalformedURLException e) {
        } catch (IOException e) {
        } catch (MidiUnavailableException e) {
        } catch (InvalidMidiDataException e) {
        }
    }
	
     
	public static void writeMidiFile(File in, File out) throws IOException
	{
		
		FileOutputStream outputFile = new FileOutputStream(out);
		FileInputStream inputFile = new FileInputStream(in);
		byte [] bytes = new byte[16];
		
		
		inputFile.read(bytes);
		
		CodecBitArray bits = new CodecBitArray(bytes);
		
		
		Sequence	sequence = null;
		try
		{
			sequence = new Sequence(Sequence.PPQ, 16);
		}
		catch (InvalidMidiDataException e)
		{
			e.printStackTrace();
			System.exit(1);
		}

		Track	track = sequence.createTrack();

		for(int i=0;i<128;i++){
			if(bits.isSet(i)){
				track.add(createNoteOnEvent(i,i));
				track.add(createNoteOffEvent(i,i+1));

			}
				
		}
	
		try
			{
				MidiSystem.write(sequence, 0, outputFile);
			}
			catch (IOException e)
			{
				e.printStackTrace();
				System.exit(1);
			}
		}




	private static MidiEvent createNoteOnEvent(int nKey, long lTick)
	{
		return createNoteEvent(ShortMessage.NOTE_ON,
							   nKey,
							   VELOCITY,
							   lTick);
	}



	private static MidiEvent createNoteOffEvent(int nKey, long lTick)
	{
		return createNoteEvent(ShortMessage.NOTE_OFF,
							   nKey,
							   0,
							   lTick);
	}



	private static MidiEvent createNoteEvent(int nCommand,
											 int nKey,
											 int nVelocity,
											 long lTick)
	{
		ShortMessage	message = new ShortMessage();
		try
		{
			message.setMessage(nCommand,
							   0,	// always on channel 1
							   nKey,
							   nVelocity);
		}
		catch (InvalidMidiDataException e)
		{
			e.printStackTrace();
			System.exit(1);
		}
		MidiEvent	event = new MidiEvent(message,
										  lTick);
		return event;
	}

	
	
}
