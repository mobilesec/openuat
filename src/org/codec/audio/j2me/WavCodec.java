package org.codec.audio.j2me;



/**
 * For a full documentation on the WAVE PCM sound format please refer to http://ccrma.stanford.edu/courses/422/projects/WaveFormat/.
 * This implementation follows the stadard WAVE PCM specification and takes as example the C++ implementation from http://www.thisisnotalabel.com/How-to-Read-and-Write-WAV-Files---in-C-and-VB.php. 
 * 
     WAV File Specification
     FROM http://ccrma.stanford.edu/courses/422/projects/WaveFormat/
    The canonical WAVE format starts with the RIFF header:
    0         4   ChunkID          Contains the letters "RIFF" in ASCII form
                                   (0x52494646 big-endian form).
    4         4   ChunkSize        36 + SubChunk2Size, or more precisely:
                                   4 + (8 + SubChunk1Size) + (8 + SubChunk2Size)
                                   This is the size of the rest of the chunk 
                                   following this number.  This is the size of the 
                                   entire file in bytes minus 8 bytes for the
                                   two fields not included in this count:
                                   ChunkID and ChunkSize.
    8         4   Format           Contains the letters "WAVE"
                                   (0x57415645 big-endian form).

    The "WAVE" format consists of two subchunks: "fmt " and "data":
    The "fmt " subchunk describes the sound data's format:
    12        4   Subchunk1ID      Contains the letters "fmt "
                                   (0x666d7420 big-endian form).
    16        4   Subchunk1Size    16 for PCM.  This is the size of the
                                   rest of the Subchunk which follows this number.
    20        2   AudioFormat      PCM = 1 (i.e. Linear quantization)
                                   Values other than 1 indicate some 
                                   form of compression.
    22        2   NumChannels      Mono = 1, Stereo = 2, etc.
    24        4   SampleRate       8000, 44100, etc.
    28        4   ByteRate         == SampleRate * NumChannels * BitsPerSample/8
    32        2   BlockAlign       == NumChannels * BitsPerSample/8
                                   The number of bytes for one sample including
                                   all channels. I wonder what happens when
                                   this number isn't an integer?
    34        2   BitsPerSample    8 bits = 8, 16 bits = 16, etc.

    The "data" subchunk contains the size of the data and the actual sound:
    36        4   Subchunk2ID      Contains the letters "data"
                                   (0x64617461 big-endian form).
    40        4   Subchunk2Size    == NumSamples * NumChannels * BitsPerSample/8
                                   This is the number of bytes in the data.
                                   You can also think of this as the size
                                   of the read of the subchunk following this 
                                   number.
    44        *   Data             The actual sound data.
*/
public class WavCodec {

	
		//int 	chunkSize = da;
	static int	subChunk1Size;
	static short 	format = 1;
	static short 	channels = 1;
	static int   	sampleRate = 44100;
	static int   	byteRate = 44100;
	static short 	blockAlign = 1;
	static byte 	bitsPerSample = 8;
	static int	myDataSize;
		
	
	/**
	 * Ads format and WAV header to the data. The result can be written to a WAV file and played.
	 * The length of the output is 44 (the WAV header size) + the data size.
	 * 
	 * @param data The data that will be encoded to be transmitted in the WAV file. Can be any chunk of random data, of any length.
	 * @return The byte array enclosing the given data and following the WAV header/format standard.
	 */
	public static byte[] encodeToWav(byte data[]){
		int datasize = data.length;
		int chunkSize = datasize + 36;
		//the current index at which we write in the wav byte array
		int index = 0;
		
		
		//44 is the length of the header
		byte [] wav = new byte [datasize + 44];
		
		//ChunkID = "RIFF", on 4 bytes
		//System.arraycopy(Object src, int src_position, Object dst, int dst_position, int length)
		System.arraycopy("RIFF".getBytes(), 0, wav, index, 4);
		index += 4; //4
				
		//chunkSize = datasize + 36, on 4 bytes
		byte [] chunksizeB = intToByteArray( chunkSize);
		System.arraycopy(chunksizeB, 0, wav, index, 4);
		index += 4; //8
		
		//format = "WAVE", on 4 bytes
		System.arraycopy("WAVE".getBytes(), 0, wav, index, 4);
		index += 4;//12
		
		//subChunkID = "fmt ", on 4 bytes
		System.arraycopy("fmt ".getBytes(), 0, wav, index, 4);
		index += 4;//16
		
		//subChunk1Size = 16 for PCM, on 4 bytes
		wav[index] = (byte)16;
		index += 4;//20
		
		//format = 1, on 2 bytes
		wav[20] = (byte) format ;
		index += 2; //22
		
		//channels = 1, on 2 bytes
		wav[index] = 1;
		index += 2;//24
		
		//sampleRate = 44100, on 4 bytes
		byte [] sampleRateB = intToByteArray( sampleRate);
		System.arraycopy(sampleRateB, 0, wav, index, 4);
		index += 4;//28
		
		//byteRate = 44100, on 4 bytes
		byte [] byteRateB = intToByteArray( byteRate);
		System.arraycopy(byteRateB, 0, wav, index, 4);
		index += 4;//32
		
		//block align = 1, on 2 bytes
		wav[index] = 1;
//		byte [] blockAlignB = intToByteArray( blockAlign);
//		System.arraycopy(blockAlignB, 0, wav, index, 2);
		index += 2;//34
		
		//bits per sample = 1, on 2 bytes
		wav[index] = bitsPerSample;
//		byte [] bitsPerSampleB = intToByteArray( bitsPerSample);
//		System.arraycopy(bitsPerSampleB, 0, wav, index, 2);
		index += 2;//36
		
		// subChunk2ID = "data", on 4 bytes
		System.arraycopy("data".getBytes(), 0, wav, index, 4);
		index += 4;//40
		
		//subchunk2size = data size
		byte [] dataSizeB = intToByteArray(datasize);
		System.arraycopy(dataSizeB, 0, wav, index, 4);
		index += 4;//44

		//XXX is there a more efficient way of achieving the same?
		//the wav format seems to encode integers differently, 0 -> -128, 1 -> -127 and -1 -> 127 
		for (int i = 0; i < data.length; i++) {
			if(data[i] >= 0 ) {
				data[i] -= 128;
			}
			else{
				data[i] += 128;
			}
		}
		//finally, add the data
		System.arraycopy(data, 0, wav, index, datasize);
		index += datasize;
		
		return wav;
		

	}
	
	/**
	 * Retrieves the data from a wav file.
	 * @param data
	 * @return
	 */
	public static byte [] decodeWav(byte wav[]){
		
		//if sampleSize = 8 bits
		if((int)wav[34] == 8){
			byte data [] = new byte[wav.length-44];
			for (int i = 0; i < data.length; i++) {

				//decode back the data
				if(wav[i + 44] < 0){
					data[i] = (byte)(wav[i+44] + 128);
				}else {
					data[i] = (byte)(wav[i+44] - 128);
				}

			}
			return data;
		}else if ((int)wav[34] == 16){
			byte data [] = new byte[(wav.length-44)/2];
			for (int i = 0; i < data.length; i++) {

				//decode back the data

				data[i] = (byte) ((wav[2*i + 44 + 1] + 256) & 0xFF);


			}
			return data;
		}
	//not known
		return null;
	}

	/**
	 * Writes an integer to a 4 byte array using little endian.
	 * @param value
	 * @return
	 */
    private static byte[] intToByteArray(int value) {
        byte[] b = new byte[4];
        for (int i = 0; i < 4; i++) {
            int offset = (b.length - 1 - i) * 8;
            b[3-i] = (byte) ((value >>> offset) & 0xFF);
        }
        return b;
    }
    
	private static long getOutputSampleLongWithoutSign(long outputSampleLongWithSign, long inputUnsignedMax, int inputSampleSize, int outputSampleSize)
	{
		// here we want -1 to become 255 for an 8-bit value.
		long outputSampleLongWithoutSign;
		if (outputSampleLongWithSign >= 0)
			outputSampleLongWithoutSign = outputSampleLongWithSign;
		else
			outputSampleLongWithoutSign = inputUnsignedMax + 1 + outputSampleLongWithSign;
		
		return getOutputSampleLongWithoutSign(outputSampleLongWithoutSign, inputSampleSize, outputSampleSize);

	}

	private static long getOutputSampleLongWithoutSign(long outputSampleLongWithoutSign, int inputSampleSize, int outputSampleSize)
	{
		// do calculation with unsigned long, so that sign bits are not shifted in.
		// apply sample size (truncates, does not round, when going to smaller sample size)
		if (outputSampleSize > inputSampleSize)
		{
			outputSampleLongWithoutSign <<= (outputSampleSize - inputSampleSize);
		}
		else if (inputSampleSize > outputSampleSize)
		{
			outputSampleLongWithoutSign >>= (inputSampleSize - outputSampleSize);
		}
		return outputSampleLongWithoutSign;
	}

}
