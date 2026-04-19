package com.example.chatapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

    private List<Message> messages = new ArrayList<>();

    public static class Message {
        private String text;
        private boolean isUser;

        public Message(String text, boolean isUser) {
            this.text = text;
            this.isUser = isUser;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public boolean isUser() {
            return isUser;
        }
    }

    public static class MessageViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessage;

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
        }
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message message = messages.get(position);
        holder.tvMessage.setText(message.getText());
        
        if (message.isUser()) {
            holder.tvMessage.setBackgroundResource(R.color.user_message);
            holder.tvMessage.setGravity(android.view.Gravity.END);
        } else {
            holder.tvMessage.setBackgroundResource(R.color.server_message);
            holder.tvMessage.setGravity(android.view.Gravity.START);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public void addMessage(Message message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }

    public void updateLastServerMessage(String newText) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (!messages.get(i).isUser()) {
                messages.get(i).setText(newText);
                notifyItemChanged(i);
                return;
            }
        }
    }

    public void addServerMessage(String text) {
        Message message = new Message(text, false);
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }
}
