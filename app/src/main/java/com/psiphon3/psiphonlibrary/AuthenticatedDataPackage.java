/*
 * Copyright (c) 2012, Psiphon Inc.
 * All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.psiphon3.psiphonlibrary;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

import android.util.Base64;
import android.util.Base64OutputStream;

import com.fasterxml.jackson.core.Base64Variants;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.psiphon3.R;
import com.psiphon3.log.MyLog;


public class AuthenticatedDataPackage
{
    public static class AuthenticatedDataPackageException extends Exception
    {
        private static final long serialVersionUID = 1L;
        
        public AuthenticatedDataPackageException()
        {
            super();
        }
        
        public AuthenticatedDataPackageException(String message)
        {
            super(message);
        }

        public AuthenticatedDataPackageException(String message, Throwable cause)
        {
            super(message, cause);
        }

        public AuthenticatedDataPackageException(Throwable cause)
        {
            super(cause);
        }
    }
    
    static public String extractAndVerifyData(
            String signaturePublicKey,
            boolean dataIsBase64,
            String dataPackage)
        throws AuthenticatedDataPackageException
    {
        try
        {
            ByteArrayOutputStream dataDestination = new ByteArrayOutputStream();
            
            extractAndVerifyData(
                signaturePublicKey,
                new ByteArrayInputStream(dataPackage.getBytes("UTF-8")),
                dataIsBase64,
                dataDestination);
            
            return dataDestination.toString("UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            MyLog.e("Invalid encoding for authenticated data package: " + e);
            throw new AuthenticatedDataPackageException();
        }
    }
    
    private static class VerifyingOutputStream extends FilterOutputStream
    {
        private static class SignatureOutputStream extends OutputStream
        {
            private Signature signature;

            public SignatureOutputStream(Signature signature)
            {
                this.signature = signature;
            }            
            public void write(byte[] b) throws IOException
            {
                try
                {
                    this.signature.update(b);
                } catch (SignatureException e) {}
            }

            public void write(byte[] b, int off, int len) throws IOException
            {
                try
                {
                    this.signature.update(b, off, len);
                } catch (SignatureException e) {}
                
            }

            public void write(int b) throws IOException
            {
                try
                {
                    // NOTE: write(int oneByte): "Writes a single byte to this stream. 
                    // Only the least significant byte of the integer oneByte is written to the stream."
                    this.signature.update((byte)b);
                } catch (SignatureException e) {}
            }
        };
        
        private boolean dataIsBase64;
        private OutputStream verifyOutputStream;

        public VerifyingOutputStream(boolean dataIsBase64, OutputStream out, Signature signature)
        {
            super(out);
            this.dataIsBase64 = dataIsBase64;
            this.verifyOutputStream = new SignatureOutputStream(signature);

            if (this.dataIsBase64)
            {
                // We re-encode the data as Base64, since the signature is on Base64-encoded
                // data (a pre-existing design). The Jackson API forces a decode before we can
                // stream the encoded data to the signature stream, hence the re-encode.
                // TODO: Avoid this redundant computation.
                
                this.verifyOutputStream = new Base64OutputStream(this.verifyOutputStream, Base64.NO_WRAP);
            }
        }

        public void write(byte[] b) throws IOException
        {
            this.out.write(b);
            this.verifyOutputStream.write(b);
        }

        public void write(byte[] b, int off, int len) throws IOException
        {
            this.out.write(b, off, len);
            this.verifyOutputStream.write(b, off, len);
            
        }

        public void write(int b) throws IOException
        {
            this.out.write(b);            
            this.verifyOutputStream.write(b);
        }
        
        public void close() throws IOException
        {
            this.out.close();            
            this.verifyOutputStream.close();
        }
    }

    static public void extractAndVerifyData(
            String signaturePublicKey,
            InputStream dataPackage,
            boolean dataIsBase64,
            OutputStream dataDestination)
        throws AuthenticatedDataPackageException
    {
        // Authenticate remote server list as per scheme described in
        // Psiphon/Automation/psi_ops_server_entry_auth.py
        
        // NOTE: this function always closes the dataPackage input stream
        // and dataDestination output stream.

        JsonParser parser = null;
        VerifyingOutputStream verifyingOutputStream = null;
        
        try
        {
            boolean dataValueRead = false;
            String signature = null;
            String signingPublicKeyDigest = null;

            // Initialize a verifier using the expected public key; this will
            // be used while streaming the "data" value when parsing the JSON.
            
            byte[] publicKeyBytes = Base64.decode(signaturePublicKey, Base64.NO_WRAP);
            java.security.spec.X509EncodedKeySpec spec = new X509EncodedKeySpec(publicKeyBytes);
            java.security.KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey publicKey = keyFactory.generatePublic(spec);

            Signature verifier = java.security.Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey);
            verifyingOutputStream = new VerifyingOutputStream(dataIsBase64, dataDestination, verifier);
            
            // JSON parsing - using a streaming API as the "data" value is too large
            // to be loaded into memory.
            
            parser = new JsonFactory().createParser(dataPackage);

            if (parser.nextToken() != JsonToken.START_OBJECT)
            {
                throw new AuthenticatedDataPackageException();
            }

            while (true)
            {
                JsonToken token = parser.nextToken();
                
                if (token == JsonToken.END_OBJECT)
                {
                    break;
                }
                else if (token != JsonToken.FIELD_NAME)                    
                {
                    throw new AuthenticatedDataPackageException();
                }
                
                String fieldName = parser.getCurrentName();

                if (parser.nextToken() != JsonToken.VALUE_STRING)                    
                {
                    // Forward compatibility: ignore unexpected objects and arrays
                    parser.skipChildren();
                    continue;
                }

                if (fieldName.equals("data"))
                {
                    if (dataIsBase64)
                    {
                        // This value is too large to load into memory as a string. The value is
                        // decoded from base64 and written to the output stream; at the same
                        // time, the bytes is fed into a signature verifier.
    
                        // NOTE: The verification is not finished here, as we require the 
                        // "signingPublicKeyDigest" value to check which public key was used
                        // and we require the "signature" value to complete the verification.
    
                        // IMPORTANT NOTE: Complete data is written to the "dataDestination"
                        // output stream *before* the signature is verified. If the caller is
                        // writing to disk, for example, it should perform a two-phase process
                        // whereby it writes to a temp file name, then renames (commits) the file
                        // after validateAndExtractData returns true.
                        
                        try
                        {
                            parser.readBinaryValue(Base64Variants.MIME, verifyingOutputStream);
                        }
                        catch (IllegalArgumentException e)
                        {
                            // Jackson throws this unchecked exception for malformed Base64
                            throw new AuthenticatedDataPackageException(e);
                        }
                    }
                    else
                    {
                        // NOTE: Jackson can only stream Base64 values
                        
                        verifyingOutputStream.write(parser.getValueAsString().getBytes());
                    }
                    
                    // This explicit close() ensures that the Base64OutputStream in 
                    // Base64OutputStream has flushed and output final padding *before*
                    // the verifier.verify() call.
                    verifyingOutputStream.close();

                    dataValueRead = true;
                }
                else if (fieldName.equals("signature"))
                {
                    signature = parser.getValueAsString();
                }
                else if (fieldName.equals("signingPublicKeyDigest"))
                {
                    signingPublicKeyDigest = parser.getValueAsString();
                }
                else
                {
                    // Forward compatibility: ignore unexpected values
                }
            }
            
            // Check if expected values are missing.
            
            if (!dataValueRead || signature == null || signingPublicKeyDigest == null)
            {
                MyLog.e("Missing value in authenticated data package");
                throw new AuthenticatedDataPackageException();
            }
            
            // Check if the entry is signed with a different public key than our embedded value.
            
            MessageDigest sha2;
            sha2 = MessageDigest.getInstance("SHA256");
            String publicKeyDigest = Base64.encodeToString(sha2.digest(signaturePublicKey.getBytes()), Base64.NO_WRAP);
            if (0 != publicKeyDigest.compareTo(signingPublicKeyDigest))
            {
                MyLog.e("Authenticated data package signed with different public key");
                throw new AuthenticatedDataPackageException();
            }
            
            // Now that we've checked the signing public key and have read the signature,
            // we can complete the verification process.

            if (!verifier.verify(Base64.decode(signature, Base64.NO_WRAP)))
            {
                MyLog.e("Invalid signature on authenticated data package");
                throw new AuthenticatedDataPackageException();
            }
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new AuthenticatedDataPackageException(e);
        }
        catch (InvalidKeySpecException e)
        {
            throw new AuthenticatedDataPackageException(e);
        }
        catch (InvalidKeyException e)
        {
            throw new AuthenticatedDataPackageException(e);
        }
        catch (SignatureException e)
        {
            throw new AuthenticatedDataPackageException(e);
        }
        catch (UnsupportedEncodingException e)
        {
            throw new AuthenticatedDataPackageException(e);
        }
        catch (IOException e)
        {
            throw new AuthenticatedDataPackageException(e);
        }
        finally
        {
            if (parser != null)
            {
                try { parser.close(); } catch (IOException e) {}
            }            
            if (verifyingOutputStream != null)
            {
                try { verifyingOutputStream.close(); } catch (IOException e) {}
            }            
        }
    }
}
