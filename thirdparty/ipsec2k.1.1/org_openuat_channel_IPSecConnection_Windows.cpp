/* This file implements a JNI wrapper around the ipsec2k library.

   The code for importing a certificate into the Windows certificate store
   so that it can be used for IPSec authentication has been taken partially from
   openswan's certimport tool, which is currently licensed under the GPL. */

#define UNICODE
#define _UNICODE

#define WIN32_LEAN_AND_MEAN

#include <stdio.h>
#include <windows.h>
#include <wincrypt.h>

#ifdef _MANAGED
#pragma managed(push, off)
#endif

#include "types.h"
#include "ipsec.h"
#include "org_openuat_channel_IPSecConnection_Windows.h"

void guidToString(const GUID &id, char* str) 
{
	sprintf(str, "%lx-%x-%x-%02x%02x%02x%02x%02x%02x%02x%02x",
		id.Data1, id.Data2, id.Data3,
		id.Data4[0], id.Data4[1], id.Data4[2], id.Data4[3], 
		id.Data4[4], id.Data4[5], id.Data4[6], id.Data4[7]);
}

bool stringToGuid(const char* str, GUID& id) 
{
	int num = sscanf(str, "%lx-%x-%x-%02x%02x%02x%02x%02x%02x%02x%02x",
		&id.Data1, &id.Data2, &id.Data3,
		&id.Data4[0], &id.Data4[1], &id.Data4[2], &id.Data4[3], 
		&id.Data4[4], &id.Data4[5], &id.Data4[6], &id.Data4[7]);

	printf("Reconstructed %d elements of the GUID from string '%s': %lx-%x-%x-%02x%02x%02x%02x%02x%02x%02x%02x\n",
		num, str, id.Data1, id.Data2, id.Data3,
		id.Data4[0], id.Data4[1], id.Data4[2], id.Data4[3], 
		id.Data4[4], id.Data4[5], id.Data4[6], id.Data4[7]);

	return num == 11;
}

JNIEXPORT jlong JNICALL Java_org_openuat_channel_IPSecConnection_1Windows_createPolicyHandle
  (JNIEnv *env, jobject jobj, jint jcipher, jint jmac, jint jdhgroup, jint jlifetime) 
{
	x4_ipsec_profile * ipsec = x4_ipsec_profile::instance();

	ipsec->config((x4e_cipher) jcipher,
	            (x4e_hasher) jmac,
			    (x4e_dhgroup) jdhgroup,
				jlifetime);
	return (long) ipsec;
}

jboolean addPolicyHelper
(JNIEnv *env, jobject jobj, jlong jhandle, jbyteArray jfromAddress, jbyteArray jfromMask, jbyteArray jtoAddress, jbyteArray jtoMask, jbyteArray jfromGateway, jbyteArray jtoGateway, jint jcipher, jint jmac, jboolean jpfs, jstring jpsk, jstring jcaDn) 
{
	x4_ipsec_ts lts, rts;
	ipv4        lg,  rg;

	if (jhandle == NULL) {
		printf("Error: ipsec profile handle is NULL\n");
		return NULL;
	}

	x4_ipsec_profile * ipsec = (x4_ipsec_profile*) jhandle;

	jbyte* fromAddr = env->GetByteArrayElements(jfromAddress, JNI_FALSE);
	jbyte* fromMask = env->GetByteArrayElements(jfromMask, JNI_FALSE);
	jbyte* toAddr = env->GetByteArrayElements(jtoAddress, JNI_FALSE);
	jbyte* toMask = env->GetByteArrayElements(jtoMask, JNI_FALSE);
	jbyte* fromGate = env->GetByteArrayElements(jfromGateway, JNI_FALSE);
	jbyte* toGate = env->GetByteArrayElements(jtoGateway, JNI_FALSE);
	for (int i=0; i<4; i++) {
		lts.addr[i] = fromAddr[i];
		lts.mask[i] = fromMask[i];
		rts.addr[i] = toAddr[i];
		rts.mask[i] = toMask[i];
		lg[i] = fromGate[i];
		rg[i] = toGate[i];
	}
	lts.port = 0; // any
	rts.port = 0;
	env->ReleaseByteArrayElements(jfromAddress, fromAddr, JNI_FALSE);
	env->ReleaseByteArrayElements(jfromMask, fromMask, JNI_FALSE);
	env->ReleaseByteArrayElements(jtoAddress, toAddr, JNI_FALSE);
	env->ReleaseByteArrayElements(jtoMask, toMask, JNI_FALSE);
	env->ReleaseByteArrayElements(jfromGateway, fromGate, JNI_FALSE);
	env->ReleaseByteArrayElements(jtoGateway, toGate, JNI_FALSE);

	const char *psk;
	const char *caDn;
	
	if (jpsk != NULL)
		psk = env->GetStringUTFChars(jpsk, JNI_FALSE);
	else
		psk = NULL;
	if (jcaDn != NULL)
		caDn = env->GetStringUTFChars(jcaDn, JNI_FALSE);
	else
		caDn = NULL;

	printf("psk is '%s'\n", psk);
	printf("CA DN is '%s'\n", caDn);

	if (psk == NULL && caDn == NULL) {
		printf("Error: need either PSK or CA DN\n");
		return JNI_FALSE;
	}

	ipsec->insert(lts, rts, 0,      // traffic selectors (all protocols)
                lg, rg,           // tunnel ends
                (x4e_cipher) jcipher,
                (x4e_hasher) jmac,
                jpfs,            // no PFS
                psk,       // preshared secret authentication
                caDn);
	if (psk != NULL)
		env->ReleaseStringUTFChars(jpsk, psk);
	if (caDn != NULL)
		env->ReleaseStringUTFChars(jcaDn, caDn);

	return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL Java_org_openuat_channel_IPSecConnection_1Windows_addPolicyPsk
  (JNIEnv *env, jobject jobj, jlong jhandle, jbyteArray jfromAddress, jbyteArray jfromMask, jbyteArray jtoAddress, jbyteArray jtoMask, jbyteArray jfromGateway, jbyteArray jtoGateway, jint jcipher, jint jmac, jboolean jpfs, jstring jpsk) 
{
	return addPolicyHelper(env, jobj, jhandle, jfromAddress, jfromMask, jtoAddress, jtoMask, jfromGateway, jtoGateway, jcipher, jmac, jpfs, jpsk, NULL);
}

JNIEXPORT jboolean JNICALL Java_org_openuat_channel_IPSecConnection_1Windows_addPolicyCA
  (JNIEnv *env, jobject jobj, jlong jhandle, jbyteArray jfromAddress, jbyteArray jfromMask, jbyteArray jtoAddress, jbyteArray jtoMask, jbyteArray jfromGateway, jbyteArray jtoGateway, jint jcipher, jint jmac, jboolean jpfs, jstring jcaDn) 
{
	return addPolicyHelper(env, jobj, jhandle, jfromAddress, jfromMask, jtoAddress, jtoMask, jfromGateway, jtoGateway, jcipher, jmac, jpfs, NULL, jcaDn);
}

/*  ipsec->insert_dynamic(x4c_cipher_3des,
                        x4c_hasher_sha1,
                        true,       // PFS
                        "123",      // preshared secret authentication
                        0);*/

JNIEXPORT jstring JNICALL Java_org_openuat_channel_IPSecConnection_1Windows_registerPolicy
  (JNIEnv *env, jobject jobj, jlong jhandle)
{
	if (jhandle == NULL) {
		printf("Error: ipsec profile handle is NULL\n");
		return NULL;
	}

	x4_ipsec_profile * ipsec = (x4_ipsec_profile*) jhandle;

	if (! x4_register(ipsec)) {
		printf("Unable to register policy and create GUID!\n");
		return JNI_FALSE;
	}

	char buf[128]; // this should be enough to hold the GUID
	guidToString(ipsec->id(), buf);
	delete ipsec;

	printf("Created id '%s'\n", buf);

	jstring ret = env->NewStringUTF(buf);

	return ret;
}

JNIEXPORT jboolean JNICALL Java_org_openuat_channel_IPSecConnection_1Windows_activatePolicy
  (JNIEnv *env, jobject jobj, jstring jid)
{
	const char *idstr = env->GetStringUTFChars(jid, JNI_FALSE);

	printf("Trying to activate policy with GUID '%s'\n", idstr);

	GUID id;
	bool ret = stringToGuid(idstr, id);
	env->ReleaseStringUTFChars(jid, idstr);
	if (!ret) {
		printf("Could not parse GUID string correctly!\n");
		return JNI_FALSE;
	}

	printf("Now here\n");
	return x4_activate(id);
}

JNIEXPORT jboolean JNICALL Java_org_openuat_channel_IPSecConnection_1Windows_deactivatePolicy
  (JNIEnv *env, jobject jobj, jstring jid)
{
	const char *idstr = env->GetStringUTFChars(jid, JNI_FALSE);
	GUID id;
	bool ret = stringToGuid(idstr, id);
	env->ReleaseStringUTFChars(jid, idstr);
	if (!ret)
		return JNI_FALSE;

	return x4_deactivate(&id);
}

JNIEXPORT jboolean JNICALL Java_org_openuat_channel_IPSecConnection_1Windows_removePolicy
  (JNIEnv *env, jobject jobj, jstring jid)
{
	const char *idstr = env->GetStringUTFChars(jid, JNI_FALSE);
	GUID id;
	bool ret = stringToGuid(idstr, id);
	env->ReleaseStringUTFChars(jid, idstr);
	if (!ret)
		return JNI_FALSE;

	return x4_unregister(id);
}

/*string getErrMsg(const char* func, DWORD err = GetLastError()) 
{
	char szErrMsg[256];
	char szMsg[512];
	if ( FormatMessage( FORMAT_MESSAGE_FROM_SYSTEM, 0, err, 0, szErrMsg, sizeof szErrMsg / sizeof *szErrMsg, 0 ) )
	{
		 sprintf( szMsg, L"%s failed: %s", fcn, szErrMsg );
	}
	else 
	{
		sprintf( szMsg, L"%s failed: 0x%08X", fcn, err );
	}
	return string(szMsg);
}*/

//////////////////////////////
// This code is based on the certimport tool by the Openswan team, which was based on 
// pfxImport lib.
// It was heavily rewritten by me.
JNIEXPORT jint JNICALL Java_org_openuat_channel_IPSecConnection_1Windows_nativeImportCertificate
  (JNIEnv *env, jclass jclass, jstring jfilename, jstring jpassword, jboolean joverwriteExisting)
{
	HANDLE hfile = INVALID_HANDLE_VALUE;
	HANDLE hsection = 0;
	void* pfx = 0;
	HCERTSTORE pfxStore = 0;
	HCERTSTORE myStore = 0;
	HCERTSTORE rootStore = 0;
	// if this is set to true, the private key will be marked as exportable in the cert store
	bool exportable = false;
	int retCode = -1;

	const char *filename = env->GetStringUTFChars(jfilename, JNI_FALSE);
	const char *password = env->GetStringUTFChars(jpassword, JNI_FALSE);

	if (filename ==  NULL || password == NULL) {
		printf("Error: need both filename and password parameters!\n");
		retCode = 6;
		goto cleanup;
	}

	wchar_t wFilename[256];
	wchar_t wPassword[256];
	mbstowcs(wFilename, filename, sizeof(wFilename) / sizeof(wchar_t));
	mbstowcs(wPassword, password, sizeof(wPassword) / sizeof(wchar_t));

	hfile = CreateFile(	wFilename, 
						FILE_READ_DATA, 
						FILE_SHARE_READ, 
						0, 
						OPEN_EXISTING, 
						0, 
						0);
	if (INVALID_HANDLE_VALUE == hfile) 
	{
		// file could not be opened for reading --> error code 1
		retCode = 1;
		goto cleanup;
	}

	hsection = CreateFileMapping(	hfile, 
									0, 
									PAGE_READONLY, 
									0, 
									0, 
									0);
	if (!hsection) 
	{
		// dt.
		retCode = 1;
		goto cleanup;
	}

	pfx = MapViewOfFile(	hsection, 
							FILE_MAP_READ, 
							0, 
							0, 
							0);
	if (!pfx) 
	{
		// dt.
		retCode = 1;
		goto cleanup;
	}

	CRYPT_DATA_BLOB blob;
	blob.cbData = GetFileSize(	hfile, 
								0);
	blob.pbData = (BYTE*)pfx;
	
	if (!PFXIsPFXBlob(&blob)) 
	{
		//wprintf(L"%s is not a valid PFX file\n", filename);
		// file could not be decoded --> error code 3
		retCode = 3;
		goto cleanup;
	}

	// the key will be imported for the local machine, not the current user
	DWORD importFlags = CRYPT_MACHINE_KEYSET;
	if (exportable) 
	{
		importFlags |= CRYPT_EXPORTABLE;
	}
	
	pfxStore = PFXImportCertStore(	&blob, 
									wPassword, 
									importFlags);
	if (!pfxStore) 
	{
		//_err(L"PFXImportCertStore");
		// either password mismatch or import error - can't decide, so only handle password error
		retCode = 2;
		goto cleanup;
	}

	myStore = CertOpenStore(	CERT_STORE_PROV_SYSTEM, 
								0, 
								0, 
								CERT_STORE_OPEN_EXISTING_FLAG | CERT_SYSTEM_STORE_LOCAL_MACHINE, 
								L"MY");
	if (!myStore) 
	{
		//_err(L"CertOpenSystemStore MY");
		// import error --> return code 4
		retCode = 4;
		goto cleanup;
	}
	rootStore = CertOpenStore(	CERT_STORE_PROV_SYSTEM, 
								0, 
								0, 
								CERT_STORE_OPEN_EXISTING_FLAG | CERT_SYSTEM_STORE_LOCAL_MACHINE, 
								L"Root");
	if (!rootStore ) 
	{
		//_err(L"CertOpenSystemStore Root");
		// dt.
		retCode = 4;
		goto cleanup;
	}
	
	// We assume two certificates, the first one being the root certificate,
	// all other certificates personal.
	unsigned long counter = 0;
	
	PCCERT_CONTEXT pctx = 0;
	while (0 != (pctx = CertEnumCertificatesInStore(pfxStore, pctx))) 
	{
		wchar_t name[128];
		if (CertGetNameString(pctx, CERT_NAME_FRIENDLY_DISPLAY_TYPE, 0, 0, name, sizeof name / sizeof *name)) 
		{
			wprintf(L"Found a certificate in the PFX file: %s\n", name);
		}else 
		{
			//_err(L"CertGetNameString");
		}
		wprintf(L"Attempting to import certificate into machine store...\n");

		if (CertAddCertificateContextToStore(counter == 0 ? rootStore : myStore, pctx, CERT_STORE_ADD_NEW, 0)) 
		{
			wprintf(L"Import succeeded.\n");
		}else 
		{
			DWORD err = GetLastError();
			if (CRYPT_E_EXISTS == err) 
			{
				wprintf(L"\nAn equivalent certificate already exists. ");
				if (joverwriteExisting == JNI_TRUE) {
					printf("Overwriting the certificate\n");
					if (CertAddCertificateContextToStore(counter == 0 ? rootStore : myStore, pctx, CERT_STORE_ADD_REPLACE_EXISTING, 0)) 
					{
						wprintf(L"Import succeeded.\n");
					}else 
					{
						//_err(L"CertAddCertificateContextToStore");
						// dt.
						retCode = 4;
						goto cleanup;
					}
				}else 
				{
					printf("Not overwriting, returning with error.\n");
					retCode = 5;
					goto cleanup;
				}
			}else 
			{
				//_err(L"CertAddCertificateContextToStore");
				// dt.
				retCode = 4;
				goto cleanup;
			}
		}
		++counter;
	}
	retCode = 0;

cleanup:
	env->ReleaseStringUTFChars(jfilename, filename);
	env->ReleaseStringUTFChars(jpassword, password);

	if (myStore) 
	{
		CertCloseStore(myStore, 0);
	}
	if (pfxStore) 
	{
		CertCloseStore(pfxStore, CERT_CLOSE_STORE_FORCE_FLAG);
	}
	if (pfx) 
	{
		UnmapViewOfFile(pfx);
	}
	if (hsection) 
	{
		CloseHandle(hsection);
	}
	if (INVALID_HANDLE_VALUE != hfile) 
	{
		CloseHandle(hfile);
	}

	if (retCode == -1) {
		printf("Error: do not have a defined return code, exiting with -1\n");
	}

	return retCode;
}

#ifdef _MANAGED
#pragma managed(pop)
#endif
