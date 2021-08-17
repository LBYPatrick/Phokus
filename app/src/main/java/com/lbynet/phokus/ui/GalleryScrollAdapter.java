package com.lbynet.phokus.ui;

import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class GalleryScrollAdapter extends RecyclerView.Adapter<GalleryScrollAdapter.ItemHolder> {

    ArrayList<Uri> imageUris = new ArrayList<>();

    final public class ItemHolder extends ViewHolder {

        private View itemView;

        public ItemHolder(@NonNull @NotNull View itemView) {
            super(itemView);
            this.itemView = itemView;
        }
        public void bind(Uri imageUri) {
            //TODO:Update itemView with the provided Image URI

        }

    }

    @NonNull
    @Override
    public ItemHolder onCreateViewHolder(@NotNull ViewGroup parent, int viewType) {

        //TODO: Inflate "parents" (which refers to the individual gallery item views)



        return null;
    }

    @Override
    public void onBindViewHolder(@NonNull @NotNull ItemHolder holder, int position) {
        holder.bind(imageUris.get(position));
    }

    @Override
    public int getItemCount() {
        return imageUris.size();
    }


}
