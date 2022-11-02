package com.example.messageapp.activities;

import androidx.appcompat.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.example.messageapp.R;
import com.example.messageapp.adapters.RecentConversationsAdapter;
import com.example.messageapp.databinding.ActivityMainBinding;
import com.example.messageapp.listeners.ConversationListener;
import com.example.messageapp.models.ChatMessage;
import com.example.messageapp.models.User;
import com.example.messageapp.securities.AES;
import com.example.messageapp.utilities.Constants;
import com.example.messageapp.utilities.PreferenceManager;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.messaging.FirebaseMessaging;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.CodeSigner;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends BaseActivity implements ConversationListener {
    private ActivityMainBinding binding;
    private PreferenceManager preferenceManager;
    private List<ChatMessage> conversations;
    private RecentConversationsAdapter conversationsAdapter;
    private FirebaseFirestore database;
    private AES aes;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        init();
        loadUserDetails();
        getToken();
        setListeners();
        listenConversations();
    }
    //**************************************//
    private void init(){
        preferenceManager = new PreferenceManager(getApplicationContext());
        conversations = new ArrayList<>();
        conversationsAdapter = new RecentConversationsAdapter(conversations, this);
        binding.conversationsRecyclerView.setAdapter(conversationsAdapter);
        database = FirebaseFirestore.getInstance();
        aes = new AES();
    }
    //***************************************//
    private void setListeners(){

        binding.imageSignOut.setOnClickListener(v -> signOut());
        binding.fabNewChat.setOnClickListener(view -> startActivity(new Intent(getApplicationContext(),UsersActivity.class)));
    }
    private void loadUserDetails(){
        binding.textName.setText(preferenceManager.getString(Constants.KEY_NAME));
        byte[] bytes = Base64.decode(preferenceManager.getString(Constants.KEY_IMAGE), Base64.DEFAULT);
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        binding.imageProfile.setImageBitmap(bitmap);
    }
    private void showToast(String message){
        Toast.makeText(getApplicationContext(),message, Toast.LENGTH_SHORT).show();
    }

    private void listenConversations(){
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .addSnapshotListener(eventListener);
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .addSnapshotListener(eventListener);
    }
    private String decryptMessage(String encryptedMessage,String cipherKey,byte[] iVector){
        String originalMessage = null;
        SecretKey secretKey = aes.loadSecretKey(cipherKey);
        try {
            originalMessage = aes.aesDecrypt(encryptedMessage,secretKey,iVector);
        } catch (IllegalBlockSizeException | BadPaddingException | InvalidKeyException | InvalidAlgorithmParameterException | NoSuchAlgorithmException | NoSuchPaddingException ex) {
            ex.printStackTrace();
        }
        Log.d("message","message: "+ originalMessage);
        return originalMessage;
    }
    //*******************************************// recent chat
    private final EventListener<QuerySnapshot> eventListener = (value, error) ->{
        if (error != null){
            return;
        }
        if (value != null){
            for (DocumentChange documentChange : value.getDocumentChanges()){
                if (documentChange.getType() == DocumentChange.Type.ADDED){
                    String senderId = documentChange.getDocument().getString(Constants.KEY_SENDER_ID);
                    String receiverId = documentChange.getDocument().getString(Constants.KEY_RECEIVER_ID);
                    ChatMessage chatMessage = new ChatMessage();
                    chatMessage.senderId = senderId;
                    chatMessage.receiverId = receiverId;

                    String encryptedMessage = documentChange.getDocument().getString(Constants.KEY_LAST_MESSAGE);
                    String strIV = documentChange.getDocument().getString(Constants.AES_IV);
                    byte[] iVector = new byte[0];
                    if (strIV != null) {
                        iVector = Base64.decode((strIV.getBytes(StandardCharsets.UTF_8)), Base64.DEFAULT);
                    }
                    if(preferenceManager.getString(Constants.KEY_USER_ID).equals(senderId)){   //if user is a sender
                        /* These info will be push to chat activity to identify the person you are chatting to */
                        chatMessage.conversationImage = documentChange.getDocument().getString(Constants.KEY_RECEIVER_IMAGE);
                        chatMessage.conversationName = documentChange.getDocument().getString(Constants.KEY_RECEIVER_NAME);
                        chatMessage.conversationId = documentChange.getDocument().getString(Constants.KEY_RECEIVER_ID);
                        chatMessage.publicKey = documentChange.getDocument().getString(Constants.RECEIVER_PUBLIC_KEY);
                        /*decrypt the message */
                        String aesKey = preferenceManager.getString(Constants.AES_CIPHER_KEY);
                        chatMessage.message = decryptMessage(encryptedMessage,aesKey,iVector);

                    }
                    //if user is a receiver
                    if(preferenceManager.getString(Constants.KEY_USER_ID).equals(receiverId)){
                        chatMessage.conversationImage = documentChange.getDocument().getString(Constants.KEY_SENDER_IMAGE);
                        chatMessage.conversationName = documentChange.getDocument().getString(Constants.KEY_SENDER_NAME);
                        chatMessage.conversationId = documentChange.getDocument().getString(Constants.KEY_SENDER_ID);
                        chatMessage.publicKey = documentChange.getDocument().getString(Constants.SENDER_PUBLIC_KEY);

                        String cipherKey = documentChange.getDocument().getString(Constants.AES_CIPHER_KEY);
                        chatMessage.message = decryptMessage(encryptedMessage,cipherKey,iVector);

                    }
                    chatMessage.messageType = documentChange.getDocument().getString(Constants.KEY_FILE_TYPE);
                    chatMessage.dateObject = documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP);
                    conversations.add(chatMessage);
                }else if (documentChange.getType() == DocumentChange.Type.MODIFIED){     //facing problem here
                    for ( int i = 0; i < conversations.size(); i ++){
                        String senderId = documentChange.getDocument().getString(Constants.KEY_SENDER_ID);
                        String receiverId = documentChange.getDocument().getString(Constants.KEY_RECEIVER_ID);
                        String cipherKey;
                        String decryptedMessage;
                        if(conversations.get(i).senderId.equals(senderId) && conversations.get(i).receiverId.equals(receiverId)){
                            /* user is sender */
                            String encryptedMessage = documentChange.getDocument().getString(Constants.KEY_LAST_MESSAGE);
                            String strIV = documentChange.getDocument().getString(Constants.AES_IV);
                            byte[] iVector = new byte[0];
                            if (strIV != null) {
                                iVector = Base64.decode((strIV.getBytes(StandardCharsets.UTF_8)), Base64.DEFAULT);
                            }
                            cipherKey = documentChange.getDocument().getString(Constants.AES_CIPHER_KEY);
                            decryptedMessage = decryptMessage(encryptedMessage,cipherKey,iVector);
                            if(decryptedMessage == null){
                                cipherKey =  preferenceManager.getString(Constants.AES_CIPHER_KEY);
                                decryptedMessage = decryptMessage(encryptedMessage,cipherKey,iVector);
                                conversations.get(i).message = decryptedMessage;
                            }else{
                                conversations.get(i).message = decryptedMessage;
                            }
                            conversations.get(i).messageType = documentChange.getDocument().getString(Constants.KEY_FILE_TYPE);
                            conversations.get(i).dateObject = documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP);
                            break;
                        }
                    }
                }
            }
            //sort the conversation in order
            Collections.sort(conversations, (obj1,obj2) -> obj2.dateObject.compareTo(obj1.dateObject));
            conversationsAdapter.notifyDataSetChanged();
            binding.conversationsRecyclerView.smoothScrollToPosition(0);
            binding.conversationsRecyclerView.setVisibility(View.VISIBLE);
            binding.progressBar.setVisibility(View.GONE);
        }
    };
    //****************************************************************//
    private void getToken(){
        FirebaseMessaging firebaseMessaging = FirebaseMessaging.getInstance();
        firebaseMessaging.getToken().addOnSuccessListener(this::updateToken);
    }
    private void updateToken(String token){
        preferenceManager.putString(Constants.KEY_FCM_TOKEN, token);   //save the token to device
        FirebaseFirestore database = FirebaseFirestore.getInstance();
        DocumentReference documentReference =
                database.collection(Constants.KEY_COLLECTION_USERS).document(
                        preferenceManager.getString(Constants.KEY_USER_ID)
                );
        documentReference.update(Constants.KEY_FCM_TOKEN, token)
                .addOnFailureListener(e -> showToast("Unable to update token"));
    }
    private void signOut(){
        confirmSignOut();
        //showToast("Signing out...");

    }
    private void clearMemory(){     //Delete these info but not the cipher Key
        preferenceManager.remove(Constants.KEY_USER_ID);
        preferenceManager.remove(Constants.KEY_FCM_TOKEN);
        preferenceManager.remove(Constants.KEY_IS_SIGNED_IN);
        preferenceManager.remove(Constants.KEY_NAME);
        preferenceManager.remove(Constants.KEY_IMAGE);
    }
    private void confirmSignOut(){
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this, R.style.AlertDialogStyle);
        alertDialog.setTitle("Warning!");
        alertDialog.setMessage("Are you sure you want to log out?");
        alertDialog.setPositiveButton("Log out", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                FirebaseFirestore database = FirebaseFirestore.getInstance();
                DocumentReference documentReference =
                        database.collection(Constants.KEY_COLLECTION_USERS).document(
                                preferenceManager.getString(Constants.KEY_USER_ID)
                        );
                HashMap<String, Object> updates = new HashMap<>();
                updates.put(Constants.KEY_FCM_TOKEN, FieldValue.delete());
                documentReference.update(updates)
                        .addOnSuccessListener(unused -> {
                            clearMemory();
                            //preferenceManager.clear();
                            startActivity(new Intent(getApplicationContext(), SignUpActivity.class));
                            finish();
                        })
                        .addOnFailureListener(e -> showToast("Unable to sign out"));
            }
        });
        alertDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                //do nothing
            }
        });
        alertDialog.show();
    }

    @Override
    public void onConversationClicked(User user) {
        Intent intent = new Intent(getApplicationContext(), ChatActivity.class);
        intent.putExtra(Constants.KEY_USER,user);
        Log.d("AAA", "Key: " + user.publicKey);
        startActivity(intent);
    }
}
