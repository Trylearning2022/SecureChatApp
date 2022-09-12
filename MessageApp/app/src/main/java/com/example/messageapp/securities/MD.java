package com.example.messageapp.securities;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MD {
    public MD() {
    }

    public byte[] getSHA(String input) throws NoSuchAlgorithmException
    {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        //digest() method called to calculate message digest of an input and return array of byte
        return md.digest(input.getBytes(StandardCharsets.UTF_8));
    }
    public String toHexString(byte[] hash){
        //Convert array into signum representation
        BigInteger number = new BigInteger(1,hash);  //-1:-ve,0:zero,1:+ve
        //Convert message digest into hex value
        StringBuilder hexString = new StringBuilder(number.toString(16));
        //Pad with leading zeros
        while(hexString.length() < 64){
            hexString.insert(0,'0');
        }
        return hexString.toString();
    }
}
