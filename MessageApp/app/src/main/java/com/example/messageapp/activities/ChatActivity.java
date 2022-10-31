package com.example.messageapp.activities;


import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;

import com.example.messageapp.adapters.ChatAdapter;
import com.example.messageapp.databinding.ActivityChatBinding;
import com.example.messageapp.models.ChatMessage;
import com.example.messageapp.models.User;
import com.example.messageapp.network.ApiClient;
import com.example.messageapp.network.ApiService;
import com.example.messageapp.securities.AES;
import com.example.messageapp.utilities.Constants;
import com.example.messageapp.utilities.PreferenceManager;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.OnProgressListener;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.StorageTask;
import com.google.firebase.storage.UploadTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatActivity extends BaseActivity {
    private ActivityChatBinding binding;
    private User receiverUser;
    private List<ChatMessage> chatMessages;
    private ChatAdapter chatAdapter;
    private PreferenceManager preferenceManager;
    private FirebaseFirestore database;
    private StorageReference storageReference;
    private StorageReference filePath;
    private String conversationId = null;   //collection id for the recent message
    private Boolean isReceiverAvailable = false;
    private String optionCode;
    private ProgressDialog loadingBar;
    private StorageTask uploadTask;
    private byte[] iv ;
    private AES aes;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding  = ActivityChatBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setListeners();
        loadReceiverDetails();
        Log.d("AAA", "pubKey: "+receiverUser.publicKey);
        init();
        listenMessages();
    }

    //get receiver name and receiverId
    private void loadReceiverDetails(){
        receiverUser = (User) getIntent().getSerializableExtra(Constants.KEY_USER);
        binding.textName.setText(receiverUser.name);
    }
    private void init(){
        preferenceManager = new PreferenceManager(getApplicationContext());
        database = FirebaseFirestore.getInstance();    //**
        chatMessages = new ArrayList<>();

        chatAdapter = new ChatAdapter(
                chatMessages,
                getBitmapFromEncodedString(receiverUser.image),
                preferenceManager.getString(Constants.KEY_USER_ID)     //this para is to view message as sender or receiver
                );
        binding.chatRecycleView.setAdapter(chatAdapter);
        loadingBar=new ProgressDialog(this);
        aes = new AES();
    }

    //Convert encoded string to bitmap image
    private Bitmap getBitmapFromEncodedString(String encodedImage){
        if(encodedImage != null){
            byte[] bytes = Base64.decode(encodedImage, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        }else{
            return null;
        }

    }
    private PublicKey loadPublicKey() throws KeyStoreException, CertificateException, IOException, NoSuchAlgorithmException, UnrecoverableEntryException {
        KeyStore keyStore = KeyStore.getInstance(Constants.KEYSTORE);
        keyStore.load(null);
        KeyStore.Entry  entry =  keyStore.getEntry(Constants.ALIAS,null);
        KeyStore.PrivateKeyEntry keyEntry = (KeyStore.PrivateKeyEntry) entry;
        return keyEntry.getCertificate().getPublicKey();
    }
    private String EncryptedMessage(String message, SecretKey secretKey) throws  IllegalBlockSizeException,
            NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException{
        String encryptedMessage = aes.aesEncrypt(message,secretKey);
        iv  = aes.getIV();          //get IV from GCM block cipher
        return encryptedMessage;
    }
    private String encryptAESKey(SecretKey secretKey) throws IllegalBlockSizeException, NoSuchPaddingException, BadPaddingException,
            NoSuchAlgorithmException, InvalidKeyException {
        //compute CipherKey which is sent to receiver(from original key)
        String rsaPublicKey = receiverUser.publicKey;
        Log.d("AAA","Receiver PublicKey: "+ rsaPublicKey);
        String cipherKey = aes.rsaEncrypt(secretKey,rsaPublicKey);
        return cipherKey;
    }
    private void sendMessage() {
        HashMap<String, Object> message = new HashMap<>();
        message.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
        message.put(Constants.KEY_RECEIVER_ID, receiverUser.id);
        /* ********************ENCRYPT MESSAGE************************** */
        String plainMessage = binding.inputMessage.getText().toString();

        /* Load AES key from local device and encrypt the message*/
        SecretKey aesSecretKey = aes.loadSecretKey(preferenceManager.getString(Constants.AES_CIPHER_KEY));    //This is AES key for encrypting message

        Log.d("AAA", "AES KEY: "+ Base64.encodeToString(aesSecretKey.getEncoded(),Base64.DEFAULT));
        String encryptedMessage = null;
        try {
            encryptedMessage = EncryptedMessage(plainMessage, aesSecretKey);
        } catch (IllegalBlockSizeException | NoSuchPaddingException | BadPaddingException | NoSuchAlgorithmException | InvalidKeyException e) {
            e.printStackTrace();
        }
        String cipherKey = null;    //encrypt AES secret key using receiver's public key
        try {
            cipherKey = encryptAESKey(aesSecretKey);
        } catch (IllegalBlockSizeException | NoSuchPaddingException | BadPaddingException | NoSuchAlgorithmException | InvalidKeyException e) {
            e.printStackTrace();
        }
        Log.d("AAA","CipherKey: "+ cipherKey);
        message.put(Constants.KEY_MESSAGE, encryptedMessage);  // encrypted message
        message.put(Constants.AES_CIPHER_KEY, cipherKey);      //save cipherKey to database
        message.put(Constants.AES_IV,Base64.encodeToString(iv,Base64.DEFAULT));  //initialization vector
        Log.d("AAA","IV: "+ Base64.encodeToString(iv,Base64.DEFAULT));
        /* *******************END ENCRYPT MESSAGE******************** */

        message.put(Constants.KEY_FILE_TYPE,"text");
        message.put(Constants.KEY_TIMESTAMP, new Date());
        database.collection(Constants.KEY_COLLECTION_CHAT)
                .add(message)
                .addOnCompleteListener(new OnCompleteListener<DocumentReference>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentReference> task) {
                        if(task.isSuccessful()){
                            Log.d("AAA", "MessageInfo upload successfully");
                        }else {
                            Log.d("AAA", "Failed to upload MessageInfo");
                        }
                    }
                });

        /* ***add recent chat*** */
        saveRecentMessageInfo(encryptedMessage,cipherKey,iv,"text");

        /*check availability of receiver*/
        if (!isReceiverAvailable){
            try {
                JSONArray tokens = new JSONArray();
                tokens.put(receiverUser.token);
                JSONObject data = new JSONObject();
                data.put(Constants.KEY_USER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
                data.put(Constants.KEY_NAME, preferenceManager.getString(Constants.KEY_NAME));
                data.put(Constants.KEY_FCM_TOKEN, preferenceManager.getString(Constants.KEY_FCM_TOKEN));
                data.put(Constants.KEY_MESSAGE, binding.inputMessage.getText().toString());

                JSONObject body = new JSONObject();
                body.put(Constants.REMOTE_MSG_DATA, data);
                body.put(Constants.REMOTE_MSG_REGISTRATION_IDS, tokens);

                sendNotification(body.toString());
            }catch (Exception e){
                showToast(e.getMessage());
            }
        }
        //empty the input text field after sending message
        binding.inputMessage.setText(null);
    }
    /* ****************Files sending option****************** */
    private void fileSelection(Context context){

        CharSequence[] options =new CharSequence[]{"Images","Documents"};

        AlertDialog.Builder builder=new AlertDialog.Builder(context);
        builder.setTitle("Select File");
        builder.setItems(options, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if(options[which].equals("Images"))
                {
                    optionCode = "image";
                    Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    pickFile.launch(intent);
                }else if(options[which].equals("Documents"))
                {
                    optionCode = "document";
                    Intent intent;
                    if (android.os.Build.MANUFACTURER.equalsIgnoreCase("samsung")) {
                        intent = new Intent("com.sec.android.app.myfiles.PICK_DATA");
                        intent.putExtra("CONTENT_TYPE", "*/*");
                        intent.addCategory(Intent.CATEGORY_DEFAULT);
                    } else {

                        String[] mimeTypes =
                                {"application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // .doc & .docx
                                        "application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation", // .ppt & .pptx
                                        "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", // .xls & .xlsx
                                        "text/plain",
                                        "application/pdf",
                                        "application/zip", "application/vnd.android.package-archive"};

                        intent = new Intent(Intent.ACTION_GET_CONTENT); // or ACTION_OPEN_DOCUMENT
                        intent.setType("*/*");
                        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
                    }
                    pickFile.launch(intent);
                }
            }
        });
        builder.show();
    }

    private String getFileExtension(Uri uri){
        ContentResolver contentResolver = getContentResolver();
        MimeTypeMap mime = MimeTypeMap.getSingleton();
        return mime.getExtensionFromMimeType(contentResolver.getType(uri));
    }

    private final ActivityResultLauncher<Intent> pickFile = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            (ActivityResult result) -> {
                if (result.getResultCode() == RESULT_OK){
                    if (result.getData() != null) {
                        loadingBar.setTitle("Sending File");
                        loadingBar.setMessage("please wait, we are sending that file...");
                        loadingBar.setCanceledOnTouchOutside(false);
                        loadingBar.show();
                        CollectionReference collectionReference = database.collection(Constants.KEY_COLLECTION_CHAT);
                        final String messageId;
                        switch (optionCode){
                            //if sender sends image
                            case "image":
                                storageReference = FirebaseStorage.getInstance().getReference(Constants.KEY_IMAGE_FILE);
                                Uri imageUri = result.getData().getData();
                                messageId = collectionReference.document().getId();
                                Log.d("AAA", messageId);
                                /* create file collection on Storage */
                                filePath = storageReference.child(messageId
                                        +"."+getFileExtension(imageUri));
                                /* upload image onto Storage */
                                uploadTask = filePath.putFile(imageUri);
                                uploadTask.continueWithTask(new Continuation() {
                                    @Override
                                    public Object then(@NonNull Task task) throws Exception {
                                        if (!task.isSuccessful()){
                                            throw task.getException();
                                        }
                                        return filePath.getDownloadUrl();
                                    }
                                }).addOnCompleteListener(new OnCompleteListener() {
                                    @Override
                                    public void onComplete(@NonNull Task task) {
                                        if(task.isSuccessful()){
                                            Uri downloadUri = (Uri) task.getResult();
                                            String myUrl = downloadUri.toString();
                                            /* Load AES key from local device and encrypt the message*/
                                            SecretKey aesSecretKey = aes.loadSecretKey(preferenceManager.getString(Constants.AES_CIPHER_KEY));    //This is AES key for encrypting message
                                            String encryptedUrl = null;
                                            try {
                                                encryptedUrl = EncryptedMessage(myUrl, aesSecretKey);   //this process will generate IV internally
                                            } catch (IllegalBlockSizeException | NoSuchPaddingException | BadPaddingException | NoSuchAlgorithmException | InvalidKeyException e) {
                                                e.printStackTrace();
                                            }
                                            String cipherKey = null;
                                            try {
                                                cipherKey = encryptAESKey(aesSecretKey); //encrypt AES secret key using receiver's public key
                                            } catch (IllegalBlockSizeException | NoSuchPaddingException | BadPaddingException | NoSuchAlgorithmException | InvalidKeyException e) {
                                                e.printStackTrace();
                                            }
                                            HashMap<String, Object> message = new HashMap<>();
                                            message.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
                                            message.put(Constants.KEY_RECEIVER_ID, receiverUser.id);
                                            message.put(Constants.KEY_MESSAGE, encryptedUrl);   // save image in URL form
                                            message.put(Constants.AES_CIPHER_KEY, cipherKey);      //save cipherKey to database
                                            message.put(Constants.AES_IV,Base64.encodeToString(iv,Base64.DEFAULT));  //initialization vector
                                            message.put(Constants.KEY_FILE_TYPE,optionCode);
                                            message.put(Constants.KEY_TIMESTAMP, new Date());
                                            collectionReference.document(messageId).set(message);
                                            /* ***************save recent chat************ */
                                            saveRecentMessageInfo(encryptedUrl,cipherKey,iv,optionCode);
                                            loadingBar.dismiss();
                                        }else{
                                            loadingBar.dismiss();
                                            showToast("Error!");
                                        }
                                    }
                                });
                                break;
                            //if sender sends documents//
                            case "document":
                                storageReference =FirebaseStorage.getInstance().getReference().child(Constants.KEY_DOCUMENTS);
                                Uri fileUri = result.getData().getData();
                                messageId = collectionReference.document().getId();
                                filePath = storageReference.child(messageId+"."+ getFileExtension(fileUri));

                                filePath.putFile(fileUri).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                                    @Override
                                    public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                                        filePath.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                                            @Override
                                            public void onSuccess(Uri uri) {
                                                String downloadUrl  = uri.toString();
                                                HashMap<String, Object> message = new HashMap<>();
                                                message.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
                                                message.put(Constants.KEY_RECEIVER_ID, receiverUser.id);
                                                message.put(Constants.KEY_MESSAGE, downloadUrl);
                                                message.put(Constants.KEY_TIMESTAMP, new Date());
                                                collectionReference.document(messageId).set(message);
                                                loadingBar.dismiss();
                                            }
                                        }).addOnFailureListener(new OnFailureListener() {
                                            @Override
                                            public void onFailure(@NonNull Exception e) {
                                                loadingBar.dismiss();
                                                showToast(e.getMessage());
                                            }
                                        });
                                    }
                                }).addOnProgressListener(new OnProgressListener<UploadTask.TaskSnapshot>() {
                                    @Override
                                    public void onProgress(@NonNull UploadTask.TaskSnapshot snapshot) {
                                        double p= (100.0* snapshot.getBytesTransferred())/snapshot.getTotalByteCount();
                                        loadingBar.setMessage((int)p+" % Uploading...");
                                    }
                                });
                                break;
                        }
                    }
                }
            }
    );
    //*******************************************//
    private void saveRecentMessageInfo(String lastMessage,String cipherKey, byte[] iVec,String messageType){
        /* ***********************************start recent chat*************************** */
        if(conversationId != null){     /* if conversation is already existed, only these info to be updated */
            updateConversation(lastMessage,messageType,cipherKey,Base64.encodeToString(iVec,Base64.DEFAULT));
        }else {    /* if conversation is new, all of these info to be upload onto database */
            HashMap<String, Object> conversation = new HashMap<>();

            conversation.put(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID));
            conversation.put(Constants.KEY_SENDER_NAME, preferenceManager.getString(Constants.KEY_NAME));
            conversation.put(Constants.KEY_SENDER_IMAGE, preferenceManager.getString(Constants.KEY_IMAGE));
            conversation.put(Constants.KEY_RECEIVER_ID, receiverUser.id);
            conversation.put(Constants.KEY_RECEIVER_NAME, receiverUser.name);
            conversation.put(Constants.KEY_RECEIVER_IMAGE, receiverUser.image);
            String senderPublicKey = null;
            try {
                PublicKey publicKey = loadPublicKey();
                senderPublicKey = Base64.encodeToString(publicKey.getEncoded(),Base64.DEFAULT);
            } catch (KeyStoreException | CertificateException | IOException | NoSuchAlgorithmException | UnrecoverableEntryException e) {
                e.printStackTrace();
            }

            conversation.put(Constants.SENDER_PUBLIC_KEY,senderPublicKey);
            conversation.put(Constants.RECEIVER_PUBLIC_KEY, receiverUser.publicKey);    //newly updated
            conversation.put(Constants.AES_CIPHER_KEY,cipherKey);
            conversation.put(Constants.AES_IV,Base64.encodeToString(iVec,Base64.DEFAULT));
            conversation.put(Constants.KEY_LAST_MESSAGE, lastMessage);
            conversation.put(Constants.KEY_FILE_TYPE, messageType);
            conversation.put(Constants.KEY_TIMESTAMP, new Date());

            addConversation(conversation);
        }
        /* *******************************end recent chat****************************** */
    }
    private void listenMessages(){
        database.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverUser.id)
                .addSnapshotListener(eventListener);

        database.collection(Constants.KEY_COLLECTION_CHAT)
                .whereEqualTo(Constants.KEY_SENDER_ID, receiverUser.id)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, preferenceManager.getString(Constants.KEY_USER_ID))
                .addSnapshotListener(eventListener);
    }

   /* CHECK FOR NEW MESSAGE ON DATABASE */
    private final EventListener<QuerySnapshot> eventListener = (value, error) -> {
        if(error != null){
            return;
        }
        if(value!= null){    //if there is changes in database
            int count = chatMessages.size();
            for (DocumentChange documentChange : value.getDocumentChanges()){
                if(documentChange.getType() == DocumentChange.Type.ADDED){    //if new message is added to database
                    ChatMessage messageInfo = new ChatMessage();   //get the info of the message
                    messageInfo.senderId = documentChange.getDocument().getString(Constants.KEY_SENDER_ID);
                    messageInfo.receiverId = documentChange.getDocument().getString(Constants.KEY_RECEIVER_ID);
                    //***************SAVE FILE FORMAT***********************//
                    messageInfo.messageType = documentChange.getDocument().getString(Constants.KEY_FILE_TYPE); //newly added for image sending feature
                    //get encrypted message from database
                    String encryptedMessage = documentChange.getDocument().getString(Constants.KEY_MESSAGE);

                    /* ***************DECRYPT MESSAGE************* */
                    String strIV = documentChange.getDocument().getString(Constants.AES_IV);   //get iv
                    byte[] iVector = Base64.decode((strIV.getBytes(StandardCharsets.UTF_8)), Base64.DEFAULT);
                    String cipherKey = null;
                    if (messageInfo.senderId.equals(preferenceManager.getString(Constants.KEY_USER_ID))) {   /*This is for sender to see his own message*/
                        /* Load AES key from device memory and decrypt the message*/
                        cipherKey = preferenceManager.getString(Constants.AES_CIPHER_KEY);

                    } else if (messageInfo.receiverId.equals(preferenceManager.getString(Constants.KEY_USER_ID))) {  /* This is for receiver to see the message sent by sender */
                        cipherKey = documentChange.getDocument().getString(Constants.AES_CIPHER_KEY);   /* Get the cipher key from database */
                    }
                    SecretKey aesSecretKey = aes.loadSecretKey(cipherKey);    //This is AES key for decrypting the message
                    try {
                        messageInfo.message = aes.aesDecrypt(encryptedMessage, aesSecretKey, iVector);
                    } catch (InvalidAlgorithmParameterException | IllegalBlockSizeException | NoSuchPaddingException | BadPaddingException | NoSuchAlgorithmException | InvalidKeyException e) {
                        e.printStackTrace();
                    }
                    messageInfo.dateTime = getReadableDateTime(documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP));
                    messageInfo.dateObject = documentChange.getDocument().getDate(Constants.KEY_TIMESTAMP);
                    chatMessages.add(messageInfo);
                }
            }
            Collections.sort(chatMessages, (obj1,obj2) -> obj1.dateObject.compareTo(obj2.dateObject));
            if (count == 0){
                chatAdapter.notifyDataSetChanged();
//                chatAdapter = new ChatAdapter(chatMessages,
//                        getBitmapFromEncodedString(receiverUser.image),
//                        preferenceManager.getString(Constants.KEY_USER_ID));
//                binding.chatRecycleView.setAdapter(chatAdapter);
                // Log.d("AAA", chatMessages.get(0).message);
            } else {
                chatAdapter.notifyItemRangeInserted(chatMessages.size(), chatMessages.size());
                binding.chatRecycleView.smoothScrollToPosition(chatMessages.size()-1);
            }
            binding.chatRecycleView.setVisibility(View.VISIBLE);
        }
        binding.progressBar.setVisibility(View.GONE);
        if(conversationId == null){
            checkForConversation();
        }
    };
    /* when the conversation just started */
    private void addConversation(HashMap<String, Object> conversation){
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .add(conversation)          /* All the message info is uploaded to the database */
                .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
                    @Override
                    public void onSuccess(DocumentReference documentReference) {
                        conversationId = documentReference.getId();
                    }
                });
    }
    /* When there is already an existing conversation */
    private void updateConversation(String message, String messageType,String cipherKey,String iVec){
        DocumentReference documentReference =
                database.collection(Constants.KEY_COLLECTION_CONVERSATIONS).document(conversationId);
        documentReference.update(
                Constants.KEY_LAST_MESSAGE, message,
                Constants.AES_IV, iVec,
                Constants.AES_CIPHER_KEY,cipherKey,
                Constants.KEY_FILE_TYPE, messageType,         //newly update for recent conversation
                Constants.KEY_TIMESTAMP, new Date()
        );
    }

    private void checkForConversation(){     //this function is to acquire the ID of recent conversation
        if (chatMessages.size() != 0){
            checkForConversationRemotely(
                    preferenceManager.getString(Constants.KEY_USER_ID),
                    receiverUser.id
            );
            checkForConversationRemotely(
                    receiverUser.id,
                    preferenceManager.getString(Constants.KEY_USER_ID)
            );
        }
    }

    private void checkForConversationRemotely(String senderId, String receiverId){
        database.collection(Constants.KEY_COLLECTION_CONVERSATIONS)
                .whereEqualTo(Constants.KEY_SENDER_ID, senderId)
                .whereEqualTo(Constants.KEY_RECEIVER_ID, receiverId)
                .get()
                .addOnCompleteListener(onCompleteListener);
    }

    private final OnCompleteListener<QuerySnapshot> onCompleteListener = task ->{
        if(task.isSuccessful() && task.getResult() != null && task.getResult().getDocuments().size()>0){
            DocumentSnapshot documentSnapshot = task.getResult().getDocuments().get(0);
            conversationId = documentSnapshot.getId();    //get the ID from database
        }
    };

    private void showToast(String message){
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }

    private void sendNotification(String messageBody){
        ApiClient.getClient().create(ApiService.class).sendMessage(
                Constants.getRemoteMSGHeaders(),
                messageBody
        ).enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if (response.isSuccessful()){
                    try{
                        if(response.body() !=null){
                            JSONObject responseJson = new JSONObject(response.body());
                            JSONArray results = responseJson.getJSONArray("results");
                            if (responseJson.getInt("failure") == 1){
                                JSONObject error = (JSONObject) results.get(0);
                                showToast(error.getString("error"));
                                return;
                            }
                        }
                    }catch (JSONException e){
                        e.printStackTrace();
                    }
                    showToast("Notification sent successfully");
                }else{
                    showToast("Error:" + response.code());
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                showToast(t.getMessage());
            }
        });
    }

    /* Check availability of receiver*/
    private void listenAvailabilityOfReceiver(){
        database.collection(Constants.KEY_COLLECTION_USERS).document(
                receiverUser.id
        ).addSnapshotListener(ChatActivity.this, (value,error)->{
            if(error != null){
                return;
            }
            if(value != null){
                if(value.getLong(Constants.KEY_AVAILABILITY) != null){
                    int availability = Objects.requireNonNull(
                            value.getLong(Constants.KEY_AVAILABILITY)
                    ) .intValue();
                    isReceiverAvailable = availability == 1;
                }
                receiverUser.token = value.getString(Constants.KEY_FCM_TOKEN);
                if(receiverUser.image == null){
                    receiverUser.image = value.getString(Constants.KEY_IMAGE);
                    chatAdapter.setReceiverProfileImage(getBitmapFromEncodedString(receiverUser.image));
                    chatAdapter.notifyItemRangeChanged(0,chatMessages.size());
                }
            }
            if (isReceiverAvailable){
                binding.textAvailability.setVisibility(View.VISIBLE);
            }else {
                binding.textAvailability.setVisibility(View.GONE);
            }

        });
    }

    private void setListeners(){
        binding.filesSelect.setOnClickListener(v->fileSelection(ChatActivity.this));
        binding.imageBack.setOnClickListener(v-> onBackPressed());
        binding.layoutSend.setOnClickListener(v-> sendMessage());
    }
    private String getReadableDateTime(Date date){
        return new SimpleDateFormat("MMMM dd, yyyy - hh:mm a", Locale.getDefault()).format(date);
    }


    @Override
    protected void onResume() {
        super.onResume();
        listenAvailabilityOfReceiver();
    }
}
