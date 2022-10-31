package com.example.messageapp.securities;

import android.util.Base64;
import android.util.Log;

import com.example.messageapp.utilities.Constants;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AES {
    public static final int TAG_LENGTH_BIT = 128;
    private byte[] initVec;
    public AES() {}
    //Encrypt message using AES secret key
    public String aesEncrypt(String message, SecretKey key) throws  BadPaddingException, IllegalBlockSizeException,
            InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");  //using Cipher class for encryption and decryption in CBC mode
        cipher.init(Cipher.ENCRYPT_MODE, key);//, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
        initVec = cipher.getIV();       //the initialization vector is in-built
        return Base64.encodeToString(cipher.doFinal(message.getBytes(StandardCharsets.UTF_8)), Base64.DEFAULT);
    }
    public byte[] getIV(){
        return initVec;
    }
    //Decrypt encrypted message using AES secret key which is derived from RSA algorithm
    public  String aesDecrypt(String message, SecretKey key, byte[] iv) throws IllegalBlockSizeException, BadPaddingException,
            InvalidKeyException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchPaddingException{
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BIT, iv));   //decrypt with secret key and initialization vector
        return new String(cipher.doFinal(Base64.decode(message.getBytes(StandardCharsets.UTF_8),Base64.DEFAULT)));
    }
    //covert RSA public key from String type to PublicKey type
    public PublicKey getPublicKey(String base64PublicKey){
        try{
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(Base64.decode(base64PublicKey.getBytes(),Base64.DEFAULT));
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey pubKey = keyFactory.generatePublic(keySpec);
            return pubKey;
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();
        }
        return null;
    }

    //Encrypt AES key using RSA public key
    public String rsaEncrypt(SecretKey secretKey, String pubKey) throws BadPaddingException, IllegalBlockSizeException,
            InvalidKeyException, NoSuchPaddingException, NoSuchAlgorithmException {
        String data = Base64.encodeToString(secretKey.getEncoded(),Base64.DEFAULT);
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, getPublicKey(pubKey));
        return Base64.encodeToString(cipher.doFinal(data.getBytes(StandardCharsets.UTF_8)),Base64.DEFAULT);
    }


    public String rsaDecrypt(String data, PrivateKey privateKey) throws NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidKeyException, BadPaddingException, IllegalBlockSizeException {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return new String(cipher.doFinal(Base64.decode(data,Base64.DEFAULT)));
    }

    //Load private key from Android Key Store
    public PrivateKey loadPrivateKey() throws KeyStoreException, CertificateException, IOException, NoSuchAlgorithmException, UnrecoverableEntryException {
        KeyStore keyStore = KeyStore.getInstance(Constants.KEYSTORE);
        keyStore.load(null);
        KeyStore.Entry  entry =  keyStore.getEntry(Constants.ALIAS,null);
        KeyStore.PrivateKeyEntry keyEntry = (KeyStore.PrivateKeyEntry) entry;
        return keyEntry.getPrivateKey();
    }
    //Load cipherSecretKey and decrypt it using RSA private key
    public SecretKey loadSecretKey(String cipherKey)  {

        PrivateKey privateKey = null;
        try {
            privateKey = loadPrivateKey();
        } catch (Exception e) {
            e.printStackTrace();
        }
        SecretKey secretKey = null;
        try {
            String aesKey = rsaDecrypt(cipherKey, privateKey);
            secretKey = new SecretKeySpec(Base64.decode(aesKey.getBytes(StandardCharsets.UTF_8),Base64.DEFAULT),"AES");// this is the AES key
        }catch(Exception e){
            Log.d("AAA", "Failed to retrieve aes key");
            e.printStackTrace();
        }
        return secretKey;
    }
}
