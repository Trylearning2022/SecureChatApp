package com.example.messageapp.utilities;

import java.util.HashMap;

public class Constants {
    public static final String KEY_COLLECTION_USERS = "users";
    public static final  String KEY_NAME = "name";
    public static final String KEY_EMAIL = "email";
    public static final String KEY_PASSWORD = "password";
    public static final String KEY_PREFERENCE_NAME = "chatAPP_PREFERENCE";
    public static final String KEY_IS_SIGNED_IN = "isSignedIn";
    public static final String KEY_USER_ID = "userID";
    public static final String KEY_IMAGE = "image";
    public static final String KEY_FCM_TOKEN = "fcmToken";
    public static final String KEY_USER = "user";
    public static final String KEY_COLLECTION_CHAT= "chat";
    public static final String KEY_SENDER_ID = "senderID";
    public static final String KEY_RECEIVER_ID = "receiverID";
    public static final String KEY_MESSAGE = "message";
    public static final String KEY_TIMESTAMP = "timestamp";
    public static final String KEY_COLLECTION_CONVERSATIONS = "conversations";
    public static final String KEY_SENDER_NAME = "senderName";
    public static final String KEY_RECEIVER_NAME = "receiverName";
    public static final String KEY_SENDER_IMAGE = "senderImage";
    public static final String KEY_RECEIVER_IMAGE = "receiverImage";
    public static final String KEY_LAST_MESSAGE = "lastMessage";
    public static final String KEY_AVAILABILITY = "availability";
    public static final String REMOTE_MSG_AUTHORIZATION = "Authorization";
    public static final String REMOTE_MSG_CONTENT_TYPE = "Content-Type";
    public static final String REMOTE_MSG_DATA = "data";
    public static final String REMOTE_MSG_REGISTRATION_IDS = "registration_ids";
    public static final String KEY_DOCUMENTS = "documentFiles";
    public static final String KEY_IMAGE_FILE = "imageFiles";
    public static final String KEY_FILE_TYPE = "fileType";
    public static final String SECRET_KEY = "secretKey";
    public static final String RSA_PUBLIC_KEY ="rsaPublicKey";
    public static final String RSA_PRIVATE_KEY = "rsaPrivateKey";
    public static final String AES_IV = "iv";
    public static final String AES_CIPHER_KEY = "cipherAESKey";
    public static final String KEYSTORE = "AndroidKeyStore";
    public static final String ALIAS = "MY_APP";
    public static final String SENDER_PUBLIC_KEY = "senderKey";
    public static final String RECEIVER_PUBLIC_KEY = "receiverKey";

    public static HashMap<String, String> remoteMSGHeaders = null;
    public static HashMap<String, String> getRemoteMSGHeaders(){
        if(remoteMSGHeaders == null){
            remoteMSGHeaders = new HashMap<>();
            remoteMSGHeaders.put(
                    REMOTE_MSG_AUTHORIZATION,
                    "key=AAAACSJ7xf4:APA91bHGLK2pMgN3siIgRv8eIrdY02y3i-FWIJAXr6JazlF-XH4VDc5-UPMPa6puibsFwd9I32hQ5VwCRK2brABxLgM5XdGnpWDRswNvIW9QTpV5gvVoQ0f1a5pPURiy5lV7Xkj6ewQo"
            );
            remoteMSGHeaders.put(
                    REMOTE_MSG_CONTENT_TYPE,
                    "application/json"
            );
        }
        return remoteMSGHeaders;
    }
}
