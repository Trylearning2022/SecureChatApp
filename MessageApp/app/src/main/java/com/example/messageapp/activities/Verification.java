package com.example.messageapp.activities;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.example.messageapp.databinding.ActivityVerificationBinding;
import com.example.messageapp.securities.AES;
import com.example.messageapp.securities.RSAKeyGen;
import com.example.messageapp.utilities.Constants;
import com.example.messageapp.utilities.PreferenceManager;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.IOException;

import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.HashMap;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

public class Verification extends AppCompatActivity {
    private ActivityVerificationBinding binding;
    private FirebaseAuth firebaseAuth;
    private boolean isEmailVerified = false;
    private boolean stop;
    private int count;
    private PreferenceManager preferenceManager;
    private String rsaPublicKey;
    private AES aes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityVerificationBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        firebaseAuth = FirebaseAuth.getInstance();
        preferenceManager = new PreferenceManager(getApplicationContext());
        stop = false;
        count = 0;
        binding.verifyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendEmailVerification();
                Handler handler = new Handler();
                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        checkValidation();
                        if (!stop) {
                            handler.postDelayed(this, 3000);  //keep checking after every 3s
                            count = count + 3000;
                        }
                        if (count > 60000) {   //resend email option after 1 minute
                            binding.resendEmail.setVisibility(View.VISIBLE);
                            binding.resendEmail.setOnClickListener(v -> {
                                sendEmailVerification();
                                handler.postDelayed(this, 3000);
                            });
                        }
                    }
                };
                handler.postDelayed(runnable, 3000);
            }
        });
    }

    private void sendEmailVerification() {
        FirebaseUser fUser = firebaseAuth.getCurrentUser();
        fUser.sendEmailVerification().addOnSuccessListener(unused -> {
            showToast("Check your email for verification");
            Log.d("aaaaaaaa", "onSuccess: Email send");

        }).addOnFailureListener(e -> {
            Log.d("aaaaaaaa", "onFailure: Email not sent" + e.getMessage());
        });
    }

    private String byteToBase64Conversion(byte[] input) {
        return Base64.encodeToString(input, Base64.DEFAULT);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void saveUserDetails() {
        /*Generate RSA KeyPair*/
        try {
            RSAKeyGen rsaKeyGen = new RSAKeyGen();
            final KeyStore.PrivateKeyEntry privateKeyEntry = rsaKeyGen.getPrivateKey(Verification.this);

            /* Retrieve public key to upload it onto database */
            if (privateKeyEntry != null) {
                final PublicKey publicKey = privateKeyEntry.getCertificate().getPublicKey();
                rsaPublicKey = byteToBase64Conversion(publicKey.getEncoded());
//                final PrivateKey privateKey = privateKeyEntry.getPrivateKey();
//                rsaPrivateKey = byteToBase64Conversion(privateKey.getEncoded());
            }
        } catch (UnrecoverableEntryException | CertificateException | KeyStoreException | NoSuchAlgorithmException | IOException e) {
            e.printStackTrace();
        }
        /*Generate AES key*/
        KeyGenerator keyGen = null;
        try {
            keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        SecureRandom secureRandom = new SecureRandom();
        keyGen.init(256,secureRandom);
        SecretKey key = keyGen.generateKey();
        aes = new AES();
        try {
            preferenceManager.putString(Constants.AES_CIPHER_KEY, aes.rsaEncrypt(key,rsaPublicKey));
        } catch (BadPaddingException | IllegalBlockSizeException | InvalidKeyException | NoSuchPaddingException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        /* Save user details and public key to database */
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        HashMap<String, Object> user = new HashMap<>();
        //upload rsa public key onto database
        user.put(Constants.RSA_PUBLIC_KEY, rsaPublicKey);
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            String name = extras.getString(Constants.KEY_NAME);
            String email = extras.getString(Constants.KEY_EMAIL);
            String password = extras.getString(Constants.KEY_PASSWORD);
            String encodedImage = extras.getString(Constants.KEY_IMAGE);
            String userId = extras.getString(Constants.KEY_USER_ID);
            user.put(Constants.KEY_NAME, name);
            user.put(Constants.KEY_EMAIL, email);
            user.put(Constants.KEY_PASSWORD, password);
            user.put(Constants.KEY_IMAGE, encodedImage);
            database.collection(Constants.KEY_COLLECTION_USERS)
                    .document(userId)
                    .set(user)
                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void unused) {
                            //preferenceManager.putBoolean(Constants.KEY_IS_SIGNED_IN,true);
                            preferenceManager.putString(Constants.KEY_USER_ID, userId);
                            preferenceManager.putString(Constants.KEY_NAME, name);
                            preferenceManager.putString(Constants.KEY_IMAGE, encodedImage);
                        }
                    });
        }
    }

    private void checkValidation() {
        firebaseAuth.getCurrentUser().reload().addOnSuccessListener(new OnSuccessListener<Void>() {
            @RequiresApi(api = Build.VERSION_CODES.N)
            @Override
            public void onSuccess(Void unused) {
                if (firebaseAuth.getCurrentUser() != null) {
                    isEmailVerified = firebaseAuth.getCurrentUser().isEmailVerified();
                    //Log.d("aaaaaaaaaaaaaaaa","onSuccess" + isEmailVerified);
                    if (isEmailVerified) {
                        //email verified, save info and go to sign in page
                        stop = true;
                        saveUserDetails();
                        Intent intent = new Intent(Verification.this, SignInActivity.class);
                        startActivity(intent);
                    }
                }
            }
        });
    }

    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }
}