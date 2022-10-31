package com.example.messageapp.adapters;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.messageapp.databinding.ItemContainerReceivedMessageBinding;
import com.example.messageapp.databinding.ItemContainerSendMessageBinding;
import com.example.messageapp.models.ChatMessage;
import com.squareup.picasso.Picasso;

import java.util.List;
import java.util.Objects;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private final List<ChatMessage> chatMessages;
    private Bitmap receiverProfileImage;      //variable declare final means it's constant
    private final String senderId;

    public static final int VIEW_TYPE_SENT = 1;
    public static final int VIEW_TYPE_RECEIVED = 2;
    public static final String TEXT_TYPE = "text";
    public static final String IMAGE_TYPE = "image";


    public void setReceiverProfileImage(Bitmap bitmap){
        receiverProfileImage = bitmap;
    }

    public ChatAdapter(List<ChatMessage> chatMessages, Bitmap receiverProfileImage, String senderId) {
        this.chatMessages = chatMessages;
        this.receiverProfileImage = receiverProfileImage;
        this.senderId = senderId;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_SENT) {
            return new SendMessageViewHolder(
                    ItemContainerSendMessageBinding.inflate(
                            LayoutInflater.from(parent.getContext()),
                            parent,
                            false
                    )
            );
        } else {
            return new ReceiverMessageViewHolder(
                    ItemContainerReceivedMessageBinding.inflate(
                            LayoutInflater.from(parent.getContext()),
                            parent,
                            false
                    )
            );
        }
    }
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (getItemViewType(position) == VIEW_TYPE_SENT){
            ((SendMessageViewHolder) holder).setData(chatMessages.get(position));
        }else{
            ((ReceiverMessageViewHolder) holder).setData(chatMessages.get(position), receiverProfileImage);
        }
    }

    @Override
    public int getItemCount() {
        return chatMessages.size();
    }
    @Override
    public int getItemViewType(int position){
        if (chatMessages.get(position).senderId.equals(senderId)){
            return VIEW_TYPE_SENT;
        }else{
            return VIEW_TYPE_RECEIVED;
        }
    }


    static class SendMessageViewHolder extends RecyclerView.ViewHolder {
        private final ItemContainerSendMessageBinding binding;
        SendMessageViewHolder(ItemContainerSendMessageBinding itemContainerSendMessageBinding){
            super(itemContainerSendMessageBinding.getRoot());
            binding = itemContainerSendMessageBinding;
        }

        void setData(ChatMessage chatMessage){
            if(Objects.equals(chatMessage.messageType, TEXT_TYPE)){
                binding.textMessage.setVisibility(View.VISIBLE);
                binding.textDateTime.setVisibility(View.VISIBLE);
                binding.imageSendView.setVisibility(View.GONE);
                binding.imageDateTime.setVisibility(View.GONE);
                binding.textMessage.setText(chatMessage.message);
                binding.textDateTime.setText(chatMessage.dateTime);
            }else if (Objects.equals(chatMessage.messageType, IMAGE_TYPE)){    // be careful
                binding.imageSendView.setVisibility(View.VISIBLE);
                binding.imageDateTime.setVisibility(View.VISIBLE);
                binding.textMessage.setVisibility(View.GONE);
                binding.textDateTime.setVisibility(View.GONE);
                Picasso.get()
                        .load(chatMessage.message)  //load image from URL link
                        .fit()
                        .centerInside()
                        .into(binding.imageSendView);
                binding.imageDateTime.setText(chatMessage.dateTime);
            }
        }
    }
    static class ReceiverMessageViewHolder extends RecyclerView.ViewHolder {
        private final ItemContainerReceivedMessageBinding binding ;
        ReceiverMessageViewHolder(ItemContainerReceivedMessageBinding itemContainerReceivedMessageBinding){
            super(itemContainerReceivedMessageBinding.getRoot());
            binding = itemContainerReceivedMessageBinding;
        }
        void setData(ChatMessage chatMessage, Bitmap receiverProfileImage){
            if (Objects.equals(chatMessage.messageType, TEXT_TYPE)){   //Display view for text message
                binding.imageReceivedView.setVisibility(View.GONE);
                binding.imageDateTime.setVisibility(View.GONE);

                binding.textMessage.setVisibility(View.VISIBLE);
                binding.textDateTime.setVisibility(View.VISIBLE);
                binding.textMessage.setText(chatMessage.message);
                binding.textDateTime.setText(chatMessage.dateTime);
            }else if (Objects.equals(chatMessage.messageType, IMAGE_TYPE)){  //Display view for image message
                binding.textMessage.setVisibility(View.GONE);
                binding.textDateTime.setVisibility(View.GONE);

                binding.imageReceivedView.setVisibility(View.VISIBLE);
                binding.imageDateTime.setVisibility(View.VISIBLE);
                Picasso.get()
                        .load(chatMessage.message)
                        .fit()
                        .centerInside()
                        .into(binding.imageReceivedView);
                binding.imageDateTime.setText(chatMessage.dateTime);
            }
            if(receiverProfileImage != null) {
                binding.imageProfile.setImageBitmap(receiverProfileImage);
            }

        }
    }
}
