package com.example.messageapp.securities;

import android.content.Context;
import android.icu.util.Calendar;
import android.icu.util.GregorianCalendar;
import android.os.Build;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.example.messageapp.utilities.Constants;

import java.io.IOException;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;

import javax.security.auth.x500.X500Principal;

public class RSAKeyGen {
    public RSAKeyGen(){}
    //Generate RSA key
    @RequiresApi(api = Build.VERSION_CODES.N)
    public void KeyGen(Context context) throws NoSuchAlgorithmException, NoSuchProviderException,
            InvalidAlgorithmParameterException {
        Calendar start = new GregorianCalendar();
        Calendar end = new GregorianCalendar();
        end.add(Calendar.YEAR, 25);
        KeyPairGeneratorSpec spec =
                new KeyPairGeneratorSpec.Builder(context)
                        .setAlias(Constants.ALIAS)
                        .setSubject(new X500Principal("CN=" + Constants.ALIAS)) //self-signed certificate of the generated pair
                        .setSerialNumber(BigInteger.valueOf(1337))   //self-signed certificate
                        .setStartDate(start.getTime())
                        .setEndDate(end.getTime())
                        .build();
        final KeyPairGenerator keyGen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, Constants.KEYSTORE);
        keyGen.initialize(spec);
        final KeyPair pair = keyGen.generateKeyPair();
        Log.d("AAA","Private Key is:" + Base64.encodeToString(pair.getPublic().getEncoded(), Base64.DEFAULT));
    }

    /*
     * Retrieve Private Key
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    public KeyStore.PrivateKeyEntry getPrivateKey(Context context) throws KeyStoreException, UnrecoverableEntryException,
            NoSuchAlgorithmException, CertificateException, IOException {
        //getPrivateKey
        KeyStore keyStore = KeyStore.getInstance(Constants.KEYSTORE);
        // Weird artifact of Java API.  If you don't have an InputStream to load, you still need
        // to call "load", or it'll crash.
        keyStore.load(null);
        //Load the keypair from the Android key store
        KeyStore.Entry entry = keyStore.getEntry(Constants.ALIAS, null);
        //if the entry is null, keys were never stored under this alias.
        if (entry == null) {
            Log.d("AAA", "No key found under alias: " + Constants.ALIAS);
            Log.d("AAA","Generating new key...");
            try {
                KeyGen(context);

                //reload keystore
                keyStore = KeyStore.getInstance(Constants.KEYSTORE);
                keyStore.load(null);
                //reload key pair
                entry = keyStore.getEntry(Constants.ALIAS, null);
                if (entry == null) {
                    Log.d("AAA", "Failed to generate new key...");
                    return null;
                }
            } catch (InvalidAlgorithmParameterException | NoSuchProviderException e) {
                e.printStackTrace();
                return null;
            }
        }
        if (!(entry instanceof KeyStore.PrivateKeyEntry)) {
            Log.d("AAA", "Not an instance of a PrivateKeyEntry");
            return null;
        }

        return (KeyStore.PrivateKeyEntry) entry;
    }

}
