package com.example.messageapp.activities;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.widget.Toast;

import com.example.messageapp.databinding.ActivitySignUpBinding;
import com.example.messageapp.securities.MD;
import com.example.messageapp.utilities.Constants;
import com.example.messageapp.utilities.PreferenceManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class SignUpActivity extends AppCompatActivity{
    private ActivitySignUpBinding binding;
    private PreferenceManager preferenceManager;
    private String encodedImage;
    private final int REQUEST_ID_MULTIPLE_PERMISSION = 101;
    private int optionCode;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySignUpBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        preferenceManager = new PreferenceManager(getApplicationContext());
        setListeners();
    }
    private void setListeners(){
        binding.textSignIn.setOnClickListener(v -> {
            startActivity(new Intent(SignUpActivity.this, SignInActivity.class));
        });
        binding.buttonSignUp.setOnClickListener(v -> {
            if (isValidSignUpDetails()){
                signUpAuth();
                }
        });
        binding.layoutImage.setOnClickListener(view -> {
            if(checkAndRequestPermission(SignUpActivity.this)){
                SelectImage(SignUpActivity.this);
            }
        });
    }

    private void signUpAuth(){
        loading(true);
        String email = binding.inputEmail.getText().toString().trim();
        String unHashedPassword = binding.inputPassword.getText().toString().trim();
        MD messageDigest = new MD();
        String password=null;
        try {
            password = messageDigest.toHexString(messageDigest.getSHA(unHashedPassword));
        }catch(NoSuchAlgorithmException e){
            e.printStackTrace();
        }
        FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
        String hashedPassword = password;
        firebaseAuth.createUserWithEmailAndPassword(email,password)
                        .addOnCompleteListener(task -> {
                            if(task.isSuccessful()){
                                FirebaseUser fUser = firebaseAuth.getCurrentUser();
                                Bundle extras = new Bundle();
                                String userId = fUser.getUid();
                                String name = binding.inputName.getText().toString();
                                extras.putString(Constants.KEY_NAME, name);
                                extras.putString(Constants.KEY_EMAIL, email);
                                extras.putString(Constants.KEY_PASSWORD, hashedPassword);
                                extras.putString(Constants.KEY_IMAGE, encodedImage);
                                extras.putString(Constants.KEY_USER_ID, userId);
                                //signUp(userId);
                                Intent intent = new Intent(SignUpActivity.this, Verification.class);
                                intent.putExtras(extras);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                            }else{
                                loading(false);
                                showToast("Failed to register");
                            }
                        });
    }

    private void showToast(String message){
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

    private String encodeImage(Bitmap bitmap){
        int previewWidth = 150;
        int previewHeight = bitmap.getHeight() * previewWidth / bitmap.getWidth();
        Bitmap previewBitmap = Bitmap.createScaledBitmap(bitmap, previewWidth, previewHeight,false);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        previewBitmap.compress(Bitmap.CompressFormat.JPEG,50,byteArrayOutputStream);
        byte[] bytes = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(bytes,Base64.DEFAULT);
    }

    //**********************Selecting Image Profile***************//
    private void SelectImage(Context context){
        final CharSequence[] options = { "Take Photo", "Choose from Gallery","Cancel" };
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Add Photo!");
        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (options[which].equals("Take Photo")){
                    optionCode = 1;
                    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    pickImage.launch(intent);
                }else if (options[which].equals("Choose from Gallery")){
                    optionCode = 2;
                    Intent intent = new Intent(Intent.ACTION_PICK,MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    pickImage.launch(intent);
                }else if (options[which].equals("Cancel")){
                    dialog.dismiss();
                }
            }
        });
        builder.show();
    }
    private final ActivityResultLauncher<Intent> pickImage = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            (ActivityResult result) -> {
                if (result.getResultCode() == RESULT_OK){
                    if (result.getData() != null) {
                        Bitmap bitmap;
                        switch (optionCode){
                            case 1:
                                bitmap = (Bitmap) result.getData().getExtras().get("data");
                                binding.imageProfile.setImageBitmap(bitmap);
                                binding.textAddImage.setVisibility(View.GONE);
                                encodedImage = encodeImage(bitmap);
                                break;
                            case 2:
                                Uri imageUri = result.getData().getData();
                                try {
                                    InputStream inputStream = getContentResolver().openInputStream(imageUri);
                                    bitmap = BitmapFactory.decodeStream(inputStream);
                                    binding.imageProfile.setImageBitmap(bitmap);
                                    binding.textAddImage.setVisibility(View.GONE);
                                    encodedImage = encodeImage(bitmap);
                                }catch (FileNotFoundException e){
                                    e.printStackTrace();
                                }
                        }

                    }
                }
            }
    );

    public boolean checkAndRequestPermission(final Activity context){
        int externalStorePermission = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int cameraPermission = ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA);
        List<String> listPermissionNeeded = new ArrayList<>();

        //if camera and external storage permission is not enabled, store the request in List
        if (cameraPermission != PackageManager.PERMISSION_GRANTED){  //granted=0, denied=-1
            listPermissionNeeded.add(Manifest.permission.CAMERA);
            Log.d("AAA","request: "+ Manifest.permission.CAMERA);
        }
        if (externalStorePermission != PackageManager.PERMISSION_GRANTED){
            listPermissionNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if(!listPermissionNeeded.isEmpty()){
            ActivityCompat.requestPermissions(context,
                    listPermissionNeeded.toArray(new String[listPermissionNeeded.size()]),
                    REQUEST_ID_MULTIPLE_PERMISSION);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_ID_MULTIPLE_PERMISSION:
                if (ContextCompat.checkSelfPermission(SignUpActivity.this,
                        Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(getApplicationContext(), "Require access to camera", Toast.LENGTH_SHORT).show();
                } else if (ContextCompat.checkSelfPermission(SignUpActivity.this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(getApplicationContext(), "Require access to your storage", Toast.LENGTH_SHORT).show();
                } else {
                    SelectImage(SignUpActivity.this);
                }
                break;
        }
    }

    //***************************************************//

    private Boolean isValidSignUpDetails() {
        if (encodedImage == null) {
            showToast("Select profile image");
            return false;
        } else if (binding.inputName.getText().toString().trim().isEmpty()) {
            showToast("Enter name");
            return false;
        } else if (binding.inputEmail.getText().toString().trim().isEmpty()) {
            showToast("Enter email");
            return false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(binding.inputEmail.getText().toString()).matches()) {
            showToast("Enter valid image");
            return false;
        } else if (binding.inputPassword.getText().toString().trim().isEmpty()) {
            showToast("Enter password");
            return false;
        } else if (binding.inputConfirmPassword.getText().toString().trim().isEmpty()) {
            showToast("Confirm your password");
            return false;
        } else if (!binding.inputPassword.getText().toString().equals(binding.inputConfirmPassword.getText().toString())) {
            showToast("Password & confirm password must be the same");
            return false;
        } else {
            return true;
        }
    }
    private void loading(Boolean isLoading){
        if (isLoading){
            binding.buttonSignUp.setVisibility(View.INVISIBLE);
            binding.progressBar.setVisibility(View.VISIBLE);
        }else{
            binding.progressBar.setVisibility(View.INVISIBLE);
            binding.buttonSignUp.setVisibility(View.VISIBLE);
        }
    }
}