package com.example.chatapp;

import android.content.Context;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

    private List<Message> messages = new ArrayList<>();

    public interface OnRerollListener {
        void onReroll(int messagePosition);
    }

    private OnRerollListener rerollListener;

    public void setRerollListener(OnRerollListener listener) {
        this.rerollListener = listener;
    }

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
        ImageButton btnReroll;
        LinearLayout layoutMessage;

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            btnReroll = itemView.findViewById(R.id.btnReroll);
            layoutMessage = itemView.findViewById(R.id.layoutMessage);
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
        int width = msgWidthInPx(holder.itemView.getContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                width, LinearLayout.LayoutParams.WRAP_CONTENT);

        if (message.isUser()) {
            holder.tvMessage.setBackgroundResource(R.color.user_message);
            holder.tvMessage.setGravity(android.view.Gravity.START);
            params.gravity = android.view.Gravity.START;
            holder.btnReroll.setVisibility(View.GONE);
        } else {
            holder.tvMessage.setBackgroundResource(R.color.server_message);
            holder.tvMessage.setGravity(android.view.Gravity.START);
            params.gravity = android.view.Gravity.END;
            holder.btnReroll.setVisibility(View.VISIBLE);
            holder.btnReroll.setOnClickListener(v -> {
                if (rerollListener != null) {
                    rerollListener.onReroll(position);
                }
            });
        }
        holder.layoutMessage.setLayoutParams(params);
    }

    private int msgWidthInPx(Context context) {
        DisplayMetrics dm = new DisplayMetrics();
        ((android.view.WindowManager)
                context.getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay().getRealMetrics(dm);
        return (int) (dm.widthPixels * 0.9f);
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

    public void clear() {
        messages.clear();
        notifyDataSetChanged();
    }
}
