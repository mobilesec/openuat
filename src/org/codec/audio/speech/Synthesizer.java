package org.codec.audio.speech;

import javax.speech.synthesis.Voice;
import javax.speech.synthesis.SynthesizerModeDesc;
import javax.speech.Central;
import javax.speech.EngineList;
import java.util.Locale;
import java.io.File;


public class Synthesizer {

     private static String noSynthesizerMessage() {
        String message =
            "No synthesizer created.  This may be the result of any\n" +
            "number of problems.  It's typically due to a missing\n" +
            "\"speech.properties\" file that should be at either of\n" +
            "these locations: \n\n";
        message += "user.home    : " + System.getProperty("user.home") + "\n";
        message += "java.home/lib: " + System.getProperty("java.home") +
	    File.separator + "lib\n\n" +
            "Another cause of this problem might be corrupt or missing\n" +
            "voice jar files in the freetts lib directory.  This problem\n" +
            "also sometimes arises when the freetts.jar file is corrupt\n" +
            "or missing.  Sorry about that.  Please check for these\n" +
            "various conditions and then try again.\n";
        return message;
    }

    private static void listAllVoices(String modeName) {
        System.out.println();
        System.out.println(
            "All " + modeName + " Mode JSAPI Synthesizers and Voices:");

        /* Create a template that tells JSAPI what kind of speech
         * synthesizer we are interested in.  In this case, we're
         * just looking for a general domain synthesizer for US
         * English.
         */
        SynthesizerModeDesc required = new SynthesizerModeDesc(
            null,      // engine name
            modeName,  // mode name
            Locale.US, // locale
            null,      // running
            null);     // voices

        /* Contact the primary entry point for JSAPI, which is
         * the Central class, to discover what synthesizers are
         * available that match the template we defined above.
         */
        EngineList engineList = Central.availableSynthesizers(required);
        for (int i = 0; i < engineList.size(); i++) {

            SynthesizerModeDesc desc = (SynthesizerModeDesc) engineList.get(i);
            System.out.println("    " + desc.getEngineName()
                               + " (mode=" + desc.getModeName()
                               + ", locale=" + desc.getLocale() + "):");
            Voice[] voices = desc.getVoices();
            for (int j = 0; j < voices.length; j++) {
                System.out.println("        " + voices[j].getName());
            }
        }
    }

    public static void speakPlainText(String text) {

        /* List all the "general" domain voices, which are voices that
         * are capable of attempting to speak almost any text you
         * throw at them.
         */
        listAllVoices("general");

        String voiceName = "kevin16";

        System.out.println();
        System.out.println("Using voice: " + voiceName);

	try {
	    SynthesizerModeDesc desc = new SynthesizerModeDesc(
                null,          // engine name
                "general",     // mode name
                Locale.US,     // locale
                null,          // running
                null);         // voice
	    javax.speech.synthesis.Synthesizer synthesizer = Central.createSynthesizer(desc);

            /* Just an informational message to guide users that didn't
             * set up their speech.properties file.
             */
	    if (synthesizer == null) {
		System.err.println(noSynthesizerMessage());
		System.exit(1);
	    }

	    /* Get the synthesizer ready to speak
             */
	    synthesizer.allocate();
	    synthesizer.resume();

            /* Choose the voice.
             */
            desc = (SynthesizerModeDesc) synthesizer.getEngineModeDesc();
            Voice[] voices = desc.getVoices();
            Voice voice = null;
            for (int i = 0; i < voices.length; i++) {
                if (voices[i].getName().equals(voiceName)) {
                    voice = voices[i];
                    break;
                }
            }
            if (voice == null) {
                System.err.println(
                    "Synthesizer does not have a voice named "
                    + voiceName + ".");
                System.exit(1);
            }
            synthesizer.getSynthesizerProperties().setVoice(voice);

	    /* The the synthesizer to speak and wait for it to
             * complete.
             */
	    synthesizer.speakPlainText(text, null);
	    synthesizer.waitEngineState(javax.speech.synthesis.Synthesizer.QUEUE_EMPTY);

	    /* Clean up and leave.
             */
	    synthesizer.deallocate();
            System.exit(0);

	} catch (Exception e) {
	    e.printStackTrace();
	}
    }

}
