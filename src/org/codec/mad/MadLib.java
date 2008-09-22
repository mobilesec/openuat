////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//  MadLib.java
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//  Desc: This file is the core wrapper class for the MadLib API.
//  Author: John Solis
//  Version: 0.1
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
package org.codec.mad;

import java.io.UnsupportedEncodingException;
import net.mypapit.java.StringTokenizer;
import java.util.Vector;

public class MadLib
{
	//Public variables used in this class
	public static final int HASH_SHA = 0x01;
	public static final int HASH_MD2 = 0x02;
	public static final int HASH_MD5 = 0x05;
	public static final int HASH_USER = 0x0F;
        public static final int SIZE = 5;

   /*****
    * String GenerateMadLib() -- Converts the input string into a user friendly madlib
    *
    * Params:
    *		String szInput -- Input string to convert (in our case will be the public diffie-hellman key)
    *		int nHash -- Hash Function to use
    *		int nCollapsedSize -- Final size to collapse hash down to (This becomes our security param. The smaller the size the less secure)
    * Output:
    *		A string representing the MadLib encoding of the input
    *****/
	public String GenerateMadLib( byte[] szInput, int nHash, int nCollapsedSize ) 
            throws UnsupportedEncodingException//, NoSuchAlgorithmException
	{
		//Perform some initial input validation
		if( nCollapsedSize < 1 )
		{  return "Collapsed size can not be less than one."; }

//		if( nHash != HASH_SHA && nHash != HASH_MD2 && nHash != HASH_MD5 && nHash != HASH_USER )
//		{  return "Invalid hash type."; }


	   //Begin function by converting the input string into a series of bytes
	   //byte[] InputAsBytes = szInput.getBytes( "8859_1" /* encoding */ );  //Does encoding matter?
	   byte[] byMsgDigest = szInput;


		/*//
		//Now collapse down the hash using the XOR technique from S/KEY & OTP
		//Collapse down to nCollapsedsize * 10  (ie, 10 bits per word)
		/////*/

		//First convert the byte sequence into a binary string
		String szBits = "";


		//Copy current array over
		for( int copy=0; copy < byMsgDigest.length; copy++ )
		{
			Byte bTmp = new Byte(byMsgDigest[copy]);

		    for(int loop = 0; loop < 10; loop++ )
		   	{
				if( ((bTmp.byteValue()>>(7-loop)) & 1) == 1 )
				{  szBits += "1"; }
				else
				{  szBits += "0"; }
			}
		}

		//Compress binary string using XORS
		int nOffset = 0;

		while( szBits.length() > nCollapsedSize * 10 )
		{

			String Set = szBits.substring(nOffset*10, nOffset*10+20);

			String Result = "";

			for( int loop = 0; loop < 10; loop++ )
			{
				//The only time OR is zero is when both are zero, o/w a 1
				if( Set.charAt(loop) == '0' && Set.charAt(loop+10) == '0' )
				{  Result += "0"; }
				else
				{  Result += "1"; }
			}

			szBits = szBits.substring(0, nOffset*10) + Result + szBits.substring( (nOffset+1)*10+10 );

			nOffset++;

			if( (nOffset+1)*20 > szBits.length() )
			{  nOffset = 0; }

		}

	   //Now we select an approriate sized madlib and replace the words accordingly.
	   String szMadLib = GetMadLib( nCollapsedSize, szBits );


	   return szMadLib;
	}

   /*****
    * byte[] UserHash() -- Allows a user to manually implement their desired hash function
    *
    * Params:
    *		String szInput -- Input string to be used as input
    * Output:
    *		A byte array representing the final message digest output of the user hash function
    *****/
    public byte[] UserHash( byte[] szInput )
	{
		byte[] byMsgDigest = new byte[50]; //user should set size to whatever is desired

		return byMsgDigest;
	}
    
	private int pow (int base, int exp){
		int result = 1;
		for (int i = 0; i < exp; i++) {
			result = result * base;
		}
		return result;
	}

   /*****
    * String GetMadLib(int nCollapsedSize ) -- Returns a Madlib in vector form that allows for easy string replacement
    *
    * Params:
    *		int nCollapsedSize -- Select a madlib that requires nCollapsedSize replacements
    *		String szHashBits -- String representing the bits in the hash
    * Output:
    *		A MadLib string with the necessary number of replacements/Error message
    *****/
    private String GetMadLib(int nCollapsedSize, String szHashBits )
	{
		//First get an appropriate sized madlib
		String szMadLib = "";

		switch( nCollapsedSize )
		{
			case 1: szMadLib = DICTIONARY.arMadLib1[0]; break;
			case 2: szMadLib = DICTIONARY.arMadLib2[0]; break;
			case 3: szMadLib = DICTIONARY.arMadLib3[0]; break;
			case 4: szMadLib = DICTIONARY.arMadLib4[0]; break;
			case 5: szMadLib = DICTIONARY.arMadLib5[0]; break;
			case 6: szMadLib = DICTIONARY.arMadLib6[0]; break;
			case 7: szMadLib = DICTIONARY.arMadLib7[0]; break;
			case 8: szMadLib = DICTIONARY.arMadLib8[0]; break;
			case 9: szMadLib = DICTIONARY.arMadLib9[0]; break;
			case 10: szMadLib = DICTIONARY.arMadLib10[0]; break;
			case 11: szMadLib = DICTIONARY.arMadLib11[0]; break;
			case 12: szMadLib = DICTIONARY.arMadLib12[0]; break;
			case 13: szMadLib = DICTIONARY.arMadLib13[0]; break;
			case 14: szMadLib = DICTIONARY.arMadLib14[0]; break;
			case 15: szMadLib = DICTIONARY.arMadLib15[0]; break;
			case 16: szMadLib = DICTIONARY.arMadLib16[0]; break;
			case 17: szMadLib = DICTIONARY.arMadLib17[0]; break;
			case 18: szMadLib = DICTIONARY.arMadLib18[0]; break;
			case 19: szMadLib = DICTIONARY.arMadLib19[0]; break;
			case 20: szMadLib = DICTIONARY.arMadLib20[0]; break;
			default: return "ERROR: Invalid MADLIB size";
		}

		Vector vecMadLib = new Vector();

		//Now tokenize for easy parsing
		StringTokenizer st = new StringTokenizer( szMadLib, ">");
		while (st.hasMoreTokens())
		{
			//Each token needs to be parsed into two parts: Part 1 -> Mad lib sentence; Part 2->Replacement Word
			String szToken = st.nextToken();
			int nPos = szToken.indexOf( '<' );

			//Only add Part 1 if needed (will occur when two replacement words follow each other)
			if( nPos > 0 )
			{ vecMadLib.addElement( szToken.substring( 0, nPos ) ); }

			vecMadLib.addElement( szToken.substring( nPos+1, szToken.length() )  );
		}

		String szUpdatedMadLib = "";

		//Select a word for each byte of the final digest size
		int nWordCount = 0;
		int nIndex = 0;
		for( int loop = 0; loop < vecMadLib.size(); loop++ )
		{
			String szToken = (String)vecMadLib.elementAt(loop);
			szToken = szToken.trim();

			//Get index into dictionary by converting bits back to integer value
			if( ( szToken.compareTo("BOYNAME") == 0 ) || ( szToken.compareTo("GIRLNAME") == 0 ) ||
				( szToken.compareTo("ANIMAL") == 0 ) || ( szToken.compareTo("NOUN") == 0 ) ||
				( szToken.compareTo("ADJECTIVE") == 0 ) || ( szToken.compareTo("VERB") == 0 ) ||
				( szToken.compareTo("ADVERB") == 0 ) )
			{
                String szIndex = szHashBits.substring( nWordCount*10, nWordCount*10+10 );

                nIndex = 0;

				for( int conv = 0; conv < 10; conv++ )
				{
					if( szIndex.charAt(conv) == '1' )
					{
						nIndex |= pow(2, 7-conv );
					}
				}

				nWordCount++;
			}

			//Replace each replacement word based on the hash (+128 required to convert to unsigned value)
			if( szToken.compareTo("BOYNAME") == 0 )
			{	szUpdatedMadLib += DICTIONARY.arBoyName[nIndex]; continue; }

			if( szToken.compareTo("GIRLNAME") == 0 )
			{	szUpdatedMadLib += DICTIONARY.arGirlName[nIndex]; continue; }

			if( szToken.compareTo("ANIMAL") == 0 )
			{	szUpdatedMadLib += DICTIONARY.arAnimal[nIndex];	continue; }

			if( szToken.compareTo("NOUN") == 0 )
			{	szUpdatedMadLib += DICTIONARY.arNoun[nIndex]; continue; }

			if( szToken.compareTo("ADJECTIVE") == 0 )
			{	szUpdatedMadLib += DICTIONARY.arAdjective[nIndex]; continue; }

			if( szToken.compareTo("VERB") == 0 )
			{	szUpdatedMadLib += DICTIONARY.arVerb[nIndex]; continue; }

			if( szToken.compareTo("ADVERB") == 0 )
			{	szUpdatedMadLib += DICTIONARY.arAdverb[nIndex]; continue; }


			szUpdatedMadLib +=  (String)vecMadLib.elementAt(loop);
		}


		return szUpdatedMadLib;
	}

}
