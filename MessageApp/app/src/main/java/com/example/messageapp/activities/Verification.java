package com.example.messageapp.activities;

import androidx.appcompat.app.AppCompatActivity;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.example.messageapp.databinding.ActivityVerificationBinding;
import com.example.messageapp.utilities.Constants;
import com.example.messageapp.utilities.PreferenceManager;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;

public class Verification extends AppCompatActivity {
    private ActivityVerificationBinding binding;
    private FirebaseAuth firebaseAuth;
    private boolean isEmailVerified = false;
    private boolean stop;
    private int count;
    private ProgressDialog progressDialog;
    private PreferenceManager preferenceManager;
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
                        if(!stop){
                            handler.postDelayed(this,3000);  //keep checking after every 3s
                            count = count + 3000;
                        }
                        if(count > 60000){   //resend email option after 1 minute
                            binding.resendEmail.setVisibility(View.VISIBLE);
                            binding.resendEmail.setOnClickListener(v -> {
                                sendEmailVerification();
                                handler.postDelayed(this,3000);
                            });
                        }
                    }
                };
                handler.postDelayed(runnable,3000);
            }
        });
    }

    private void sendEmailVerification(){
        FirebaseUser fUser = firebaseAuth.getCurrentUser();
        fUser.sendEmailVerification().addOnSuccessListener(unused -> {
            showToast("Check your email for verification");
            Log.d("aaaaaaaa", "onSuccess: Email send");

        }).addOnFailureListener(e -> {
            Log.d("aaaaaaaa", "onFailure: Email not sent"+ e.getMessage());
        });
    }
    private void saveUserDetails(){
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        HashMap<String, Object> user = new HashMap<>();
        Bundle extras = getIntent().getExtras();
        if(extras != null){
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
                            preferenceManager.putString(Constants.KEY_USER_ID,userId);
                            preferenceManager.putString(Constants.KEY_NAME,name);
                            preferenceManager.putString(Constants.KEY_IMAGE,encodedImage);
                        }
                    });
        }
    }

    private void checkValidation(){
        firebaseAuth.getCurrentUser().reload().addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void unused) {
                if(firebaseAuth.getCurrentUser() != null){
                    isEmailVerified = firebaseAuth.getCurrentUser().isEmailVerified();
                    //Log.d("aaaaaaaaaaaaaaaa","onSuccess" + isEmailVerified);
                    if(isEmailVerified){
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

    private void showToast(String message){
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }

    private void timeLimitDialog(){
        Handler handler = new Handler();
        progressDialog = new ProgressDialog(this);
        progressDialog.setMax(60);
        progressDialog.setMessage("It is loading...");
        progressDialog.setTitle("Verifying Email");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                for(int i = 0;i<=progressDialog.getMax(); i++) {
                    final int currentProgressCount = i;
                    try {
                        Thread.sleep(1000);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    //Update value background thread to UI thread
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            progressDialog.setProgress(currentProgressCount);
                        }
                    });
                }
            }
        }).start();
    }
}